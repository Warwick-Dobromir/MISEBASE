package com.culinary.startup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.culinary.R
import com.culinary.core.data.startup.RecoveryReason

@Composable
fun StartupLoadingScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.startup_loading),
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/** 数据库版本高于 App：只提示升级，不降级、不重建。 */
@Composable
fun IncompatibleScreen(
    databaseVersion: Int,
    appSchemaVersion: Int,
    modifier: Modifier = Modifier,
) {
    CenteredMessage(
        title = stringResource(R.string.startup_incompatible_title),
        body = stringResource(R.string.startup_incompatible_body, databaseVersion, appSchemaVersion),
        modifier = modifier,
    )
}

/** 损坏/迁移失败：进入 Recovery，保留数据；只提供重试，不提供清库。 */
@Composable
fun RecoveryScreen(
    reason: RecoveryReason,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CenteredMessage(
        title = stringResource(R.string.startup_recovery_title),
        body = stringResource(R.string.startup_recovery_body, reason.name),
        modifier = modifier,
        action = {
            Button(onClick = onRetry) {
                Text(text = stringResource(R.string.action_retry))
            }
        },
    )
}

@Composable
private fun CenteredMessage(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp, bottom = 20.dp),
        )
        action?.invoke()
    }
}
