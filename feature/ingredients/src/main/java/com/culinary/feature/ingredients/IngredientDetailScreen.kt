package com.culinary.feature.ingredients

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.culinary.core.designsystem.component.ArchiveDetailBar
import com.culinary.core.designsystem.component.ArchiveDetailFooter
import com.culinary.core.designsystem.component.ArchiveDetailHeading
import com.culinary.core.designsystem.component.ArchiveModuleHeader
import com.culinary.core.designsystem.component.ArchiveRelatedRow
import com.culinary.core.designsystem.component.ArchiveWideImage
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.component.formatArchiveTimestamp
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.designsystem.theme.CulinaryTextStyles
import com.culinary.core.domain.model.IngredientConversionView
import com.culinary.core.domain.model.KitchenRecordKind

/** 食材档案详情。仅展示食材数据；编辑仍进入原有食材编辑器。 */
@Composable
fun IngredientDetailRoute(
    onEdit: (String) -> Unit,
    onArchived: () -> Unit,
    onOpenRelation: (KitchenRecordKind, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    backLabel: String = "厨房",
    viewModel: IngredientDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }

    Column(modifier = modifier.fillMaxSize()) {
        state.error?.let { message ->
            ErrorBanner(
                message = message,
                actionLabel = "重试",
                onAction = viewModel::load,
                modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal),
            )
        }
        when {
            state.isLoading -> LoadingRows()
            state.detail == null -> Text(
                text = "食材档案加载失败",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(CulinarySpacing.pageHorizontal),
            )
            else -> IngredientDetailContent(
                detailState = state,
                onBack = onBack,
                backLabel = backLabel,
                onEdit = onEdit,
                onOpenRelation = onOpenRelation,
                onToggleActive = viewModel::toggleActive,
            )
        }
    }
}

@Composable
internal fun IngredientDetailContent(
    detailState: IngredientDetailUiState,
    onBack: () -> Unit,
    backLabel: String = "厨房",
    onEdit: (String) -> Unit,
    onOpenRelation: (KitchenRecordKind, String) -> Unit,
    onToggleActive: () -> Unit,
) {
    val detail = detailState.detail ?: return
    val ingredient = detail.ingredient
    val sauceRelations = detailState.relations.filter { it.targetKind == KitchenRecordKind.SAUCE }
    val recipeRelations = detailState.relations.filter { it.targetKind == KitchenRecordKind.RECIPE }

    Column(modifier = Modifier.fillMaxSize()) {
        ArchiveDetailBar(
            editLabel = "编辑档案 ↗",
            onBack = onBack,
            backLabel = backLabel,
            onEdit = { onEdit(ingredient.id.value) },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CulinarySpacing.pageHorizontal),
        ) {
            Spacer(modifier = Modifier.height(CulinarySpacing.lg))
            ArchiveDetailHeading(
                categoryLabel = detailState.categoryPath.joinToString(" / ")
                    .ifBlank { ingredient.category ?: "未分类" },
                englishTitle = ingredient.englishName,
                chineseTitle = ingredient.name,
                description = ingredient.description,
            )
            detailState.imageBytes?.let { bytes ->
                ArchiveWideImage(bytes = bytes, caption = null)
            }

            ArchiveModuleHeader(number = "01", english = "BASIC INFORMATION", chinese = "基本信息")
            IngredientBasicInformation(
                category = detailState.categoryPath.joinToString(" / ").ifBlank { ingredient.category ?: "未分类" },
                englishName = ingredient.englishName,
                scientificName = ingredient.scientificName,
                exampleProducts = ingredient.exampleProducts,
                edibleParts = ingredient.edibleParts,
                defaultUnit = detail.defaultUnit?.let { "${it.name}（${it.symbol}）" },
            )
            Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleActive).padding(vertical = CulinarySpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Text("档案状态", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(if (ingredient.isActive) "在用 · 点击归档" else "已归档 · 点击恢复在用", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
            }
            CulinaryRule()
            Spacer(modifier = Modifier.height(CulinarySpacing.xl))
            ArchiveModuleHeader(number = "02", english = "FLAVOR PROFILE", chinese = "风味与特性")
            Text(ingredient.flavorProfile?.takeIf { it.isNotBlank() } ?: "尚未填写", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(vertical = CulinarySpacing.sm))
            Spacer(modifier = Modifier.height(CulinarySpacing.xl))
            ArchiveModuleHeader(number = "03", english = "CULINARY USE", chinese = "烹饪用途")
            Text(ingredient.culinaryUse?.takeIf { it.isNotBlank() } ?: "尚未填写", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(vertical = CulinarySpacing.sm))
            Spacer(modifier = Modifier.height(CulinarySpacing.xl))
            ArchiveModuleHeader(number = "04", english = "DETAILS", chinese = "补充信息")
            IngredientFieldSection(fields = listOf("时令" to ingredient.seasonality, "产地" to ingredient.origin, "挑选" to ingredient.selectionNotes, "储存" to ingredient.storageNotes, "预处理" to ingredient.prepNotes, "过敏原提示" to ingredient.allergenNotes))

            if (detail.conversions.isNotEmpty()) {
                IngredientConversions(detail.conversions)
            }

            if (sauceRelations.isNotEmpty() || recipeRelations.isNotEmpty()) {
                ArchiveModuleHeader(number = if (detail.conversions.isNotEmpty()) "06" else "05", english = "RELATED ARCHIVES", chinese = "关联档案")
                if (sauceRelations.isNotEmpty()) {
                    Text(
                        "关联酱汁",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = CulinarySpacing.sm),
                    )
                    sauceRelations.forEach { relation ->
                        ArchiveRelatedRow(
                            title = relation.targetLabel,
                            subtitle = null,
                            relationLabel = relation.relationLabel,
                            onClick = { onOpenRelation(KitchenRecordKind.SAUCE, relation.targetRecordId) },
                        )
                    }
                }
                if (recipeRelations.isNotEmpty()) {
                    Text(
                        "关联菜谱",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = CulinarySpacing.sm),
                    )
                    recipeRelations.forEach { relation ->
                        ArchiveRelatedRow(
                            title = relation.targetLabel,
                            subtitle = null,
                            relationLabel = relation.relationLabel,
                            onClick = { onOpenRelation(KitchenRecordKind.RECIPE, relation.targetRecordId) },
                        )
                    }
                }
            }

            IngredientVersionHistory(detailState.versions)

            val currentVersion = detailState.versions.firstOrNull()
            ArchiveDetailFooter(
                updatedLabel = currentVersion?.createdAt
                    ?.let { formatArchiveTimestamp(it) }
                    ?.let { "最后更新 $it" }
                    ?: "最后更新信息暂不可用",
                versionLabel = currentVersion?.let { "正式版本 v${it.versionNo}" },
            )
        }
    }
}


@Composable
private fun IngredientVersionHistory(versions: List<com.culinary.core.domain.model.IngredientVersion>) {
    if (versions.isEmpty()) return

    val publishedLabel = "已发布"
    Column(modifier = Modifier.fillMaxWidth()) {
        CulinaryRule()
        Column(modifier = Modifier.padding(top = CulinarySpacing.md, bottom = CulinarySpacing.sm)) {
            Text(
                text = "VERSIONS",
                style = CulinaryTextStyles.sectionTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "版本历史",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        versions.sortedByDescending { it.versionNo }.forEachIndexed { index, version ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "v" + version.versionNo.toString().padStart(2, '0'),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(48.dp),
                )
                Text(
                    text = publishedLabel + if (index == 0) " · 当前" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            CulinaryRule()
        }
        Spacer(modifier = Modifier.height(CulinarySpacing.xl))
    }
}

@Composable
private fun IngredientBasicInformation(category: String, englishName: String?, scientificName: String?, exampleProducts: String?, edibleParts: String?, defaultUnit: String?) {
    val rows = listOf("所属分类" to category, "英文名称" to englishName, "学名" to scientificName, "常见品种" to exampleProducts, "常用计量单位" to defaultUnit, "可食用部分" to edibleParts).filter { !it.second.isNullOrBlank() }
    rows.forEach { (label, value) ->
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(value.orEmpty(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
    CulinaryRule()
}

@Composable
private fun IngredientFieldSection(fields: List<Pair<String, String?>>) {
    fields.filter { !it.second.isNullOrBlank() }.forEach { (label, value) ->
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.sm)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = CulinarySpacing.xxs),
            )
        }
        CulinaryRule()
    }
}

@Composable
private fun IngredientConversions(conversions: List<IngredientConversionView>) {
    ArchiveModuleHeader(number = "05", english = "CONVERSIONS", chinese = "换算依据")
    conversions.forEach { conversion ->
        val from = conversion.fromUnit?.symbol ?: conversion.fromUnitId.value
        val to = conversion.toUnit?.symbol ?: conversion.toUnitId.value
        Text(
            text = "${from} → ${to}：${formatFactor(conversion.factor)}" +
                (conversion.note?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = CulinarySpacing.sm),
        )
    }
}

private fun formatFactor(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
