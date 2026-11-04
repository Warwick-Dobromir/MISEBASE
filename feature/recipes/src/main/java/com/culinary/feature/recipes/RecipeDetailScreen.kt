package com.culinary.feature.recipes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.component.QuantityText
import com.culinary.core.designsystem.component.SectionHeader
import com.culinary.core.designsystem.component.StatusBadge
import com.culinary.core.designsystem.component.StatusTone
import com.culinary.core.designsystem.theme.CulinarySizes
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.domain.model.ExpandedRecipe
import com.culinary.core.domain.model.RecipeComponentKind
import com.culinary.core.domain.model.RecipeComponentView
import com.culinary.core.domain.model.RecipeDetail
import com.culinary.core.domain.model.RecipeVersion
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.model.VersionStatus

/** 菜谱详情路由：档案、组成、按份数展开、版本历史与发布。 */
@Composable
fun RecipeDetailRoute(
    onEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecipeDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    RecipeDetailScreen(
        state = state,
        onEdit = onEdit,
        onPublish = viewModel::publish,
        onServingsChange = viewModel::onTargetServingsChange,
        onExpand = viewModel::expand,
        onRetry = viewModel::load,
        modifier = modifier,
    )
}

@Composable
private fun RecipeDetailScreen(
    state: RecipeDetailUiState,
    onEdit: (String) -> Unit,
    onPublish: (RecipeVersionId) -> Unit,
    onServingsChange: (String) -> Unit,
    onExpand: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        state.error?.let { message ->
            ErrorBanner(
                message = message,
                actionLabel = stringResource(R.string.recipes_retry),
                onAction = onRetry,
            )
        }
        when {
            state.isLoading -> LoadingRows()
            state.detail == null ->
                Text(
                    text = stringResource(R.string.recipes_load_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(CulinarySpacing.md),
                )

            else ->
                RecipeDetailContent(
                    detail = state.detail,
                    state = state,
                    onEdit = onEdit,
                    onPublish = onPublish,
                    onServingsChange = onServingsChange,
                    onExpand = onExpand,
                )
        }
    }
}

@Composable
internal fun RecipeDetailContent(
    detail: RecipeDetail,
    state: RecipeDetailUiState,
    onEdit: (String) -> Unit,
    onPublish: (RecipeVersionId) -> Unit,
    onServingsChange: (String) -> Unit,
    onExpand: () -> Unit,
) {
    val latestDraft = detail.versions.firstOrNull { it.status == VersionStatus.DRAFT }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = CulinarySpacing.md,
                end = CulinarySpacing.md,
                bottom = CulinarySizes.listBottomInset,
            ),
    ) {
        item { RecipeHeader(detail) }
        item { RecipeCurrentVersion(detail) }
        item {
            RecipeActions(
                detail = detail,
                latestDraft = latestDraft,
                isPublishing = state.isPublishing,
                onEdit = onEdit,
                onPublish = onPublish,
            )
        }
        item { RecipeComponents(detail.components) }
        item {
            RecipeExpansion(
                state = state,
                onServingsChange = onServingsChange,
                onExpand = onExpand,
            )
        }
        item { SectionHeader(title = stringResource(R.string.recipes_versions)) }
        items(detail.versions, key = { it.id.value }) { version -> RecipeVersionRow(version) }
    }
}

@Composable
private fun RecipeHeader(detail: RecipeDetail) {
    Text(
        text = detail.recipe.name,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = CulinarySpacing.sm),
    )
    Row(
        modifier = Modifier.padding(top = CulinarySpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusBadge(
            text = recipeCategoryLabel(detail.recipe.category),
            tone = StatusTone.Accent,
        )
        Text(
            text =
                stringResource(
                    if (detail.recipe.status == com.culinary.core.domain.model.EntityStatus.ACTIVE) {
                        R.string.recipes_active
                    } else {
                        R.string.recipes_archived
                    },
                ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecipeCurrentVersion(detail: RecipeDetail) {
    SectionHeader(title = stringResource(R.string.recipes_current_version))
    val current = detail.currentVersion
    if (current == null) {
        Text(
            text = stringResource(R.string.recipes_no_version),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "v${current.versionNo}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            StatusBadge(
                text = stringResource(R.string.recipes_status_published),
                tone = StatusTone.Success,
                modifier = Modifier.padding(start = CulinarySpacing.xs),
            )
            Spacer(modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                QuantityText(
                    amount = formatRecipeAmount(current.yield.amount),
                    unit = current.yield.unit.symbol,
                )
                Text(
                    text = stringResource(R.string.recipes_base_servings) + " ${current.servings}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RecipeActions(
    detail: RecipeDetail,
    latestDraft: RecipeVersion?,
    isPublishing: Boolean,
    onEdit: (String) -> Unit,
    onPublish: (RecipeVersionId) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = CulinarySpacing.md),
        horizontalArrangement = Arrangement.spacedBy(CulinarySpacing.sm),
    ) {
        OutlinedButton(onClick = { onEdit(detail.recipe.id.value) }) {
            Text(text = stringResource(R.string.recipes_edit))
        }
        if (latestDraft != null) {
            Button(onClick = { onPublish(latestDraft.id) }, enabled = !isPublishing) {
                Text(
                    text =
                        if (isPublishing) {
                            stringResource(R.string.recipes_publishing)
                        } else {
                            stringResource(R.string.recipes_publish, latestDraft.versionNo)
                        },
                )
            }
        }
    }
}

@Composable
private fun RecipeComponents(components: List<RecipeComponentView>) {
    SectionHeader(title = stringResource(R.string.recipes_components, components.size))
    if (components.isEmpty()) {
        Text(
            text = stringResource(R.string.recipes_no_components),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        components.forEach { component -> RecipeComponentRow(component) }
    }
}

@Composable
private fun RecipeComponentRow(component: RecipeComponentView) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = component.label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = componentKindLabel(component),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (component.isOptional) {
            StatusBadge(
                text = stringResource(R.string.recipes_optional),
                tone = StatusTone.Neutral,
                modifier = Modifier.padding(end = CulinarySpacing.xs),
            )
        }
        QuantityText(
            amount = formatRecipeAmount(component.quantity.amount),
            unit = component.quantity.unit.symbol,
        )
    }
}

@Composable
private fun componentKindLabel(component: RecipeComponentView): String =
    when (component.kind) {
        RecipeComponentKind.INGREDIENT -> stringResource(R.string.recipes_kind_ingredient)
        RecipeComponentKind.RECIPE ->
            stringResource(R.string.recipes_kind_recipe, component.referencedVersionNo ?: 0)
        RecipeComponentKind.SAUCE ->
            stringResource(R.string.recipes_kind_sauce, component.referencedVersionNo ?: 0)
    }

@Composable
private fun RecipeExpansion(
    state: RecipeDetailUiState,
    onServingsChange: (String) -> Unit,
    onExpand: () -> Unit,
) {
    SectionHeader(title = stringResource(R.string.recipes_expand))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = state.targetServings,
            onValueChange = onServingsChange,
            label = { Text(text = stringResource(R.string.recipes_servings_label)) },
            singleLine = true,
            keyboardOptions =
                androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                ),
            modifier = Modifier.width(132.dp),
        )
        Spacer(modifier = Modifier.width(CulinarySpacing.xs))
        Button(onClick = onExpand, enabled = !state.isExpanding) {
            Text(
                text =
                    if (state.isExpanding) {
                        stringResource(R.string.recipes_expanding)
                    } else {
                        stringResource(R.string.recipes_expand)
                    },
            )
        }
    }
    state.expansionError?.let { message ->
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = CulinarySpacing.xs),
        )
    }
    state.expanded?.let { expanded -> ExpandedLines(expanded) }
}

@Composable
private fun ExpandedLines(expanded: ExpandedRecipe) {
    Text(
        text =
            stringResource(
                R.string.recipes_expanded,
                expanded.targetServings.toString(),
                expanded.baseServings.toString(),
            ),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = CulinarySpacing.sm),
    )
    if (expanded.lines.isEmpty()) {
        Text(
            text = stringResource(R.string.recipes_expansion_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        expanded.lines.forEach { line ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = line.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (line.isOptional) {
                    StatusBadge(
                        text = stringResource(R.string.recipes_optional),
                        tone = StatusTone.Neutral,
                        modifier = Modifier.padding(end = CulinarySpacing.xs),
                    )
                }
                QuantityText(
                    amount = formatRecipeAmount(line.quantity.amount),
                    unit = line.quantity.unit.symbol,
                )
            }
        }
    }
    Text(
        text = stringResource(R.string.recipes_expansion_hint),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = CulinarySpacing.xs),
    )
}

@Composable
private fun RecipeVersionRow(version: RecipeVersion) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = CulinarySpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "v${version.versionNo}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        StatusBadge(
            text = recipeVersionStatusLabel(version.status),
            tone = recipeVersionStatusTone(version.status),
            modifier = Modifier.padding(start = CulinarySpacing.xs),
        )
        if (version.isCurrent) {
            StatusBadge(
                text = stringResource(R.string.recipes_current_badge),
                tone = StatusTone.Accent,
                modifier = Modifier.padding(start = CulinarySpacing.xs),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        QuantityText(
            amount = formatRecipeAmount(version.yield.amount),
            unit = version.yield.unit.symbol,
        )
    }
}

@Composable
private fun recipeVersionStatusLabel(status: VersionStatus): String =
    stringResource(
        when (status) {
            VersionStatus.DRAFT -> R.string.recipes_status_draft
            VersionStatus.PUBLISHED -> R.string.recipes_status_published
            VersionStatus.SUPERSEDED -> R.string.recipes_status_superseded
        },
    )

private fun recipeVersionStatusTone(status: VersionStatus): StatusTone =
    when (status) {
        VersionStatus.DRAFT -> StatusTone.Warning
        VersionStatus.PUBLISHED -> StatusTone.Success
        VersionStatus.SUPERSEDED -> StatusTone.Neutral
    }

private fun formatRecipeAmount(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
