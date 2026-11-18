package com.culinary.feature.ingredients

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.culinary.core.designsystem.component.ArchiveDetailBar
import com.culinary.core.designsystem.component.ArchiveDetailHeading
import com.culinary.core.designsystem.component.ArchiveHeroImage
import com.culinary.core.designsystem.component.ArchiveModuleHeader
import com.culinary.core.designsystem.component.ArchiveRelatedRow
import com.culinary.core.designsystem.component.ConfirmDialog
import com.culinary.core.designsystem.component.CulinaryTextButton
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.component.StatusBadge
import com.culinary.core.designsystem.component.StatusTone
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.domain.model.IngredientConversionView
import com.culinary.core.domain.model.KitchenRecordKind
import com.culinary.core.domain.model.KitchenRelatedRecord

/** 食材详情：沿用酱汁/菜谱档案外壳，保留食材自身字段与单位换算。 */
@Composable
fun IngredientDetailRoute(
    onEdit: (String) -> Unit,
    onArchived: () -> Unit,
    onOpenRelation: (KitchenRecordKind, String) -> Unit = { _, _ -> },
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: IngredientDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showArchiveConfirm by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }

    LaunchedEffect(state.archived) {
        if (state.archived) onArchived()
    }

    Column(modifier = modifier.fillMaxSize()) {
        ArchiveDetailBar(
            editLabel = "编辑档案 ↗",
            onBack = onBack,
            onEdit = { state.detail?.ingredient?.id?.value?.let(onEdit) },
        )
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
                    onArchive = { showArchiveConfirm = true },
                    onOpenRelation = onOpenRelation,
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
    onArchive: () -> Unit,
    onOpenRelation: (KitchenRecordKind, String) -> Unit,
) {
    val detail = detailState.detail ?: return
    val ingredient = detail.ingredient
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = CulinarySpacing.xl),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal),
        ) {
            Spacer(modifier = Modifier.height(CulinarySpacing.lg))
            ArchiveDetailHeading(
                categoryLabel = "INGREDIENT · " + (
                    detailState.categoryPath.joinToString(" / ").ifBlank {
                        ingredient.category ?: stringResource(R.string.ingredients_uncategorized)
                    }
                ),
                englishTitle = ingredient.englishName,
                chineseTitle = ingredient.name,
                description = ingredient.description,
            )
        }
        detailState.imageBytes?.let { bytes ->
            ArchiveHeroImage(
                bytes = bytes,
                modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal),
            )
        }
        Column(modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (ingredient.isSeed) {
                    StatusBadge(
                        text = stringResource(R.string.ingredients_seed_badge),
                        tone = StatusTone.Accent,
                    )
                }
                StatusBadge(
                    text = stringResource(
                        if (ingredient.isActive) R.string.ingredients_active else R.string.ingredients_archived,
                    ),
                    tone = if (ingredient.isActive) StatusTone.Success else StatusTone.Neutral,
                )
            }

            Spacer(modifier = Modifier.height(CulinarySpacing.lg))
            ArchiveModuleHeader(number = "01", english = "BASIC INFORMATION", chinese = "基本信息")
            IngredientBasicInformation(
                detail = detail,
                categoryPath = detailState.categoryPath,
            )

            Spacer(modifier = Modifier.height(CulinarySpacing.xl))
            ArchiveModuleHeader(number = "02", english = "CONVERSIONS", chinese = "计量换算")
            ConversionsSection(conversions = detail.conversions)

            Spacer(modifier = Modifier.height(CulinarySpacing.xl))
            ArchiveModuleHeader(number = "03", english = "APPLICATIONS", chinese = "应用关系")
            IngredientApplicationsSection(
                relations = detailState.relations,
                onOpenRelation = onOpenRelation,
            )

            Spacer(modifier = Modifier.height(CulinarySpacing.xl))
            CulinaryTextButton(
                text = stringResource(R.string.ingredients_archive),
                onClick = onArchive,
                danger = true,
            )
        }
    }
}

@Composable
private fun IngredientBasicInformation(
    detail: com.culinary.core.domain.model.IngredientDetail,
    categoryPath: List<String>,
) {
    val ingredient = detail.ingredient
    val fields = listOfNotNull(
        ("所属分类" to categoryPath.joinToString(" / ").ifBlank { ingredient.category.orEmpty() })
            .takeIf { it.second.isNotBlank() },
        ("英文名称" to ingredient.englishName.orEmpty()).takeIf { it.second.isNotBlank() },
        ("学名" to ingredient.scientificName.orEmpty()).takeIf { it.second.isNotBlank() },
        ("常见品种" to ingredient.exampleProducts.orEmpty()).takeIf { it.second.isNotBlank() },
        ("常用计量单位" to detail.defaultUnit?.let { "${it.name} / ${it.symbol}" }.orEmpty())
            .takeIf { it.second.isNotBlank() },
        ("风味与特性" to ingredient.flavorProfile.orEmpty()).takeIf { it.second.isNotBlank() },
        ("时令" to ingredient.seasonality.orEmpty()).takeIf { it.second.isNotBlank() },
        ("产地" to ingredient.origin.orEmpty()).takeIf { it.second.isNotBlank() },
        ("挑选说明" to ingredient.selectionNotes.orEmpty()).takeIf { it.second.isNotBlank() },
        ("预处理" to ingredient.prepNotes.orEmpty()).takeIf { it.second.isNotBlank() },
        ("储存说明" to ingredient.storageNotes.orEmpty()).takeIf { it.second.isNotBlank() },
        ("过敏原" to ingredient.allergenNotes.orEmpty()).takeIf { it.second.isNotBlank() },
    )
    fields.forEachIndexed { index, (label, value) ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1.25f),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }
        if (index < fields.lastIndex) {
            com.culinary.core.designsystem.component.CulinaryRule()
        }
    }
}

@Composable
private fun IngredientApplicationsSection(
    relations: List<KitchenRelatedRecord>,
    onOpenRelation: (KitchenRecordKind, String) -> Unit,
) {
    val sauces = relations.filter { it.targetKind == KitchenRecordKind.SAUCE }
    val recipes = relations.filter { it.targetKind == KitchenRecordKind.RECIPE }

    if (sauces.isEmpty() && recipes.isEmpty()) {
        Text(
            text = "暂未添加应用关系",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    if (sauces.isNotEmpty()) {
        Text(
            text = "酱汁",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = CulinarySpacing.xs),
        )
        sauces.forEach { relation ->
            ArchiveRelatedRow(
                title = relation.targetLabel,
                subtitle = null,
                relationLabel = relation.relationLabel,
                onClick = { onOpenRelation(relation.targetKind, relation.targetRecordId) },
            )
        }
    }

    if (recipes.isNotEmpty()) {
        Text(
            text = "菜谱",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = CulinarySpacing.md),
        )
        recipes.forEach { relation ->
            ArchiveRelatedRow(
                title = relation.targetLabel,
                subtitle = null,
                relationLabel = relation.relationLabel,
                onClick = { onOpenRelation(relation.targetKind, relation.targetRecordId) },
            )
        }
    }
}

@Composable
private fun ConversionsSection(conversions: List<IngredientConversionView>) {
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
            text = stringResource(
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
