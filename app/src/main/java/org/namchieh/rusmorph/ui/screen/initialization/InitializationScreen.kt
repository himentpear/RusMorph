package org.namchieh.rusmorph.ui.screen.initialization

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.namchieh.rusmorph.R
import org.namchieh.rusmorph.data.local.InitializationFailureReason
import org.namchieh.rusmorph.data.local.InitializationState
import org.namchieh.rusmorph.ui.common.RusMorphPrimaryButton
import org.namchieh.rusmorph.ui.common.TerminalBackground
import org.namchieh.rusmorph.ui.common.TerminalLabel
import org.namchieh.rusmorph.ui.common.TerminalLoadingState
import org.namchieh.rusmorph.ui.common.TerminalPanel

@Composable
fun InitializationScreen(
    state: InitializationState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TerminalBackground(modifier.fillMaxSize()) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        when (state) {
            InitializationState.NotStarted -> {
                TerminalLoadingState(stringResource(R.string.initialization_checking))
            }
            InitializationState.Initializing -> {
                TerminalLoadingState(stringResource(R.string.initialization_importing))
            }
            is InitializationState.Failed -> {
                TerminalPanel {
                TerminalLabel("DATABASE INITIALIZATION ERROR")
                Text(
                    text = stringResource(state.reason.messageResource()),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                RusMorphPrimaryButton(stringResource(R.string.retry_import), onRetry)
                }
            }
            is InitializationState.Ready -> Unit
        }
    }
    }
}

private fun InitializationFailureReason.messageResource(): Int = when (this) {
    InitializationFailureReason.ASSET_MISSING -> R.string.initialization_asset_missing
    InitializationFailureReason.INVALID_DATA -> R.string.initialization_invalid_data
    InitializationFailureReason.DATABASE_WRITE -> R.string.initialization_database_error
    InitializationFailureReason.UNKNOWN -> R.string.initialization_failed
}
