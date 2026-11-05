package com.culinary.startup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.culinary.core.designsystem.component.CulinaryButton
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.Eyebrow
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.designsystem.theme.CulinaryTextStyles

/** 启动加载页：极简居中，不用全屏插画。 */
@Composable
fun StartupLoadingScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(CulinarySpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Eyebrow(text = stringResource(R.string.app_name))
        Spacer(modifier = Modifier.height(CulinarySpacing.xl))
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 1.5.dp,
            color = MaterialTheme.colorScheme.secondary,
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.md))
        Text(
            text = stringResource(R.string.startup_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            CulinaryButton(
                text = stringResource(R.string.action_retry),
                onClick = onRetry,
            )
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
                .padding(CulinarySpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CulinaryRule(modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(CulinarySpacing.lg))
        Text(
            text = title,
            style = CulinaryTextStyles.pageTitle,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.sm))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.lg))
        action?.invoke()
    }
}
