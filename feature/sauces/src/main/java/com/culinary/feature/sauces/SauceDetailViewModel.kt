package com.culinary.feature.sauces

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.KitchenRecordKind
import com.culinary.core.domain.model.SauceId
import com.culinary.core.domain.model.SauceVersionId
import com.culinary.core.domain.usecase.archive.GetArchiveCategoryForRecordUseCase
import com.culinary.core.domain.usecase.kitchen.ObserveKitchenRelationsUseCase
import com.culinary.core.domain.usecase.sauce.GetSauceDetailUseCase
import com.culinary.core.domain.usecase.sauce.PublishSauceVersionUseCase
import com.culinary.core.domain.usecase.sauce.SauceImageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 酱汁详情 ViewModel：加载详情、发布草稿版本。 */
@HiltViewModel
class SauceDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val getSauceDetail: GetSauceDetailUseCase,
        private val publishSauceVersion: PublishSauceVersionUseCase,
        private val getRelations: ObserveKitchenRelationsUseCase,
        private val getArchiveCategoryForRecord: GetArchiveCategoryForRecordUseCase =
            GetArchiveCategoryForRecordUseCase(),
        private val sauceImages: SauceImageUseCase? = null,
    ) : ViewModel() {
        private val sauceId = SauceId(savedStateHandle.get<String>(SaucesRoutes.ARG_SAUCE_ID).orEmpty())
        private val _uiState = MutableStateFlow(SauceDetailUiState())
        val uiState: StateFlow<SauceDetailUiState> = _uiState.asStateFlow()

        init {
            load()
            viewModelScope.launch {
                getRelations(sauceId.value).collect { relations ->
                    _uiState.update { it.copy(relations = relations) }
                }
            }
        }

        fun load() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }
                when (val result = getSauceDetail(sauceId)) {
                    is Outcome.Success -> {
                        val category =
                            runCatching {
                                getArchiveCategoryForRecord(KitchenRecordKind.SAUCE, sauceId.value)
                            }.getOrNull()
                        val image =
                            runCatching {
                                sauceImages
                                    ?.listForSauce(sauceId.value)
                                    ?.firstOrNull { it.role.name == "PRIMARY" }
                                    ?.let { image -> sauceImages.read(image.id) }
                            }.getOrNull()
                        _uiState.update {
                            it.copy(
                                detail = result.value,
                                categoryPath = category?.path.orEmpty(),
                                imageBytes = image,
                                isLoading = false,
                                isPublishing = false,
                            )
                        }
                    }

                    is Outcome.Failure ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isPublishing = false,
                                error = SauceErrorMessages.describe(result.error),
                            )
                        }
                }
            }
        }

        fun publish(versionId: SauceVersionId) {
            viewModelScope.launch {
                _uiState.update { it.copy(isPublishing = true, error = null) }
                when (val result = publishSauceVersion(versionId)) {
                    is Outcome.Success -> load()
                    is Outcome.Failure ->
                        _uiState.update {
                            it.copy(isPublishing = false, error = SauceErrorMessages.describe(result.error))
                        }
                }
            }
        }

        fun clearError() {
            _uiState.update { it.copy(error = null) }
        }
    }
