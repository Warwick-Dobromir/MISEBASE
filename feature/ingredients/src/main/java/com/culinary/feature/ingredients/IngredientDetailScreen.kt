package com.culinary.feature.ingredients

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
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
import com.culinary.core.designsystem.component.ArchiveDetailHeading
import com.culinary.core.designsystem.component.ArchiveHeroImage
import com.culinary.core.designsystem.component.ArchiveModuleHeader
import com.culinary.core.designsystem.component.ArchiveRelatedRow
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.component.SectionHeader
import com.culinary.core.designsystem.component.StatusBadge
import com.culinary.core.designsystem.component.StatusTone
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.domain.model.KitchenRecordKind

/** 食材详情：只读展示食材档案，并提供编辑入口与关联档案直达。 */
@Composable
fun IngredientDetailRoute(
    onEdit: (String) -> Unit,
    onArchived: () -> Unit,
    onBack: () -> Unit = {},
    onOpenRelation: (KitchenRecordKind, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    viewModel: IngredientDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
                    onOpenRelation = onOpenRelation,
                )
        }
    }
}

@Composable
internal fun IngredientDetailContent(
    detailState: IngredientDetailUiState,
    onEdit: (String) -> Unit,
    onBack: () -> Unit,
    onOpenRelation: (KitchenRecordKind, String) -> Unit,
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
                categoryLabel = detailState.categoryPath.joinToString(" / ").ifBlank {
                    ingredient.category ?: stringResource(R.string.ingredients_uncategorized)
                },
                englishTitle = ingredient.englishName,
                chineseTitle = ingredient.name,
                description = ingredient.description,
            )
            detailState.imageBytes?.let { bytes ->
                ArchiveHeroImage(bytes = bytes)
            }
            Spacer(modifier = Modifier.height(CulinarySpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.xs)) {
                if (ingredient.isSeed) {
                    StatusBadge(
                        text = stringResource(R.string.ingredients_seed_badge),
                        tone = StatusTone.Accent,
                    )
                }
                StatusBadge(
                    text = stringResource(
                        if (ingredient.isActive) {
                            R.string.ingredients_active
                        } else {
                            R.string.ingredients_archived
                        },
                    ),
                    tone = if (ingredient.isActive) StatusTone.Success else StatusTone.Neutral,
                )
            }

            Spacer(modifier = Modifier.height(CulinarySpacing.xl))
            IngredientInfoField("学名", ingredient.scientificName)
            IngredientInfoField("常见品种", null)
            IngredientInfoField(
                "常用计量单位",
                detail.defaultUnit?.let { "${it.name}（${it.symbol}）" },
            )
            IngredientInfoField("可食用部分", null)
            IngredientInfoField("风味与特性", ingredient.flavorProfile)
            IngredientInfoField("烹饪用途", null)

            Spacer(modifier = Modifier.height(CulinarySpacing.xl))
            ArchiveModuleHeader(
                number = "01",
                english = "RELATED RECORDS",
                chinese = "关联档案",
            )
            val relatedRecords = detailState.relations.filter {
                it.targetKind == KitchenRecordKind.SAUCE || it.targetKind == KitchenRecordKind.RECIPE
            }
            if (relatedRecords.isEmpty()) {
                Text(
                    text = "暂未添加关联档案",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                relatedRecords.forEach { relation ->
                    ArchiveRelatedRow(
                        title = relation.targetLabel,
                        subtitle = null,
                        relationLabel = relation.relationLabel,
                        onClick = { onOpenRelation(relation.targetKind, relation.targetRecordId) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(CulinarySpacing.xl))
            CulinaryRule()
            Spacer(modifier = Modifier.height(CulinarySpacing.xl))
        }
    }
}

@Composable
private fun IngredientInfoField(
    label: String,
    value: String?,
) {
    SectionHeader(title = label)
    Text(
        text = value?.takeIf { it.isNotBlank() } ?: "暂无记录",
        style = MaterialTheme.typography.bodyLarge,
        color = if (value.isNullOrBlank()) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    )
    CulinaryRule()
}
