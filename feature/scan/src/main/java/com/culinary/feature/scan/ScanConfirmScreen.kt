package com.culinary.feature.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.culinary.core.designsystem.component.EmptyState
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.SectionHeader
import com.culinary.core.designsystem.component.StatusBadge
import com.culinary.core.designsystem.component.StatusTone
import com.culinary.core.designsystem.theme.CulinaryColorScheme
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.domain.barcode.ScanSource
import com.culinary.core.domain.scan.ScanPreviousImport
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 导入确认页（纯 UI）。
 *
 * 展示识别结果（条码值/格式/来源）、商品/食材匹配结果、幂等提示与导入结果；
 * 用户确认后由 ViewModel 调用 `feature:products` 与 `feature:inventory` 的接口，
 * 页面本身不写数据库。
 */
@Suppress("LongMethod") // 识别结果、导入目标、数量与结果同屏；拆分只会把同一份状态拆成更多参数。
@Composable
internal fun ScanConfirmScreen(
    state: ScanConfirmUiState,
    onSelectIngredient: (String) -> Unit,
    onCreateNewChange: (Boolean) -> Unit,
    onNewIngredientNameChange: (String) -> Unit,
    onQuantityChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    onBackToScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val capture = state.capture
    if (capture == null) {
        EmptyState(
            title = stringResource(R.string.scan_confirm_missing),
            description = stringResource(R.string.scan_confirm_missing_body),
            actionLabel = stringResource(R.string.scan_confirm_back_to_scan),
            onAction = onBackToScan,
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CulinarySpacing.md),
        verticalArrangement = Arrangement.Top,
    ) {
        state.error?.let { message ->
            ErrorBanner(message = message)
        }
        state.lastError?.let { message ->
            ErrorBanner(
                message = message,
                actionLabel = stringResource(R.string.scan_confirm_retry),
                onAction = onRetry,
            )
        }
        Text(
            text = stringResource(R.string.scan_confirm_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(R.string.scan_confirm_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionHeader(title = stringResource(R.string.scan_confirm_scan_result))
        InfoRow(label = stringResource(R.string.scan_confirm_code), value = capture.rawValue)
        InfoRow(label = stringResource(R.string.scan_confirm_format), value = capture.formatLabel)
        InfoRow(label = stringResource(R.string.scan_confirm_source), value = sourceLabel(capture.source))

        state.previous?.let { previous -> AlreadyImportedPanel(previous = previous) }

        SectionHeader(title = stringResource(R.string.scan_confirm_target))
        if (state.ingredients.isEmpty() && !state.createNew) {
            Text(
                text = stringResource(R.string.scan_confirm_no_ingredients),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilterChip(
            selected = state.createNew,
            onClick = { onCreateNewChange(true) },
            label = { Text(text = stringResource(R.string.scan_confirm_new_ingredient)) },
        )
        if (state.createNew) {
            OutlinedTextField(
                value = state.newIngredientName,
                onValueChange = onNewIngredientNameChange,
                label = { Text(text = stringResource(R.string.scan_confirm_new_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        state.ingredients.forEach { ingredient ->
            FilterChip(
                selected = !state.createNew && state.selectedIngredientId == ingredient.id.value,
                onClick = { onSelectIngredient(ingredient.id.value) },
                label = { Text(text = ingredient.name) },
                modifier = Modifier.padding(top = CulinarySpacing.xxs),
            )
        }

        Spacer(modifier = Modifier.height(CulinarySpacing.md))
        OutlinedTextField(
            value = state.quantity,
            onValueChange = onQuantityChange,
            label = { Text(text = stringResource(R.string.scan_confirm_quantity)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.md))
        Button(onClick = onConfirm, enabled = state.canConfirm) {
            Text(text = stringResource(primaryActionLabel(state)))
        }

        state.result?.let { result -> ImportResultPanel(result = result) }

        Spacer(modifier = Modifier.height(CulinarySpacing.md))
        TextButton(onClick = onBackToScan) {
            Text(text = stringResource(R.string.scan_confirm_back_to_scan))
        }
    }
}

/**
 * 主按钮文案：
 * - 失败过：重试（复用同一个 operation id）；
 * - 命中历史导入：再次入库；
 * - 其它：确认导入。
 */
private fun primaryActionLabel(state: ScanConfirmUiState): Int =
    when {
        state.isSubmitting -> R.string.scan_confirm_saving
        state.lastError != null -> R.string.scan_confirm_retry
        state.isDuplicate -> R.string.scan_confirm_again
        else -> R.string.scan_confirm_action
    }

@Composable
private fun InfoRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AlreadyImportedPanel(previous: ScanPreviousImport) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.sm),
        color = CulinaryColorScheme.current.warning,
        contentColor = CulinaryColorScheme.current.onWarning,
    ) {
        Column(modifier = Modifier.padding(CulinarySpacing.sm)) {
            StatusBadge(
                text = stringResource(R.string.scan_confirm_already),
                tone = StatusTone.Warning,
            )
            Text(
                text =
                    stringResource(
                        R.string.scan_confirm_duplicate_body,
                        formatImportTime(previous.entry.occurredAt),
                        formatAmount(previous.entry.onHandDelta),
                        previous.entry.stockUnit.code,
                    ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = CulinarySpacing.xxs),
            )
        }
    }
}

@Composable
private fun ImportResultPanel(result: ScanImportOutcome) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.md),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(modifier = Modifier.padding(CulinarySpacing.sm)) {
            StatusBadge(
                text = stringResource(R.string.scan_confirm_saved_badge),
                tone = StatusTone.Success,
            )
            Text(
                text =
                    stringResource(
                        R.string.scan_confirm_saved,
                        result.ingredientName,
                        formatAmount(result.amount),
                        result.unitCode,
                    ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = CulinarySpacing.xxs),
            )
            if (result.replayed) {
                Text(
                    text = stringResource(R.string.scan_confirm_replayed),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = CulinarySpacing.xxs),
                )
            }
            result.batchCode?.let { code ->
                Text(
                    text = "${stringResource(R.string.scan_confirm_batch)} $code",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = CulinarySpacing.xxs),
                )
            }
        }
    }
}

@Composable
private fun sourceLabel(source: ScanSource): String =
    stringResource(
        when (source) {
            ScanSource.CAMERA -> R.string.scan_source_camera
            ScanSource.MANUAL -> R.string.scan_source_manual
        },
    )

private fun formatAmount(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

private fun formatImportTime(instant: Instant): String =
    DateTimeFormatter
        .ofPattern("MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(instant)
