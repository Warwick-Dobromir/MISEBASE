package com.culinary.kitchen.archive

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.culinary.core.common.Outcome
import com.culinary.core.data.kitchen.KitchenArchiveCategory
import com.culinary.core.data.kitchen.KitchenArchiveEntry
import com.culinary.core.data.kitchen.KitchenArchiveKind
import com.culinary.core.data.kitchen.KitchenArchiveReader
import com.culinary.core.domain.model.ArchiveLibrary
import com.culinary.core.domain.repository.ArchiveCategoryRepository
import com.culinary.navigation.LIBRARY_TAB_ARG
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArchiveCategoryFeedback(
    val message: String? = null,
    val error: String? = null,
)

data class ArchiveLibraryUiState(
    val entries: List<KitchenArchiveEntry> = emptyList(),
    val categories: List<KitchenArchiveCategory> = emptyList(),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val categoryMessage: String? = null,
    val categoryError: String? = null,
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
        private val section =
            KitchenArchiveSection.fromRoute(savedStateHandle.get<String>(LIBRARY_TAB_ARG))
                ?: KitchenArchiveSection.SAUCES

        /** 在当前库的指定分类下新建子库；不允许在厨房母界面创建根库。 */
        fun createSubcategory(
            parentId: String?,
            name: String,
        ) {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) {
                setCategoryFeedback()
                return
            }
            viewModelScope.launch {
                val library =
                    when (section.kind) {
                        KitchenArchiveKind.SAUCE -> ArchiveLibrary.SAUCE
                        KitchenArchiveKind.INGREDIENT -> ArchiveLibrary.INGREDIENT
                        KitchenArchiveKind.RECIPE -> ArchiveLibrary.RECIPE
                        else -> return@launch
                    }
                when (
                    val result =
                        categoryRepository.createCategory(
                            library = library,
                            parentId = parentId,
                            name = trimmed,
                        )
                ) {
                    is Outcome.Success ->
                        setCategoryFeedback(message = "已新建子库「${result.value.name}」")
                    is Outcome.Failure ->
                        setCategoryFeedback(error = "新建子库失败：${result.error}")
                }
            }
        }

        private val categoryFeedback = MutableStateFlow<ArchiveCategoryFeedback?>(null)

        private fun setCategoryFeedback(
            message: String? = null,
            error: String? = null,
        ) {
            categoryFeedback.value = ArchiveCategoryFeedback(message = message, error = error)
        }

        val uiState: StateFlow<ArchiveLibraryUiState> =
            combine(
                reader.observeEntries(),
                reader.observeCategories(section.kind),
                categoryFeedback,
            ) { entries, categories, feedback ->
                ArchiveLibraryUiState(
                    entries = entries,
                    categories = categories,
                    isLoading = false,
                    categoryMessage = feedback?.message,
                    categoryError = feedback?.error,
                )
            }.catch {
                emit(ArchiveLibraryUiState(isLoading = false, loadFailed = true))
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = ArchiveLibraryUiState(),
            )
    }
