package com.culinary.feature.ingredients

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.culinary.core.designsystem.component.ConfirmDialog
import com.culinary.core.designsystem.component.CulinaryButton
import com.culinary.core.designsystem.component.CulinaryButtonVariant
import com.culinary.core.designsystem.component.CulinaryTextButton
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.component.PageHeader
import com.culinary.core.designsystem.component.SectionHeader
import com.culinary.core.designsystem.component.StatusBadge
import com.culinary.core.designsystem.component.StatusTone
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.domain.model.IngredientConversionView

/** 食材详情路由：档案、默认单位、换算；可编辑或归档。 */
@Composable
fun IngredientDetailRoute(
    onEdit: (String) -> Unit,
    onArchived: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IngredientDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showArchiveConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.archived) {
        if (state.archived) onArchived()
    }

    Column(modifier = modifier.fillMaxSize()) {
        state.error?.let { message ->
            ErrorBanner(
                message = message,
                actionLabel = stringResource(R.string.ingredients_retry),
                onAction = viewModel::load,
                modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal),
            )
        }
        when {
            state.isLoading -> LoadingRows()
            state.detail == null ->
                Text(
                    text = stringResource(R.string.ingredients_load_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(CulinarySpacing.pageHorizontal),
                )

            else ->
                IngredientDetailContent(
                    detailState = state,
                    onEdit = onEdit,
                    onArchive = { showArchiveConfirm = true },
                )
        }
    }

    if (showArchiveConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.ingredients_archive_title),
            message = stringResource(R.string.ingredients_archive_message),
            confirmLabel = stringResource(R.string.ingredients_archive),
            onConfirm = {
                showArchiveConfirm = false
                viewModel.archive()
            },
            onDismiss = { showArchiveConfirm = false },
            dismissLabel = stringResource(R.string.ingredients_cancel),
            destructive = true,
        )
    }
}

@Composable
internal fun IngredientDetailContent(
    detailState: IngredientDetailUiState,
    onEdit: (String) -> Unit,
    onArchive: () -> Unit,
) {
    val detail = detailState.detail ?: return
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = CulinarySpacing.xl),
    ) {
        PageHeader(
            eyebrow = "INGREDIENT",
            title = detail.ingredient.name,
            supporting = detail.ingredient.category ?: stringResource(R.string.ingredients_uncategorized),
        )
        Column(modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal)) {
            StatusBadge(
                text =
                    stringResource(
                        if (detail.ingredient.isActive) {
                            R.string.ingredients_active
                        } else {
                            R.string.ingredients_archived
                        },
                    ),
                tone = if (detail.ingredient.isActive) StatusTone.Success else StatusTone.Neutral,
            )
        }

        IngredientSummary(detail = detail, onEdit = onEdit, onArchive = onArchive)
        ConversionsSection(conversions = detail.conversions)
    }
}

@Composable
private fun IngredientSummary(
    detail: com.culinary.core.domain.model.IngredientDetail,
    onEdit: (String) -> Unit,
    onArchive: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal)) {
        SectionHeader(title = stringResource(R.string.ingredients_default_unit))
        Text(
            text =
                detail.defaultUnit?.let { "${it.name}（${it.symbol}）" }
                    ?: stringResource(R.string.ingredients_no_unit),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        if (!detail.ingredient.description.isNullOrBlank()) {
            SectionHeader(title = stringResource(R.string.ingredients_description_label))
            Text(
                text = detail.ingredient.description.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(modifier = Modifier.height(CulinarySpacing.block))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.sm),
        ) {
            CulinaryButton(
                text = stringResource(R.string.ingredients_edit),
                onClick = { onEdit(detail.ingredient.id.value) },
                variant = CulinaryButtonVariant.Secondary,
                icon = CulinaryIcons.Edit,
            )
            CulinaryTextButton(
                text = stringResource(R.string.ingredients_archive),
                onClick = onArchive,
                danger = true,
            )
        }
    }
}

@Composable
private fun ConversionsSection(conversions: List<IngredientConversionView>) {
    Column(modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal)) {
        SectionHeader(title = stringResource(R.string.ingredients_conversions, conversions.size))
        if (conversions.isEmpty()) {
            Text(
                text = stringResource(R.string.ingredients_no_conversions),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            conversions.forEach { conversion -> ConversionRow(conversion) }
        }
    }
}

@Composable
private fun ConversionRow(conversion: IngredientConversionView) {
    val from = conversion.fromUnit?.symbol ?: conversion.fromUnitId.value
    val to = conversion.toUnit?.symbol ?: conversion.toUnitId.value
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = CulinarySpacing.sm),
    ) {
        Text(
            text =
                stringResource(
                    R.string.ingredients_conversion_row,
                    from,
                    to,
                    formatFactor(conversion.factor),
                ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (!conversion.note.isNullOrBlank()) {
            Text(
                text = conversion.note.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatFactor(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
