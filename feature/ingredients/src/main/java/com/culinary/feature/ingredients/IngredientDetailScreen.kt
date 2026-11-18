package com.culinary.feature.ingredients

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.culinary.core.designsystem.component.ArchiveDetailBar
import com.culinary.core.designsystem.component.ArchiveDetailFooter
import com.culinary.core.designsystem.component.ArchiveDetailHeading
import com.culinary.core.designsystem.component.ArchiveHeroImage
import com.culinary.core.designsystem.component.ArchiveMetaGrid
import com.culinary.core.designsystem.component.ArchiveMetaItem
import com.culinary.core.designsystem.component.ArchiveModuleHeader
import com.culinary.core.designsystem.component.ArchiveProse
import com.culinary.core.designsystem.component.ArchiveRelatedRow
import com.culinary.core.designsystem.component.ConfirmDialog
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.CulinaryTextButton
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.component.StatusBadge
import com.culinary.core.designsystem.component.StatusTone
import com.culinary.core.designsystem.component.formatArchiveTimestamp
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.domain.model.IngredientConversionView
import com.culinary.core.domain.model.KitchenRecordKind
import com.culinary.core.domain.model.KitchenRelatedRecord

/** 食材展示路由：保留原有编辑、归档、分类、图片、换算与关系入口。 */
@Composable
fun IngredientDetailRoute(
    onEdit: (String) -> Unit,
    onArchived: () -> Unit,
    onOpenRelation: (KitchenRecordKind, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    viewModel: IngredientDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showArchiveConfirm by remember { mutableStateOf(false) }
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }

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
                    onOpenRelation = onOpenRelation,
                    onBack = { dispatcher?.onBackPressed() },
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
    onOpenRelation: (KitchenRecordKind, String) -> Unit,
    onBack: () -> Unit = {},
) {
    val detail = detailState.detail ?: return
    val ingredient = detail.ingredient
    Column(modifier = Modifier.fillMaxSize()) {
        ArchiveDetailBar(
            editLabel = "编辑档案 ↗",
            onBack = onBack,
            onEdit = { onEdit(ingredient.id.value) },
        )
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = CulinarySpacing.pageHorizontal)
                    .padding(bottom = CulinarySpacing.xl),
        ) {
            Spacer(modifier = Modifier.height(CulinarySpacing.lg))
            ArchiveDetailHeading(
                categoryLabel =
                    detailState.categoryPath.joinToString(" / ").ifBlank {
                        ingredient.category ?: stringResource(R.string.ingredients_uncategorized)
                    },
                englishTitle = ingredient.englishName,
                chineseTitle = ingredient.name,
                description = ingredient.description,
            )
            detailState.imageBytes?.let { bytes ->
                ArchiveHeroImage(
                    bytes = bytes,
                    modifier = Modifier.padding(bottom = CulinarySpacing.lg),
                )
            }
            ArchiveMetaGrid(
                items =
                    listOf(
                        ArchiveMetaItem("DEFAULT UNIT", detail.defaultUnit?.let { "${it.name}（${it.symbol}）" }.orEmpty()),
                        ArchiveMetaItem("SCIENTIFIC NAME", ingredient.scientificName.orEmpty()),
                        ArchiveMetaItem("STATUS", if (ingredient.isActive) "在用" else "已归档"),
                    ),
            )
            if (ingredient.isSeed) {
                StatusBadge(
                    text = stringResource(R.string.ingredients_seed_badge),
                    tone = StatusTone.Accent,
                )
                Spacer(modifier = Modifier.height(CulinarySpacing.sm))
            }

            if (!ingredient.flavorProfile.isNullOrBlank()) {
                ArchiveModuleHeader(number = "01", english = "FLAVOR PROFILE", chinese = "风味特性")
                DetailField("风味描述", ingredient.flavorProfile)
            }

            if (!ingredient.prepNotes.isNullOrBlank() || !ingredient.exampleProducts.isNullOrBlank()) {
                ArchiveModuleHeader(number = "02", english = "CULINARY USE", chinese = "烹饪用途与预处理")
                DetailField("烹饪用途 / 预处理说明", ingredient.prepNotes)
                DetailField("示例商品 / 常见规格", ingredient.exampleProducts)
            }

            val professionalFields =
                listOf(
                    "时令" to ingredient.seasonality,
                    "产地" to ingredient.origin,
                    "挑选标准" to ingredient.selectionNotes,
                    "储存建议" to ingredient.storageNotes,
                    "过敏原提示" to ingredient.allergenNotes,
                    "原始备注" to ingredient.seedNote,
                ).filter { !it.second.isNullOrBlank() }
            if (professionalFields.isNotEmpty()) {
                ArchiveModuleHeader(number = "03", english = "PROFESSIONAL NOTES", chinese = "专业信息")
                professionalFields.forEach { (label, value) -> DetailField(label, value) }
            }

            ArchiveModuleHeader(number = "04", english = "APPLICATION", chinese = "应用关系")
            IngredientRelationsSection(detailState.relations, onOpenRelation)

            ArchiveModuleHeader(number = "05", english = "UNIT CONVERSIONS", chinese = "计量换算")
            ConversionsSection(conversions = detail.conversions)

            Spacer(modifier = Modifier.height(CulinarySpacing.lg))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                CulinaryTextButton(
                    text = stringResource(R.string.ingredients_archive),
                    onClick = onArchive,
                    danger = true,
                )
            }
            Spacer(modifier = Modifier.height(CulinarySpacing.lg))
            ArchiveDetailFooter(
                updatedLabel =
                    "最后更新 " +
                        (formatArchiveTimestamp(ingredient.updatedAtEpochMillis) ?: "更新时间不可用"),
                versionLabel = "正式档案",
            )
        }
    }
}

@Composable
private fun DetailField(label: String, value: String?) {
    value?.takeIf { it.isNotBlank() }?.let {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = CulinarySpacing.md)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(CulinarySpacing.xxs))
            ArchiveProse(it)
        }
    }
}

@Composable
private fun IngredientRelationsSection(
    relations: List<KitchenRelatedRecord>,
    onOpenRelation: (KitchenRecordKind, String) -> Unit,
) {
    val sauces = relations.filter { it.targetKind == KitchenRecordKind.SAUCE }
    val recipes = relations.filter { it.targetKind == KitchenRecordKind.RECIPE }

    Text(
        text = "应用于酱汁",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
    if (sauces.isEmpty()) {
        EmptyRelationText("暂未应用于酱汁")
    } else {
        sauces.forEach { relation ->
            ArchiveRelatedRow(
                title = relation.targetLabel,
                subtitle = null,
                relationLabel = relation.relationLabel,
                onClick = { onOpenRelation(relation.targetKind, relation.targetRecordId) },
            )
            if (relation != sauces.last()) CulinaryRule()
        }
    }

    Spacer(modifier = Modifier.height(CulinarySpacing.md))
    Text(
        text = "应用于菜谱",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
    if (recipes.isEmpty()) {
        EmptyRelationText("暂未应用于菜谱")
    } else {
        recipes.forEach { relation ->
            ArchiveRelatedRow(
                title = relation.targetLabel,
                subtitle = null,
                relationLabel = relation.relationLabel,
                onClick = { onOpenRelation(relation.targetKind, relation.targetRecordId) },
            )
            if (relation != recipes.last()) CulinaryRule()
        }
    }
}

@Composable
private fun EmptyRelationText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = CulinarySpacing.sm),
    )
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
        conversions.forEachIndexed { index, conversion ->
            ConversionRow(conversion)
            if (index < conversions.lastIndex) CulinaryRule()
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
