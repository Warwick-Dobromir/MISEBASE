package com.culinary.kitchen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.culinary.core.data.kitchen.KitchenArchiveEntry
import com.culinary.core.data.kitchen.KitchenArchiveReader
import com.culinary.core.data.kitchen.KitchenCounts
import com.culinary.core.data.kitchen.KitchenCountsReader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** 厨房首页状态：加载中、失败或已得到真实分类数量。 */
data class KitchenUiState(
    val counts: KitchenCounts = KitchenCounts(),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
)

/**
 * 分类数量状态持有者。
 *
 * 直接观察 [KitchenCountsReader] 的 Flow，不缓存数值；档案新增/删除后自动更新。
 */
@HiltViewModel
class KitchenViewModel
    @Inject
    constructor(
        reader: KitchenCountsReader,
        archiveReader: KitchenArchiveReader,
    ) : ViewModel() {
        val uiState: StateFlow<KitchenUiState> =
            reader
                .observeCounts()
                .map { counts -> KitchenUiState(counts = counts, isLoading = false) }
                .catch { emit(KitchenUiState(isLoading = false, loadFailed = true)) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.Eagerly,
                    initialValue = KitchenUiState(),
                )

        /** 全局搜索数据；只读，错误时不伪造记录。 */
        val archiveEntries: StateFlow<List<KitchenArchiveEntry>> =
            archiveReader
                .observeEntries()
                .catch { emit(emptyList()) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.Eagerly,
                    initialValue = emptyList(),
                )
    }
