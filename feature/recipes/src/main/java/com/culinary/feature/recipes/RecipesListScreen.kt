package com.culinary.feature.recipes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.culinary.core.designsystem.component.ArchiveListItem
import com.culinary.core.designsystem.component.EmptyState
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.theme.CulinarySpacing

/** 菜谱列表路由。 */
@Composable
fun RecipesRoute(
    onOpenRecipe: (String) -> Unit,
    onAddRecipe: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecipesListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    RecipesScreen(
        state = state,
        onOpenRecipe = onOpenRecipe,
        onAddRecipe = onAddRecipe,
        modifier = modifier,
    )
}

@Composable
private fun RecipesScreen(
    state: RecipesListUiState,
    onOpenRecipe: (String) -> Unit,
    onAddRecipe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CulinarySpacing.md, vertical = CulinarySpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.recipes_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.recipes_count, state.rows.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onAddRecipe) {
                Text(text = stringResource(R.string.recipes_new))
            }
        }

        state.error?.let { message -> ErrorBanner(message = message) }

        when {
            state.isLoading -> LoadingRows()
            state.rows.isEmpty() ->
                EmptyState(
                    title = stringResource(R.string.recipes_empty_title),
                    description = stringResource(R.string.recipes_empty_body),
                    actionLabel = stringResource(R.string.recipes_empty_action),
                    onAction = onAddRecipe,
                )

            else ->
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.rows, key = { it.id }) { row ->
                        RecipeListRow(row = row, onClick = { onOpenRecipe(row.id) })
                    }
                }
        }
    }
}

@Composable
private fun RecipeListRow(
    row: RecipeRow,
    onClick: () -> Unit,
) {
    ArchiveListItem(
        title = row.name,
        subtitle = recipeCategoryLabel(row.category),
        meta =
            stringResource(
                if (row.isActive) R.string.recipes_active else R.string.recipes_archived,
            ),
        onClick = onClick,
    )
}
