package app.aaps.pump.danar.emulator

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.rfcomm.RfcommDevice
import app.aaps.core.interfaces.pump.rfcomm.RfcommSocket
import app.aaps.core.interfaces.pump.rfcomm.RfcommTransport
import app.aaps.pump.utils.CRC
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.LinkedBlockingQueue

/**
 * Emulated RFCOMM transport for DanaR pump testing.
 * Replaces real Bluetooth with an in-process [DanaRPumpEmulator].
 *
 * Data flow:
 *   App writes to [EmulatorOutputStream] → parses DanaR packet → routes to emulator →
 *   builds response packet → enqueues to [EmulatorInputStream] → app reads response
 */
class EmulatorRfcommTransport(
    val emulator: DanaRPumpEmulator = DanaRPumpEmulator(),
    private val aapsLogger: AAPSLogger? = null,
    private val deviceName: String = "DAN12345AB"
) : RfcommTransport {

    override fun getSocketForDevice(deviceName: String): RfcommSocket {
        aapsLogger?.debug(LTag.PUMP, "Emulator: creating socket for '$deviceName'")
        return EmulatorRfcommSocket()
    }

    override fun getBondedDevices(): List<RfcommDevice> =
        listOf(RfcommDevice(name = deviceName, address = "EM:UL:AT:OR:00:01"))

    /**
     * Emulated RFCOMM socket using blocking queues for I/O.
     */
    inner class EmulatorRfcommSocket : RfcommSocket {

        private val responseQueue = LinkedBlockingQueue<Byte>()
        @Volatile private var connected = false

        override val inputStream: InputStream = EmulatorInputStream()
        override val outputStream: OutputStream = EmulatorOutputStream()
        override val isConnected: Boolean get() = connected

        override fun connect() {
            connected = true
            // Wire up callback for multi-packet responses (e.g., history events)
            emulator.onAdditionalResponse = { command, data ->
                val packet = buildResponsePacket(command, data)
                for (byte in packet) responseQueue.put(byte)
            }
            aapsLogger?.debug(LTag.PUMP, "Emulator: socket connected")
        }

        override fun close() {
            connected = false
            // Unblock any waiting reads
            responseQueue.put(0xFF.toByte())
            aapsLogger?.debug(LTag.PUMP, "Emulator: socket closed")
        }

        /**
         * Input stream that blocks until response bytes are available.
         */
        /**
         * Build a DanaR response packet with proper framing and CRC.
         */
        fun buildResponsePacket(command: Int, data: ByteArray): ByteArray {
            val dataLen = data.size + 2 // +2 for command bytes
            val packet = ByteArray(dataLen + 7)

            // Start marker
            packet[0] = 0x7E; packet[1] = 0x7E
            // Length (data + command, excluding type byte F1)
            packet[2] = dataLen.toByte()
            // Type
            packet[3] = 0xF1.toByte()
            // Command echo
            packet[4] = (command shr 8 and 0xFF).toByte()
            packet[5] = (command and 0xFF).toByte()
            // Data
            System.arraycopy(data, 0, packet, 6, data.size)
            // CRC (over type + command + data)
            val crc = CRC.getCrc16(packet, 3, dataLen + 1)
            packet[packet.size - 4] = (crc.toInt() shr 8 and 0xFF).toByte()
            packet[packet.size - 3] = (crc.toInt() and 0xFF).toByte()
            // End marker
            packet[packet.size - 2] = 0x2E; packet[packet.size - 1] = 0x2E

            return packet
        }

        inner class EmulatorInputStream : InputStream() {

            override fun read(): Int {
                if (!connected) throw IOException("Socket closed")
                return try {
                    responseQueue.take().toInt() and 0xFF
                } catch (_: InterruptedException) {
                    throw IOException("Read interrupted")
                }
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (!connected) throw IOException("Socket closed")
                val first = read()
                b[off] = first.toByte()
                var count = 1
                // Drain available bytes without blocking
                while (count < len) {
                    val next = responseQueue.poll() ?: break
                    b[off + count] = next
                    count++
                }
                return count
            }

            override fun available(): Int = responseQueue.size
        }

        /**
         * Output stream that parses DanaR packets and routes to emulator.
         */
        inner class EmulatorOutputStream : OutputStream() {

            private val writeBuffer = mutableListOf<Byte>()

            override fun write(b: Int) {
                writeBuffer.add(b.toByte())
                tryProcessPacket()
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                for (i in off until off + len) {
                    writeBuffer.add(b[i])
                }
                tryProcessPacket()
            }

            /**
             * Try to extract a complete DanaR packet from the write buffer.
             * Packet format: 7E 7E [len] F1 [CMD_HI CMD_LO] [params...] [CRC16] 2E 2E
             */
            private fun tryProcessPacket() {
                if (writeBuffer.size < 7) return // minimum packet size

                // Check start marker
                if (writeBuffer[0] != 0x7E.toByte() || writeBuffer[1] != 0x7E.toByte()) {
                    aapsLogger?.error(LTag.PUMP, "Emulator: wrong start marker, clearing buffer")
                    writeBuffer.clear()
                    return
                }

                val dataLen = writeBuffer[2].toInt() and 0xFF
                val totalLen = dataLen + 7 // 2 start + 1 len + 1 type + dataLen + 2 CRC + 2 end

                if (writeBuffer.size < totalLen) return // incomplete packet

                // Check end marker
                if (writeBuffer[totalLen - 2] != 0x2E.toByte() || writeBuffer[totalLen - 1] != 0x2E.toByte()) {
                    aapsLogger?.error(LTag.PUMP, "Emulator: wrong end marker")
                    writeBuffer.clear()
                    return
                }

                // Extract packet
                val packet = ByteArray(totalLen) { writeBuffer[it] }
                writeBuffer.subList(0, totalLen).clear()

                // Extract command (bytes 4-5, which is offset 3-4 in packet: type byte at 3, then cmd)
                val command = (packet[4].toInt() and 0xFF shl 8) or (packet[5].toInt() and 0xFF)

                // Extract params (after command, before CRC)
                val params = if (dataLen > 2) {
                    packet.copyOfRange(6, 3 + dataLen + 1) // offset 6 to 3+dataLen
                } else {
                    ByteArray(0)
                }

                // Process command
                val responseData = emulator.processCommand(command, params)

                // Build response packet
                val responsePacket = buildResponsePacket(command, responseData)

                // Enqueue response bytes for the input stream
                for (byte in responsePacket) {
                    responseQueue.put(byte)
                }
            }

        }
    }
}
