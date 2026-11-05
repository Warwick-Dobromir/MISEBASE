package com.culinary.feature.ingredients

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.culinary.core.designsystem.component.ArchiveListItem
import com.culinary.core.designsystem.component.CulinaryButton
import com.culinary.core.designsystem.component.EmptyState
import com.culinary.core.designsystem.component.ErrorBanner
import com.culinary.core.designsystem.component.LoadingRows
import com.culinary.core.designsystem.component.PageHeader
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.core.designsystem.theme.CulinarySpacing

private const val EYEBROW = "INGREDIENTS"

/** 食材列表路由。 */
@Composable
fun IngredientsRoute(
    onOpenIngredient: (String) -> Unit,
    onAddIngredient: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IngredientsListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    IngredientsScreen(
        state = state,
        onOpenIngredient = onOpenIngredient,
        onAddIngredient = onAddIngredient,
        modifier = modifier,
    )
}

@Composable
internal fun IngredientsScreen(
    state: IngredientsListUiState,
    onOpenIngredient: (String) -> Unit,
    onAddIngredient: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        PageHeader(
            eyebrow = EYEBROW,
            title = stringResource(R.string.ingredients_title),
            supporting = stringResource(R.string.ingredients_count, state.rows.size),
            actions = {
                CulinaryButton(
                    text = stringResource(R.string.ingredients_new),
                    onClick = onAddIngredient,
                    icon = CulinaryIcons.Add,
                )
            },
        )

        state.error?.let { message ->
            ErrorBanner(
                message = message,
                modifier =
                    Modifier.padding(
                        start = CulinarySpacing.pageHorizontal,
                        end = CulinarySpacing.pageHorizontal,
                        bottom = CulinarySpacing.sm,
                    ),
            )
        }

        when {
            state.isLoading -> LoadingRows()
            state.rows.isEmpty() ->
                EmptyState(
                    icon = CulinaryIcons.Ingredient,
                    title = stringResource(R.string.ingredients_empty_title),
                    description = stringResource(R.string.ingredients_empty_body),
                    actionLabel = stringResource(R.string.ingredients_empty_action),
                    onAction = onAddIngredient,
                )

            else ->
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.rows, key = { it.id }) { row ->
                        IngredientListRow(row = row, onClick = { onOpenIngredient(row.id) })
                    }
                }
        }
    }
}

@Composable
private fun IngredientListRow(
    row: IngredientRow,
    onClick: () -> Unit,
) {
    ArchiveListItem(
        title = row.name,
        subtitle = row.category ?: stringResource(R.string.ingredients_uncategorized),
        meta = row.unitLabel ?: stringResource(R.string.ingredients_no_unit),
        onClick = onClick,
    )
}
