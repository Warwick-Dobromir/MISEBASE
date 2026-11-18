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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.culinary.core.designsystem.component.ArchiveDetailBar
import com.culinary.core.designsystem.component.ArchiveDetailFooter
import com.culinary.core.designsystem.component.ArchiveDetailHeading
import com.culinary.core.designsystem.component.ArchiveModuleHeader
import com.culinary.core.designsystem.component.ArchiveProse
import com.culinary.core.designsystem.component.ArchiveRelatedRow
import com.culinary.core.designsystem.component.ArchiveWideImage
import com.culinary.core.designsystem.component.ConfirmDialog
import com.culinary.core.designsystem.component.CulinaryLinkText
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.domain.model.IngredientDetail
import com.culinary.core.domain.model.KitchenRecordKind
import com.culinary.core.domain.model.KitchenRelatedRecord

/** 食材详情：沿用酱汁/菜谱档案详情的统一结构，食材字段独立映射。 */
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
            else -> IngredientDetailContent(
                detailState = state,
                onEdit = onEdit,
                onArchive = { showArchiveConfirm = true },
                onOpenRelation = onOpenRelation,
                onBack = onBack,
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
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CulinarySpacing.pageHorizontal)
                .padding(bottom = CulinarySpacing.xl),
        ) {
            Spacer(modifier = Modifier.height(CulinarySpacing.lg))
            ArchiveDetailHeading(
                categoryLabel = detailState.categoryPath.lastOrNull()
                    ?: ingredient.category?.takeIf { it.isNotBlank() }
                    ?: "INGREDIENT",
                englishTitle = null,
                chineseTitle = ingredient.name,
                description = ingredient.description,
            )
            ingredient.englishName?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier,
                )
                Spacer(modifier = Modifier.height(CulinarySpacing.md))
            }
            detailState.imageBytes?.let { bytes ->
                ArchiveWideImage(bytes = bytes, caption = null)
            }

            ArchiveModuleHeader(number = "01", english = "BASIC INFORMATION", chinese = "基础信息")
            ArchiveField("默认单位", detail.defaultUnit?.let { "${it.name}（${it.symbol}）" } ?: "未设置")
            ArchiveField("学名", ingredient.scientificName)
            Spacer(modifier = Modifier.height(CulinarySpacing.xl))

            ArchiveModuleHeader(number = "02", english = "CHARACTERISTICS", chinese = "风味与特性")
            ArchiveField("风味特征", ingredient.flavorProfile)
            ArchiveField("时令", ingredient.seasonality)
            ArchiveField("产地", ingredient.origin)
            ArchiveField("挑选标准", ingredient.selectionNotes)
            ArchiveField("预处理", ingredient.prepNotes)
            Spacer(modifier = Modifier.height(CulinarySpacing.xl))

            ArchiveModuleHeader(number = "03", english = "CULINARY APPLICATION", chinese = "烹饪应用")
            ArchiveField("常见用途", ingredient.exampleProducts)
            Spacer(modifier = Modifier.height(CulinarySpacing.xl))

            ArchiveModuleHeader(number = "04", english = "STORAGE & SAFETY", chinese = "储存与安全")
            ArchiveField("储存说明", ingredient.storageNotes)
            ArchiveField("过敏原提示", ingredient.allergenNotes)
            Spacer(modifier = Modifier.height(CulinarySpacing.xl))

            ArchiveModuleHeader(number = "05", english = "UNIT CONVERSIONS", chinese = "单位换算")
            if (detail.conversions.isEmpty()) {
                Text(
                    text = "暂无换算关系",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                detail.conversions.forEachIndexed { index, conversion ->
                    val from = conversion.fromUnit?.symbol ?: conversion.fromUnitId.value
                    val to = conversion.toUnit?.symbol ?: conversion.toUnitId.value
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.xxs),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "1 $from  =  ${formatFactor(conversion.factor)} $to",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                        )
                    }
                    conversion.note?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End,
                        )
                    }
                    if (index < detail.conversions.lastIndex) {
                        CulinaryRule()
                    }
                }
            }
            Spacer(modifier = Modifier.height(CulinarySpacing.xl))

            ArchiveModuleHeader(number = "06", english = "RELATED RECORDS", chinese = "关联档案")
            IngredientRelationsSection(detailState.relations, onOpenRelation)
            Spacer(modifier = Modifier.height(CulinarySpacing.lg))

            ArchiveDetailFooter(
                updatedLabel = if (ingredient.isActive) "档案状态 · 正常" else "档案状态 · 已归档",
                versionLabel = null,
            )
            Spacer(modifier = Modifier.height(CulinarySpacing.md))
            CulinaryRule()
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.md),
                horizontalArrangement = Arrangement.Center,
            ) {
                CulinaryLinkText(
                    text = "删除此食材档案",
                    onClick = onArchive,
                )
            }
        }
    }
}

@Composable
private fun ArchiveField(label: String, value: String?) {
    if (value.isNullOrBlank()) {
        Text(
            text = "$label · 暂未填写",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = CulinarySpacing.xs),
        )
    } else {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ArchiveProse(value)
        Spacer(modifier = Modifier.height(CulinarySpacing.sm))
    }
}

@Composable
private fun IngredientRelationsSection(
    relations: List<KitchenRelatedRecord>,
    onOpenRelation: (KitchenRecordKind, String) -> Unit,
) {
    if (relations.isEmpty()) {
        Text(
            text = "暂无关联档案",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        relations.forEach { relation ->
            ArchiveRelatedRow(
                title = relation.targetLabel,
                subtitle = null,
                relationLabel = relation.relationLabel,
                onClick = { onOpenRelation(relation.targetKind, relation.targetRecordId) },
            )
        }
    }
}

private fun formatFactor(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
