package com.culinary.kitchen.archive

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.culinary.core.common.CulinaryError
import com.culinary.core.common.Outcome
import com.culinary.core.data.kitchen.KitchenReferenceContentRecord
import com.culinary.core.data.kitchen.KitchenReferenceStore
import com.culinary.core.data.kitchen.KitchenReferenceType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class KitchenReferenceError {
    NAME_REQUIRED,
    DUPLICATE,
    NOT_FOUND,
    GENERIC,
}

data class EditableReferenceItem(
    val id: String,
    val section: String,
    val title: String = "",
    val body: String = "",
)

data class KitchenReferenceEditorUiState(
    val type: KitchenReferenceType = KitchenReferenceType.TECHNIQUE,
    val id: String? = null,
    val name: String = "",
    val category: String = "",
    val description: String = "",
    val fields: Map<String, String> = emptyMap(),
    val items: List<EditableReferenceItem> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: KitchenReferenceError? = null,
    val saved: Boolean = false,
) {
    val isNew: Boolean get() = id == null
}

/** 新增三类参考资料的编辑状态；不接触既有酱汁/食材/菜谱模型。 */
@HiltViewModel
class KitchenReferenceEditorViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val store: KitchenReferenceStore,
    ) : ViewModel() {
        private val type =
            kitchenReferenceTypeFromRoute(savedStateHandle.get<String>(KITCHEN_REFERENCE_KIND_ARG))
                ?: KitchenReferenceType.TECHNIQUE
        private val referenceId = savedStateHandle.get<String>(KITCHEN_REFERENCE_ID_ARG)

        private val _uiState =
            MutableStateFlow(
                KitchenReferenceEditorUiState(
                    type = type,
                    id = referenceId,
                    isLoading = referenceId != null,
                ),
            )
        val uiState: StateFlow<KitchenReferenceEditorUiState> = _uiState.asStateFlow()

        init {
            if (referenceId == null) {
                _uiState.update { it.copy(isLoading = false) }
            } else {
                load(referenceId)
            }
        }

        fun onNameChange(value: String) {
            _uiState.update { it.copy(name = value, error = null) }
        }

        fun onCategoryChange(value: String) {
            _uiState.update { it.copy(category = value, error = null) }
        }

        fun onDescriptionChange(value: String) {
            _uiState.update { it.copy(description = value, error = null) }
        }

        fun onFieldChange(
            key: String,
            value: String,
        ) {
            _uiState.update { it.copy(fields = it.fields + (key to value), error = null) }
        }

        fun onItemTitleChange(
            id: String,
            value: String,
        ) {
            _uiState.update { state ->
                state.copy(
                    items = state.items.map { if (it.id == id) it.copy(title = value) else it },
                    error = null,
                )
            }
        }

        fun onItemBodyChange(
            id: String,
            value: String,
        ) {
            _uiState.update { state ->
                state.copy(
                    items = state.items.map { if (it.id == id) it.copy(body = value) else it },
                    error = null,
                )
            }
        }

        fun addItem(section: String) {
            _uiState.update { state ->
                val id = "new-$section-${state.items.size}-${System.nanoTime()}"
                state.copy(items = state.items + EditableReferenceItem(id = id, section = section))
            }
        }

        fun removeItem(id: String) {
            _uiState.update { state -> state.copy(items = state.items.filterNot { it.id == id }) }
        }

        fun moveItem(
            id: String,
            delta: Int,
        ) {
            _uiState.update { state ->
                val index = state.items.indexOfFirst { it.id == id }
                val target = index + delta
                if (index < 0 || target !in state.items.indices) {
                    state
                } else {
                    val mutable = state.items.toMutableList()
                    val item = mutable.removeAt(index)
                    mutable.add(target, item)
                    state.copy(items = mutable)
                }
            }
        }

        fun save() {
            val current = _uiState.value
            if (current.name.isBlank()) {
                _uiState.update { it.copy(error = KitchenReferenceError.NAME_REQUIRED) }
                return
            }
            if (current.isSaving) return
            _uiState.update { it.copy(isSaving = true, error = null) }
            viewModelScope.launch {
                when (
                    val result =
                        store.save(
                            type = current.type,
                            id = current.id,
                            name = current.name,
                            category = current.category,
                            description = current.description,
                            content = buildContent(current),
                        )
                ) {
                    is Outcome.Success -> _uiState.update { it.copy(isSaving = false, saved = true) }
                    is Outcome.Failure ->
                        _uiState.update {
                            it.copy(isSaving = false, error = result.error.toEditorError())
                        }
                }
            }
        }

        private fun load(id: String) {
            viewModelScope.launch {
                when (val result = store.get(id)) {
                    is Outcome.Success -> {
                        val loaded = result.value
                        val schema = KitchenReferenceSchema.of(loaded.type)
                        val listKeys = schema.lists.map { it.key }.toSet()
                        val fields =
                            loaded.content
                                .filter { it.section !in listKeys }
                                .associate { it.section to it.value }
                        val items =
                            loaded.content
                                .filter { it.section in listKeys }
                                .sortedBy { it.sortOrder }
                                .mapIndexed { index, item ->
                                    EditableReferenceItem(
                                        id = "${item.section}-$index-${item.sortOrder}",
                                        section = item.section,
                                        title = item.label.orEmpty(),
                                        body = item.value,
                                    )
                                }
                        _uiState.update {
                            it.copy(
                                type = loaded.type,
                                name = loaded.name,
                                category = loaded.category.orEmpty(),
                                description = loaded.description.orEmpty(),
                                fields = fields,
                                items = items,
                                isLoading = false,
                            )
                        }
                    }

                    is Outcome.Failure ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error =
                                    if (result.error is CulinaryError.NotFound) {
                                        KitchenReferenceError.NOT_FOUND
                                    } else {
                                        KitchenReferenceError.GENERIC
                                    },
                            )
                        }
                }
            }
        }

        private fun buildContent(state: KitchenReferenceEditorUiState): List<KitchenReferenceContentRecord> {
            val schema = KitchenReferenceSchema.of(state.type)
            val listKeys = schema.lists.map { it.key }.toSet()
            val fieldContent =
                schema.fields.mapIndexedNotNull { index, field ->
                    state.fields[field.key]?.trim()?.takeIf { it.isNotEmpty() }?.let { value ->
                        KitchenReferenceContentRecord(
                            section = field.key,
                            value = value,
                            sortOrder = index,
                        )
                    }
                }
            val itemContent =
                state.items
                    .filter { it.section in listKeys }
                    .filter { it.title.isNotBlank() || it.body.isNotBlank() }
                    .mapIndexed { index, item ->
                        KitchenReferenceContentRecord(
                            section = item.section,
                            label = item.title.trim().takeIf { it.isNotEmpty() },
                            value = item.body.trim(),
                            sortOrder = index,
                        )
                    }
            return fieldContent + itemContent
        }
    }

private fun CulinaryError.toEditorError(): KitchenReferenceError =
    when (this) {
        is CulinaryError.Validation -> KitchenReferenceError.NAME_REQUIRED
        is CulinaryError.Conflict -> KitchenReferenceError.DUPLICATE
        is CulinaryError.NotFound -> KitchenReferenceError.NOT_FOUND
        else -> KitchenReferenceError.GENERIC
    }
