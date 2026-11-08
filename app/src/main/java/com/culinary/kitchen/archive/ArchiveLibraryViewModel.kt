package com.culinary.kitchen.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.culinary.core.data.kitchen.KitchenArchiveEntry
import com.culinary.core.data.kitchen.KitchenArchiveReader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ArchiveLibraryUiState(
    val entries: List<KitchenArchiveEntry> = emptyList(),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
)

/** 六个资料库共享的只读列表状态；内部搜索/筛选在页面组合。 */
@HiltViewModel
class ArchiveLibraryViewModel
    @Inject
    constructor(
        reader: KitchenArchiveReader,
    ) : ViewModel() {
        val uiState: StateFlow<ArchiveLibraryUiState> =
            reader
                .observeEntries()
                .map { ArchiveLibraryUiState(entries = it, isLoading = false) }
                .catch { emit(ArchiveLibraryUiState(isLoading = false, loadFailed = true)) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.Eagerly,
                    initialValue = ArchiveLibraryUiState(),
                )
    }
