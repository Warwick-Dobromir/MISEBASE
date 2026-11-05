package com.culinary.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.culinary.core.common.Clock
import com.culinary.core.common.Outcome
import com.culinary.core.data.home.HomeSummaryReader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/**
 * 首页状态持有者（MB-HOME-001 / MB-UI-001 §8.5）。
 *
 * 只做状态编排：库存与知识库分别读取、分别失败；不实现库存计算或档案规则。
 *
 * 刷新策略（本次修正）：
 * - 旧实现用 `if (job?.isActive == true) return`，正在加载时的刷新请求会被**静默丢弃**；
 * - 现在改为“最新请求获胜”：新请求取消同一模块的旧读取并重新执行；
 * - 刷新不显示骨架屏，保留旧数据，只用 [HomeUiState.isRefreshing] 驱动顶部细进度条；
 * - 两个模块的刷新计数合并，任一完成不会提前熄灭进度条。
 */
@HiltViewModel
@Suppress("TooGenericExceptionCaught", "SwallowedException") // 错误边界：异常在此转换为模块级错误态
class HomeViewModel
    @Inject
    constructor(
        private val reader: HomeSummaryReader,
        private val clock: Clock,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(HomeUiState(todayLabel = formatToday()))
        val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

        private var inventoryJob: Job? = null
        private var knowledgeJob: Job? = null
        private var refreshingLoads = 0

        init {
            loadAll(showLoading = true, refresh = false)
        }

        /** 冷启动后或从其他页面返回时刷新；已有数据保留到新结果返回，避免闪烁。 */
        fun refresh() = loadAll(showLoading = false, refresh = true)

        fun retryInventory() = loadInventory(showLoading = true, refresh = false)

        fun retryKnowledge() = loadKnowledge(showLoading = true, refresh = false)

        private fun loadAll(
            showLoading: Boolean,
            refresh: Boolean,
        ) {
            _uiState.update { it.copy(todayLabel = formatToday()) }
            loadInventory(showLoading = showLoading, refresh = refresh)
            loadKnowledge(showLoading = showLoading, refresh = refresh)
        }

        private fun loadInventory(
            showLoading: Boolean,
            refresh: Boolean,
        ) {
            inventoryJob?.cancel()
            if (refresh) beginRefresh()
            inventoryJob =
                viewModelScope.launch {
                    try {
                        if (showLoading) {
                            _uiState.update { it.copy(inventory = HomeSection.Loading) }
                        }
                        val next =
                            try {
                                when (val result = reader.inventorySummary()) {
                                    is Outcome.Success -> HomeSection.Ready(result.value)
                                    is Outcome.Failure ->
                                        HomeSection.Failed(
                                            message = HomeErrorMessages.userMessage(result.error),
                                            diagnosticCode = HomeErrorMessages.diagnosticCode(result.error),
                                        )
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Throwable) {
                                HomeSection.Failed(message = HomeErrorMessages.UNEXPECTED)
                            }
                        _uiState.update { it.copy(inventory = next) }
                    } finally {
                        if (refresh) endRefresh()
                    }
                }
        }

        private fun loadKnowledge(
            showLoading: Boolean,
            refresh: Boolean,
        ) {
            knowledgeJob?.cancel()
            if (refresh) beginRefresh()
            knowledgeJob =
                viewModelScope.launch {
                    try {
                        if (showLoading) {
                            _uiState.update { it.copy(knowledge = HomeSection.Loading) }
                        }
                        val next =
                            try {
                                when (val result = reader.knowledgeSummary()) {
                                    is Outcome.Success -> HomeSection.Ready(result.value)
                                    is Outcome.Failure ->
                                        HomeSection.Failed(
                                            message = HomeErrorMessages.userMessage(result.error),
                                            diagnosticCode = HomeErrorMessages.diagnosticCode(result.error),
                                        )
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Throwable) {
                                HomeSection.Failed(message = HomeErrorMessages.UNEXPECTED)
                            }
                        _uiState.update { it.copy(knowledge = next) }
                    } finally {
                        if (refresh) endRefresh()
                    }
                }
        }

        private fun beginRefresh() {
            refreshingLoads += 1
            _uiState.update { it.copy(isRefreshing = true) }
        }

        private fun endRefresh() {
            refreshingLoads = (refreshingLoads - 1).coerceAtLeast(0)
            if (refreshingLoads == 0) {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }

        private fun formatToday(): String {
            val date = clock.now().atZone(ZoneId.systemDefault()).toLocalDate()
            return date.format(DATE_FORMAT)
        }

        private companion object {
            val DATE_FORMAT: DateTimeFormatter =
                DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.SIMPLIFIED_CHINESE)
        }
    }
