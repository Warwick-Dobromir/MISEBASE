package com.culinary.kitchen.archive

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.culinary.core.common.Outcome
import com.culinary.core.data.kitchen.KitchenReferenceRecord
import com.culinary.core.data.kitchen.KitchenReferenceStore
import com.culinary.core.data.kitchen.KitchenReferenceType
import com.culinary.core.domain.model.KitchenRecordKind
import com.culinary.core.domain.model.KitchenRelatedRecord
import com.culinary.core.domain.usecase.archive.GetArchiveCategoryForRecordUseCase
import com.culinary.core.domain.usecase.kitchen.ObserveKitchenRelationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class KitchenReferenceDetailUiState(
    val type: KitchenReferenceType = KitchenReferenceType.TECHNIQUE,
    val record: KitchenReferenceRecord? = null,
    val categoryPath: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val isArchiving: Boolean = false,
    val archived: Boolean = false,
    val error: KitchenReferenceError? = null,
    val relations: List<KitchenRelatedRecord> = emptyList(),
)

/** 新增参考资料详情；编辑/归档只影响本记录。 */
@HiltViewModel
class KitchenReferenceDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val store: KitchenReferenceStore,
        private val getRelations: ObserveKitchenRelationsUseCase,
        private val getArchiveCategoryForRecord: GetArchiveCategoryForRecordUseCase =
            GetArchiveCategoryForRecordUseCase(),
    ) : ViewModel() {
        private val type =
            kitchenReferenceTypeFromRoute(savedStateHandle.get<String>(KITCHEN_REFERENCE_KIND_ARG))
                ?: KitchenReferenceType.TECHNIQUE
        private val referenceId = savedStateHandle.get<String>(KITCHEN_REFERENCE_ID_ARG).orEmpty()

        private val _uiState =
            MutableStateFlow(
                KitchenReferenceDetailUiState(
                    type = type,
                    isLoading = referenceId.isNotBlank(),
                ),
            )
        val uiState: StateFlow<KitchenReferenceDetailUiState> = _uiState.asStateFlow()

        init {
            load()
            viewModelScope.launch {
                getRelations(referenceId).collect { relations ->
                    _uiState.update { it.copy(relations = relations) }
                }
            }
        }

        fun archive() {
            if (_uiState.value.isArchiving || referenceId.isBlank()) return
            _uiState.update { it.copy(isArchiving = true, error = null) }
            viewModelScope.launch {
                when (val result = store.archive(referenceId)) {
                    is Outcome.Success -> _uiState.update { it.copy(isArchiving = false, archived = true) }
                    is Outcome.Failure ->
                        _uiState.update {
                            it.copy(isArchiving = false, error = KitchenReferenceError.GENERIC)
                        }
                }
            }
        }

        fun load() {
            if (referenceId.isBlank()) {
                _uiState.update {
                    it.copy(isLoading = false, error = KitchenReferenceError.NOT_FOUND)
                }
                return
            }
            viewModelScope.launch {
                when (val result = store.get(referenceId)) {
                    is Outcome.Success -> {
                        val record = result.value
                        val recordKind =
                            when (record.type) {
                                KitchenReferenceType.TECHNIQUE -> KitchenRecordKind.TECHNIQUE
                                KitchenReferenceType.PRODUCTION_STANDARD ->
                                    KitchenRecordKind.PRODUCTION_STANDARD
                                KitchenReferenceType.STORAGE_STANDARD ->
                                    KitchenRecordKind.STORAGE_STANDARD
                            }
                        val category =
                            runCatching {
                                getArchiveCategoryForRecord(recordKind, referenceId)
                            }.getOrNull()
                        _uiState.update {
                            it.copy(
                                type = record.type,
                                record = record,
                                categoryPath = category?.path.orEmpty(),
                                isLoading = false,
                            )
                        }
                    }

                    is Outcome.Failure ->
                        _uiState.update {
                            it.copy(isLoading = false, error = KitchenReferenceError.NOT_FOUND)
                        }
                }
            }
        }
    }
