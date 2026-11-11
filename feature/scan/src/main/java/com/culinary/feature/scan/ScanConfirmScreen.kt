package com.culinary.feature.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.culinary.core.designsystem.component.CulinaryButton
import com.culinary.core.designsystem.component.CulinaryFilterPill
import com.culinary.core.designsystem.component.CulinaryInfoRow
import com.culinary.core.designsystem.component.CulinaryTextButton
import com.culinary.core.designsystem.component.CulinaryTextField
import com.culinary.core.designsystem.component.EmptyState
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.PageHeader
import com.culinary.core.designsystem.component.ResultPanel
import com.culinary.core.designsystem.component.SectionHeader
import com.culinary.core.designsystem.component.StatusTone
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
                .padding(bottom = CulinarySpacing.xl),
    ) {
        state.error?.let { message ->
            ErrorBanner(
                message = message,
                modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal),
            )
        }
        state.lastError?.let { message ->
            ErrorBanner(
                message = message,
                actionLabel = stringResource(R.string.scan_confirm_retry),
                onAction = onRetry,
                modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal),
            )
        }
        PageHeader(
            eyebrow = "SCAN",
            title = stringResource(R.string.scan_confirm_title),
            supporting = stringResource(R.string.scan_confirm_body),
        )

        Column(modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal)) {
            SectionHeader(title = stringResource(R.string.scan_confirm_scan_result))
            CulinaryInfoRow(
                label = stringResource(R.string.scan_confirm_code),
                value = capture.rawValue,
            )
            CulinaryInfoRow(
                label = stringResource(R.string.scan_confirm_format),
                value = capture.formatLabel,
            )
            CulinaryInfoRow(
                label = stringResource(R.string.scan_confirm_source),
                value = sourceLabel(capture.source),
            )

            state.previous?.let { previous -> AlreadyImportedPanel(previous = previous) }

            SectionHeader(title = stringResource(R.string.scan_confirm_target))
            if (state.ingredients.isEmpty() && !state.createNew) {
                Text(
                    text = stringResource(R.string.scan_confirm_no_ingredients),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.xs)) {
                CulinaryFilterPill(
                    text = stringResource(R.string.scan_confirm_new_ingredient),
                    selected = state.createNew,
                    onClick = { onCreateNewChange(true) },
                )
                state.ingredients.forEach { ingredient ->
                    CulinaryFilterPill(
                        text = ingredient.name,
                        selected = !state.createNew && state.selectedIngredientId == ingredient.id.value,
                        onClick = { onSelectIngredient(ingredient.id.value) },
                    )
                }
            }
            if (state.createNew) {
                CulinaryTextField(
                    value = state.newIngredientName,
                    onValueChange = onNewIngredientNameChange,
                    label = { Text(text = stringResource(R.string.scan_confirm_new_name)) },
                    singleLine = true,
                    modifier = Modifier.padding(top = CulinarySpacing.sm),
                )
            }

            Spacer(modifier = Modifier.height(CulinarySpacing.md))
            CulinaryTextField(
                value = state.quantity,
                onValueChange = onQuantityChange,
                label = { Text(text = stringResource(R.string.scan_confirm_quantity)) },
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(CulinarySpacing.block))
            CulinaryButton(
                text = stringResource(primaryActionLabel(state)),
                onClick = onConfirm,
                enabled = state.canConfirm,
                loading = state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.scan_confirm_handoff_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = CulinarySpacing.xs),
            )

            state.result?.let { result ->
                Spacer(modifier = Modifier.height(CulinarySpacing.md))
                ImportResultPanel(result = result)
            }

            Spacer(modifier = Modifier.height(CulinarySpacing.md))
            CulinaryTextButton(
                text = stringResource(R.string.scan_confirm_back_to_scan),
                onClick = onBackToScan,
            )
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
private fun AlreadyImportedPanel(previous: ScanPreviousImport) {
    Spacer(modifier = Modifier.height(CulinarySpacing.sm))
    ResultPanel(
        title = stringResource(R.string.scan_confirm_already),
        message =
            stringResource(
                R.string.scan_confirm_duplicate_body,
                formatImportTime(previous.entry.occurredAt),
                formatAmount(previous.entry.onHandDelta),
                previous.entry.stockUnit.code,
            ),
        tone = StatusTone.Warning,
    )
}

@Composable
private fun ImportResultPanel(result: ScanImportOutcome) {
    val details =
        listOfNotNull(
            if (result.replayed) stringResource(R.string.scan_confirm_replayed) else null,
            result.batchCode?.let { code -> stringResource(R.string.scan_confirm_batch) + " " + code },
        )
    ResultPanel(
        title = stringResource(R.string.scan_confirm_saved_badge),
        message =
            stringResource(
                R.string.scan_confirm_saved,
                result.ingredientName,
                formatAmount(result.amount),
                result.unitCode,
            ),
        tone = StatusTone.Success,
        details = details,
    )
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
