package app.aaps.pump.danar.compose

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.AapsCard
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.pump.dana.R

@Composable
fun DanaRPairWizardScreen(
    viewModel: DanaRPairWizardViewModel,
    onCancel: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    AnimatedContent(
        targetState = state.step,
        label = "pair_wizard_step"
    ) { step ->
        when (step) {
            PairWizardStep.CONFIGURE -> ConfigureStep(
                state = state,
                onPasswordChange = viewModel::updatePassword,
                onRefreshDevices = viewModel::refreshBondedDevices,
                onSelectDevice = viewModel::selectDevice,
                onCancel = onCancel
            )

            PairWizardStep.FINISHED  -> FinishedStep(
                state = state,
                onGoBack = viewModel::goBack,
                onFinish = viewModel::finish
            )
        }
    }
}

@Composable
private fun ConfigureStep(
    state: DanaRPairWizardUiState,
    onPasswordChange: (String) -> Unit,
    onRefreshDevices: () -> Unit,
    onSelectDevice: (BondedDevice) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(AapsSpacing.extraLarge),
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.extraLarge)
    ) {
        // Password input
        Text(
            text = stringResource(R.string.enter_pump_password),
            style = MaterialTheme.typography.titleMedium
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = { Text(stringResource(R.string.danar_password_title)) },
            placeholder = { Text(stringResource(R.string.password_hint)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // Open Bluetooth settings button
        OutlinedButton(
            onClick = {
                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Filled.Bluetooth,
                contentDescription = null,
                modifier = Modifier.padding(end = AapsSpacing.medium)
            )
            Text(stringResource(R.string.open_bluetooth_settings))
        }

        HorizontalDivider()

        // Device list header with refresh
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.select_paired_device),
                style = MaterialTheme.typography.titleMedium
            )
            IconButton(onClick = onRefreshDevices) {
                Icon(Icons.Filled.Refresh, contentDescription = stringResource(app.aaps.core.ui.R.string.refresh))
            }
        }

        // Device list
        if (state.bondedDevices.isEmpty()) {
            Text(
                text = stringResource(R.string.no_paired_devices),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
            ) {
                items(state.bondedDevices, key = { it.address }) { device ->
                    DeviceItem(
                        device = device,
                        enabled = state.password.isNotEmpty(),
                        onClick = { onSelectDevice(device) }
                    )
                }
            }
        }

        // Cancel button
        Spacer(modifier = Modifier.height(AapsSpacing.medium))
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(app.aaps.core.ui.R.string.cancel))
        }
    }
}

@Composable
private fun DeviceItem(
    device: BondedDevice,
    enabled: Boolean,
    onClick: () -> Unit
) {
    AapsCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AapsSpacing.extraLarge),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Filled.Bluetooth,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FinishedStep(
    state: DanaRPairWizardUiState,
    onGoBack: () -> Unit,
    onFinish: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(AapsSpacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when {
            state.isConnecting              -> {
                CircularProgressIndicator(modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(AapsSpacing.xxLarge))
                Text(
                    text = stringResource(R.string.connecting_verifying),
                    style = MaterialTheme.typography.titleMedium
                )
                state.selectedDevice?.let {
                    Text(
                        text = it.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            state.passwordVerified == true  -> {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(AapsSpacing.xxLarge))
                Text(
                    text = stringResource(R.string.password_ok),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(32.dp))
                FilledTonalButton(
                    onClick = onFinish,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.done))
                }
            }

            state.passwordVerified == false -> {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(AapsSpacing.xxLarge))
                Text(
                    text = stringResource(R.string.password_wrong),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(32.dp))
                OutlinedButton(
                    onClick = onGoBack,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(app.aaps.core.ui.R.string.back))
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Configure Step - With Devices")
@Composable
private fun ConfigureStepPreview() {
    MaterialTheme {
        ConfigureStep(
            state = DanaRPairWizardUiState(
                password = "1234",
                bondedDevices = listOf(
                    BondedDevice("DAN12345AB", "AA:BB:CC:DD:EE:01"),
                    BondedDevice("DAN67890CD", "AA:BB:CC:DD:EE:02")
                )
            ),
            onPasswordChange = {},
            onRefreshDevices = {},
            onSelectDevice = {},
            onCancel = {}
        )
    }
}

@Preview(showBackground = true, name = "Configure Step - Empty")
@Composable
private fun ConfigureStepEmptyPreview() {
    MaterialTheme {
        ConfigureStep(
            state = DanaRPairWizardUiState(),
            onPasswordChange = {},
            onRefreshDevices = {},
            onSelectDevice = {},
            onCancel = {}
        )
    }
}

@Preview(showBackground = true, name = "Finished - Connecting")
@Composable
private fun FinishedStepConnectingPreview() {
    MaterialTheme {
        FinishedStep(
            state = DanaRPairWizardUiState(
                step = PairWizardStep.FINISHED,
                isConnecting = true,
                selectedDevice = BondedDevice("DAN12345AB", "AA:BB:CC:DD:EE:01")
            ),
            onGoBack = {},
            onFinish = {}
        )
    }
}

@Preview(showBackground = true, name = "Finished - Password OK")
@Composable
private fun FinishedStepSuccessPreview() {
    MaterialTheme {
        FinishedStep(
            state = DanaRPairWizardUiState(
                step = PairWizardStep.FINISHED,
                isConnecting = false,
                passwordVerified = true
            ),
            onGoBack = {},
            onFinish = {}
        )
    }
}

@Preview(showBackground = true, name = "Finished - Wrong Password")
@Composable
private fun FinishedStepErrorPreview() {
    MaterialTheme {
        FinishedStep(
            state = DanaRPairWizardUiState(
                step = PairWizardStep.FINISHED,
                isConnecting = false,
                passwordVerified = false
            ),
            onGoBack = {},
            onFinish = {}
        )
    }
}
