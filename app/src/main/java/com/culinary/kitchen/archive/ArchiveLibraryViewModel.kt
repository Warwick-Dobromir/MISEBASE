package com.culinary.kitchen.archive

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.culinary.core.data.kitchen.KitchenArchiveCategory
import com.culinary.core.data.kitchen.KitchenArchiveEntry
import com.culinary.core.data.kitchen.KitchenArchiveReader
import com.culinary.navigation.LIBRARY_TAB_ARG
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ArchiveLibraryUiState(
    val entries: List<KitchenArchiveEntry> = emptyList(),
    val categories: List<KitchenArchiveCategory> = emptyList(),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
)

/** 六库目录状态：当前库档案 + 分类树；分类/搜索状态由页面 SavedState 保存。 */
@HiltViewModel
class ArchiveLibraryViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        reader: KitchenArchiveReader,
    ) : ViewModel() {
        private val section =
            KitchenArchiveSection.fromRoute(savedStateHandle.get<String>(LIBRARY_TAB_ARG))
                ?: KitchenArchiveSection.SAUCES

        val uiState: StateFlow<ArchiveLibraryUiState> =
            combine(
                reader.observeEntries(),
                reader.observeCategories(section.kind),
            ) { entries, categories ->
                ArchiveLibraryUiState(entries = entries, categories = categories, isLoading = false)
            }.catch {
                emit(ArchiveLibraryUiState(isLoading = false, loadFailed = true))
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = ArchiveLibraryUiState(),
            )
    }
