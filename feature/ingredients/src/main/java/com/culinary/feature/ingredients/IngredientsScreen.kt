package com.culinary.feature.ingredients

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.culinary.core.domain.model.Ingredient

/**
 * 食材列表路由：列表 + 新建/编辑对话框 + 归档。
 *
 * 只渲染状态、转发事件；校验与持久化在 Use Case/Repository。
 */
@Composable
fun IngredientsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IngredientsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var dialogIngredient by remember { mutableStateOf<Ingredient?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    IngredientsScaffold(
        state = state,
        onBack = onBack,
        onAdd = {
            dialogIngredient = null
            showDialog = true
        },
        onEdit = { ingredient ->
            dialogIngredient = ingredient
            showDialog = true
        },
        onArchive = { ingredient -> viewModel.archive(ingredient.id) },
        onDismissError = viewModel::clearError,
        modifier = modifier,
    )

    if (showDialog) {
        SaveIngredientDialog(
            initialName = dialogIngredient?.name.orEmpty(),
            isEditing = dialogIngredient != null,
            isSaving = state.isSaving,
            onDismiss = { showDialog = false },
            onSave = { name ->
                viewModel.save(name = name, id = dialogIngredient?.id)
                showDialog = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IngredientsScaffold(
    state: IngredientsUiState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Ingredient) -> Unit,
    onArchive: (Ingredient) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.ingredients_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(text = stringResource(R.string.ingredients_back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Text(text = stringResource(R.string.ingredients_add))
            }
        },
    ) { padding ->
        IngredientsBody(
            state = state,
            onEdit = onEdit,
            onArchive = onArchive,
            onDismissError = onDismissError,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        )
    }
}

@Composable
private fun IngredientsBody(
    state: IngredientsUiState,
    onEdit: (Ingredient) -> Unit,
    onArchive: (Ingredient) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        state.error?.let { message ->
            ErrorRow(message = message, onDismiss = onDismissError)
        }
        when {
            state.isLoading -> LoadingState()
            state.items.isEmpty() -> EmptyState()
            else -> IngredientList(items = state.items, onEdit = onEdit, onArchive = onArchive)
        }
    }
}

@Composable
private fun ErrorRow(
    message: String,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDismiss) {
            Text(text = stringResource(R.string.ingredients_error_dismiss))
        }
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.ingredients_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IngredientList(
    items: List<Ingredient>,
    onEdit: (Ingredient) -> Unit,
    onArchive: (Ingredient) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items, key = { it.id.value }) { ingredient ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onEdit(ingredient) }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = ingredient.name,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                )
                TextButton(onClick = { onArchive(ingredient) }) {
                    Text(text = stringResource(R.string.ingredients_archive))
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun SaveIngredientDialog(
    initialName: String,
    isEditing: Boolean,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text =
                    stringResource(
                        if (isEditing) R.string.ingredients_edit else R.string.ingredients_add,
                    ),
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(text = stringResource(R.string.ingredients_name_label)) },
                singleLine = true,
                enabled = !isSaving,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name) },
                enabled = name.isNotBlank() && !isSaving,
            ) {
                Text(text = stringResource(R.string.ingredients_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.ingredients_cancel))
            }
        },
    )
}
