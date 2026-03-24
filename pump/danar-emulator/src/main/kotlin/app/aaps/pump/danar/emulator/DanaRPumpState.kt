package app.aaps.pump.danar.emulator

import app.aaps.pump.dana.emulator.HistoryEventStore

/**
 * Mutable state of the emulated DanaR pump.
 * Inspectable from tests for assertions.
 */
class DanaRPumpState {

    // History
    val historyStore = HistoryEventStore()

    // Device info
    var serialNumber: String = "DAN12345AB"
    var shippingCountry: String = "INT"
    var shippingDate: Triple<Int, Int, Int> = Triple(2024, 1, 1) // year, month, day
    var hwModel: Int = 0x03  // DanaR
    var protocol: Int = 0x02  // DanaR v2
    var productCode: Int = 0

    // Password (numeric, XOR 0x3463 when sent)
    var password: Int = 1234

    // Basal
    var activeProfile: Int = 0
    var basalProfiles: Array<DoubleArray> = Array(4) { DoubleArray(24) { 1.0 } }
    var maxBasal: Double = 3.0
    var basalStep: Double = 0.01

    // Temp basal
    var isTempBasalRunning: Boolean = false
    var tempBasalPercent: Int = 0
    var tempBasalDurationMinutes: Int = 0
    var tempBasalStartTime: Long = 0

    // Bolus
    var maxBolus: Double = 10.0
    var bolusStep: Double = 0.05
    var lastBolusAmount: Double = 0.0
    var lastBolusTime: Long = 0
    var isExtendedBolusRunning: Boolean = false
    var extendedBolusAmount: Double = 0.0
    var extendedBolusDurationHalfHours: Int = 0
    var isExtendedEnabled: Boolean = true

    // Reservoir & battery
    var reservoirRemainingUnits: Double = 150.0
    var batteryRemaining: Int = 80

    // Daily totals
    var dailyTotalUnits: Double = 5.0
    var maxDailyTotalUnits: Double = 25.0

    // Current basal rate
    var currentBasal: Double = 1.0

    // IOB
    var iob: Double = 0.0

    // Time (pump internal clock)
    var pumpTimeMillis: Long = System.currentTimeMillis()

    // Status
    var isSuspended: Boolean = false
    var isDualBolusRunning: Boolean = false
    var calculatorEnabled: Boolean = true
    var bolusBlocked: Boolean = false

    // User options
    var timeDisplayType24: Boolean = true
    var buttonScroll: Boolean = false
    var beepAndAlarm: Int = 1  // 1=Sound
    var lcdOnTimeSec: Int = 15
    var backlightOnTimeSec: Int = 5
    var glucoseUnit: Int = 0  // 0=mg/dL, 1=mmol/L
    var shutdownHour: Int = 0
    var lowReservoirRate: Int = 20
}
