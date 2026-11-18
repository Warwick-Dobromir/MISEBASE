package com.culinary.feature.ingredients

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.culinary.core.designsystem.component.ArchiveHeroImage
import com.culinary.core.designsystem.component.ConfirmDialog
import com.culinary.core.designsystem.component.CulinaryButton
import com.culinary.core.designsystem.component.CulinaryButtonVariant
import com.culinary.core.designsystem.component.CulinaryRelationItem
import com.culinary.core.designsystem.component.CulinaryRelationSection
import com.culinary.core.designsystem.component.CulinaryTextButton
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.component.PageHeader
import com.culinary.core.designsystem.component.SectionHeader
import com.culinary.core.designsystem.component.StatusBadge
import com.culinary.core.designsystem.component.StatusTone
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.domain.model.IngredientChangeRecord
import com.culinary.core.domain.model.IngredientConversionView
import com.culinary.core.domain.model.KitchenRecordKind
import com.culinary.core.domain.model.KitchenRelatedRecord

/** 食材详情路由：档案、默认单位、换算；可编辑或归档。 */
@Composable
fun IngredientDetailRoute(
    onEdit: (String) -> Unit,
    onArchived: () -> Unit,
    onBack: () -> Unit = onArchived,
    onOpenRelation: (KitchenRecordKind, String) -> Unit = { _, _ -> },
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

            else ->
                IngredientDetailContent(
                    detailState = state,
                    onEdit = onEdit,
                    onBack = onBack,
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
    onEdit: (String) -> Unit,
    onBack: () -> Unit,
    onArchive: () -> Unit,
    onOpenRelation: (KitchenRecordKind, String) -> Unit,
) {
    val detail = detailState.detail ?: return
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = CulinarySpacing.xl),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = CulinarySpacing.pageHorizontal),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = CulinaryIcons.Back,
                contentDescription = "返回",
                modifier = Modifier.size(36.dp).clickable(onClick = onBack).padding(8.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "编辑档案",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clickable { onEdit(detail.ingredient.id.value) }.padding(8.dp),
            )
        }
        com.culinary.core.designsystem.component.CulinaryRule()
        PageHeader(
            eyebrow = "INGREDIENT ARCHIVE",
            title = detail.ingredient.name,
            supporting =
                detailState.categoryPath.joinToString(" / ").ifBlank {
                    detail.ingredient.category ?: stringResource(R.string.ingredients_uncategorized)
                },
        )
        detailState.imageBytes?.let { bytes ->
            ArchiveHeroImage(
                bytes = bytes,
                modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal),
            )
        }
        if (detailState.referenceImageBytes.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState())
                    .padding(horizontal = CulinarySpacing.pageHorizontal),
                horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.sm),
            ) {
                detailState.referenceImageBytes.forEach { bytes ->
                    val bitmap = remember(bytes) {
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    }
                    bitmap?.let {
                        Image(
                            bitmap = it,
                            contentDescription = "食材补充图片",
                            modifier = Modifier.size(88.dp),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }
        }
        Column(modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal)) {
            Row(horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.xs)) {
                if (detail.ingredient.isSeed) {
                    StatusBadge(
                        text = stringResource(R.string.ingredients_seed_badge),
                        tone = StatusTone.Accent,
                    )
                }
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
        }

        IngredientHeaderMeta(detail.ingredient)
        IngredientSummary(detail = detail, onArchive = onArchive)
        IngredientRelationsSection(detailState.relations, onOpenRelation)
        IngredientChangeHistory(detail.changeHistory)
    }
}

@Composable
private fun IngredientSummary(
    detail: com.culinary.core.domain.model.IngredientDetail,
    onArchive: () -> Unit,
) {
    val ingredient = detail.ingredient
    Column(modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal)) {
        SectionHeader(title = "01  基本信息")
        IngredientInfoLine("所属分类", ingredient.category)
        IngredientInfoLine("英文名称", ingredient.englishName)
        IngredientInfoLine("学名", ingredient.scientificName)
        IngredientInfoLine(
            "常用计量单位",
            detail.defaultUnit?.let { "${it.name}（${it.symbol}）" } ?: "未设置",
        )
        ConversionsSection(conversions = detail.conversions)

        if (!ingredient.flavorProfile.isNullOrBlank() || !ingredient.description.isNullOrBlank()) {
            SectionHeader(title = "02  风味与特性")
            IngredientParagraph(ingredient.flavorProfile)
            IngredientParagraph(ingredient.description)
        }

        if (!ingredient.exampleProducts.isNullOrBlank()) {
            SectionHeader(title = "03  烹饪用途")
            IngredientParagraph(ingredient.exampleProducts)
        }

        val otherFields = listOfNotNull(
            "产地" to ingredient.origin,
            "季节性 / 时令" to ingredient.seasonality,
            "挑选建议" to ingredient.selectionNotes,
            "预处理方法" to ingredient.prepNotes,
            "储存方式" to ingredient.storageNotes,
            "过敏原信息" to ingredient.allergenNotes,
            "档案备注" to ingredient.seedNote,
        ).filter { !it.second.isNullOrBlank() }
        if (otherFields.isNotEmpty()) {
            SectionHeader(title = "04  其他信息")
            otherFields.forEach { (label, value) -> IngredientInfoLine(label, value) }
        }

        Spacer(modifier = Modifier.height(CulinarySpacing.block))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.sm),
        ) {

            CulinaryTextButton(
                text = stringResource(R.string.ingredients_archive),
                onClick = onArchive,
                danger = true,
            )
        }
    }
}

@Composable
private fun IngredientInfoLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = CulinarySpacing.xs),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun IngredientParagraph(value: String?) {
    if (value.isNullOrBlank()) return
    Text(
        text = value,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = CulinarySpacing.sm),
    )
}

@Composable
private fun IngredientHeaderMeta(ingredient: com.culinary.core.domain.model.Ingredient) {
    val lines = listOfNotNull(
        ingredient.englishName?.takeIf { it.isNotBlank() },
        ingredient.scientificName?.takeIf { it.isNotBlank() },
        "档案编号  ${ingredient.id.value}",
    )
    if (lines.isEmpty()) return
    Column(modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal)) {
        lines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
    if (sauces.isNotEmpty() || recipes.isNotEmpty()) {
        SectionHeader(title = "05  应用关系")
    }
    if (sauces.isNotEmpty()) {
        CulinaryRelationSection(
            title = "酱汁",
            items = sauces.map { relation ->
                CulinaryRelationItem(
                    eyebrow = relation.relationLabel,
                    title = relation.targetLabel,
                    onClick = { onOpenRelation(relation.targetKind, relation.targetRecordId) },
                )
            },
        )
    }
    if (recipes.isNotEmpty()) {
        CulinaryRelationSection(
            title = "菜谱",
            items = recipes.map { relation ->
                CulinaryRelationItem(
                    eyebrow = relation.relationLabel,
                    title = relation.targetLabel,
                    onClick = { onOpenRelation(relation.targetKind, relation.targetRecordId) },
                )
            },
        )
    }
}

@Composable
private fun IngredientChangeHistory(records: List<IngredientChangeRecord>) {
    if (records.isEmpty()) return
    Column(modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal)) {
        SectionHeader(title = "06  版本历史")
        records.forEachIndexed { index, record ->
            val current = index == records.lastIndex
            val timestamp =
                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(record.createdAt))
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "v" + (index + 1) + "  " + if (current) "当前记录" else "历史记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = (if (record.action == "CREATE") "创建档案" else "修改档案") + " · " + timestamp,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (current) StatusBadge(text = "当前", tone = StatusTone.Accent)
            }
        }
    }
}

@Composable
private fun ConversionsSection(conversions: List<IngredientConversionView>) {
    Column(modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal)) {
        SectionHeader(title = "单位换算关系")
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
