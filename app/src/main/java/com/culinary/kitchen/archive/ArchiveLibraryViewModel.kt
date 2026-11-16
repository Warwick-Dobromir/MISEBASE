package com.culinary.kitchen.archive

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.culinary.core.data.kitchen.KitchenArchiveCategory
import com.culinary.core.data.kitchen.KitchenArchiveEntry
import com.culinary.core.data.kitchen.KitchenArchiveReader
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.ArchiveLibrary
import com.culinary.core.domain.repository.ArchiveCategoryRepository
import com.culinary.navigation.LIBRARY_TAB_ARG
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ArchiveLibraryUiState(
    val entries: List<KitchenArchiveEntry> = emptyList(),
    val categories: List<KitchenArchiveCategory> = emptyList(),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val categoryCreateError: String? = null,
)

/** 六库目录状态：当前库档案 + 分类树；分类/搜索状态由页面 SavedState 保存。 */
@HiltViewModel
class ArchiveLibraryViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        reader: KitchenArchiveReader,
        private val categoryRepository: ArchiveCategoryRepository,
    ) : ViewModel() {
        private val categoryCreateError = MutableStateFlow<String?>(null)
        private val section =
            KitchenArchiveSection.fromRoute(savedStateHandle.get<String>(LIBRARY_TAB_ARG))
                ?: KitchenArchiveSection.SAUCES

        val uiState: StateFlow<ArchiveLibraryUiState> =
            combine(
                reader.observeEntries(),
                reader.observeCategories(section.kind),
                categoryCreateError,
            ) { entries, categories, createError ->
                ArchiveLibraryUiState(
                    entries = entries,
                    categories = categories,
                    isLoading = false,
                    categoryCreateError = createError,
                )
            }.catch {
                emit(ArchiveLibraryUiState(isLoading = false, loadFailed = true))
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = ArchiveLibraryUiState(),
            )
        
        /** 当前资料库内创建分类；parentId 允许指向任意已有层级。 */
        fun createCategory(parentId: String?, name: String) {
            viewModelScope.launch {
                val library =
                    when (section) {
                        KitchenArchiveSection.SAUCES -> ArchiveLibrary.SAUCE
                        KitchenArchiveSection.INGREDIENTS -> ArchiveLibrary.INGREDIENT
                        KitchenArchiveSection.RECIPES -> ArchiveLibrary.RECIPE
                    }
                when (val result = categoryRepository.createCategory(library, parentId, name)) {
                    is Outcome.Success -> categoryCreateError.value = null
                    is Outcome.Failure -> {
                        categoryCreateError.value =
                            when (result.error) {
                                is com.culinary.core.common.CulinaryError.Conflict -> "同一层级已存在同名子库"
                                is com.culinary.core.common.CulinaryError.Validation -> "子库名称无效，请检查后重试"
                                else -> "新建子库失败，请重试"
                            }
                    }
                }
            }
        }
    }
