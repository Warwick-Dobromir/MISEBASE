package com.culinary.feature.sauces

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
import com.culinary.core.domain.model.Sauce

private const val EYEBROW = "SAUCES"

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
internal fun SaucesScreen(
    state: SaucesListUiState,
    onOpenSauce: (String) -> Unit,
    onAddSauce: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        PageHeader(
            eyebrow = EYEBROW,
            title = stringResource(R.string.sauces_title),
            supporting = stringResource(R.string.sauces_count, state.items.size),
            actions = {
                CulinaryButton(
                    text = stringResource(R.string.sauces_new),
                    onClick = onAddSauce,
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
            state.items.isEmpty() ->
                EmptyState(
                    icon = CulinaryIcons.Sauce,
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
