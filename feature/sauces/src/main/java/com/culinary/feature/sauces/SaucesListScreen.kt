package com.culinary.feature.sauces

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
import com.culinary.core.domain.model.Sauce

/** 酱汁列表路由：只渲染状态并转发事件。 */
@Composable
fun SaucesRoute(
    onOpenSauce: (String) -> Unit,
    onAddSauce: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SaucesListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    SaucesScreen(
        state = state,
        onOpenSauce = onOpenSauce,
        onAddSauce = onAddSauce,
        modifier = modifier,
    )
}

@Composable
private fun SaucesScreen(
    state: SaucesListUiState,
    onOpenSauce: (String) -> Unit,
    onAddSauce: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = CulinarySpacing.md,
                        vertical = CulinarySpacing.sm,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.sauces_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.sauces_count, state.items.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onAddSauce) {
                Text(text = stringResource(R.string.sauces_new))
            }
        }

        state.error?.let { message ->
            ErrorBanner(
                message = message,
                actionLabel = null,
                onAction = null,
                modifier = Modifier.padding(bottom = CulinarySpacing.xs),
            )
        }

        when {
            state.isLoading -> LoadingRows()
            state.items.isEmpty() ->
                EmptyState(
                    title = stringResource(R.string.sauces_empty_title),
                    description = stringResource(R.string.sauces_empty_body),
                    actionLabel = stringResource(R.string.sauces_empty_action),
                    onAction = onAddSauce,
                )

            else ->
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.items, key = { it.id.value }) { sauce ->
                        SauceRow(sauce = sauce, onClick = { onOpenSauce(sauce.id.value) })
                    }
                }
        }
    }
}

@Composable
private fun SauceRow(
    sauce: Sauce,
    onClick: () -> Unit,
) {
    ArchiveListItem(
        title = sauce.name,
        subtitle = sauce.family ?: stringResource(R.string.sauces_uncategorized),
        meta =
            stringResource(
                if (sauce.isActive) R.string.sauces_active else R.string.sauces_archived,
            ),
        onClick = onClick,
    )
}
