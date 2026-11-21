package com.culinary.feature.ingredients

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.culinary.core.designsystem.component.ArchiveDetailBar
import com.culinary.core.designsystem.component.ArchiveDetailHeading
import com.culinary.core.designsystem.component.ArchiveMetaGrid
import com.culinary.core.designsystem.component.ArchiveMetaItem
import com.culinary.core.designsystem.component.ArchiveNoteImages
import com.culinary.core.designsystem.component.ArchiveRelatedRow
import com.culinary.core.designsystem.component.ArchiveVersionHistory
import com.culinary.core.designsystem.component.ArchiveVersionItem
import com.culinary.core.designsystem.component.ArchiveWideImage
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.theme.CulinarySpacing
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
                onEdit = onEdit,
                onOpenRelation = onOpenRelation,
            )
        }
    }
}

@Composable
internal fun IngredientDetailContent(
    detailState: IngredientDetailUiState,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onOpenRelation: (KitchenRecordKind, String) -> Unit,
) {
    val detail = detailState.detail ?: return
    val ingredient = detail.ingredient
    val sauceRelations = detailState.relations.filter { it.targetKind == KitchenRecordKind.SAUCE }
    val recipeRelations = detailState.relations.filter { it.targetKind == KitchenRecordKind.RECIPE }

    Column(modifier = Modifier.fillMaxSize()) {
        ArchiveDetailBar(
            editLabel = "编辑档案",
            onBack = onBack,
            onEdit = { onEdit(ingredient.id.value) },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CulinarySpacing.pageHorizontal),
        ) {
            Text(
                text = detailState.categoryPath.joinToString(" / ")
                    .ifBlank { ingredient.category ?: "未分类" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = CulinarySpacing.md, bottom = CulinarySpacing.md),
            )
            ArchiveDetailHeading(
                categoryLabel = ingredient.category,
                englishTitle = ingredient.englishName,
                chineseTitle = ingredient.name,
                description = ingredient.description,
            )
            detailState.imageBytes?.let { bytes ->
                ArchiveWideImage(bytes = bytes, caption = null)
            }

            ArchiveMetaGrid(
                items = listOf(
                    ArchiveMetaItem("STATUS", if (ingredient.isActive) "在用" else "已归档"),
                    ArchiveMetaItem("DEFAULT UNIT", detail.defaultUnit?.let { "${it.name}（${it.symbol}）" }.orEmpty()),
                ),
            )

            IngredientFieldSection(
                fields = listOf(
                    "学名" to ingredient.scientificName,
                    "风味" to ingredient.flavorProfile,
                    "时令" to ingredient.seasonality,
                    "产地" to ingredient.origin,
                    "挑选" to ingredient.selectionNotes,
                    "储存" to ingredient.storageNotes,
                    "预处理" to ingredient.prepNotes,
                    "示例商品 / 规格" to ingredient.exampleProducts,
                    "过敏原提示" to ingredient.allergenNotes,
                ),
            )

            if (detail.conversions.isNotEmpty()) {
                IngredientConversions(detail.conversions)
            }

            if (sauceRelations.isNotEmpty() || recipeRelations.isNotEmpty()) {
                IngredientSectionHeading("RELATED ARCHIVES", "关联档案")
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

            // 食材版本数据尚未建立前不显示虚构版本；真实版本能力在独立迁移中接入。
            Spacer(Modifier.height(CulinarySpacing.xl))
            CulinaryRule()
            Text(
                "MISEBASE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = CulinarySpacing.md, bottom = CulinarySpacing.xl),
            )
        }
    }
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
private fun IngredientSectionHeading(english: String, chinese: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.lg, bottom = CulinarySpacing.sm)) {
        CulinaryRule()
        Text(
            english,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(top = CulinarySpacing.md),
        )
        Text(
            chinese,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun IngredientConversions(conversions: List<IngredientConversionView>) {
    IngredientSectionHeading("CONVERSIONS", "换算依据")
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
