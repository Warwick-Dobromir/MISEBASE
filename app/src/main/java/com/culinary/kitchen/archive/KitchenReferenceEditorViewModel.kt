package com.culinary.kitchen.archive

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.culinary.core.common.CulinaryError
import com.culinary.core.common.Outcome
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

data class KitchenReferenceEditorUiState(
    val type: KitchenReferenceType = KitchenReferenceType.TECHNIQUE,
    val id: String? = null,
    val name: String = "",
    val category: String = "",
    val description: String = "",
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
                    is Outcome.Success ->
                        _uiState.update {
                            it.copy(
                                type = result.value.type,
                                name = result.value.name,
                                category = result.value.category.orEmpty(),
                                description = result.value.description.orEmpty(),
                                isLoading = false,
                            )
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
    }

private fun CulinaryError.toEditorError(): KitchenReferenceError =
    when (this) {
        is CulinaryError.Validation -> KitchenReferenceError.NAME_REQUIRED
        is CulinaryError.Conflict -> KitchenReferenceError.DUPLICATE
        is CulinaryError.NotFound -> KitchenReferenceError.NOT_FOUND
        else -> KitchenReferenceError.GENERIC
    }
