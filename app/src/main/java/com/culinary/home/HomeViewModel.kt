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
 * 首页状态持有者（MB-HOME-001）。
 *
 * 只做状态编排：库存与知识库分别读取、分别失败；不实现库存计算或档案规则。
 * 汇总读取在 [HomeSummaryReader] 的 IO 上下文执行，本类不直接触碰数据库。
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

        init {
            loadAll(showLoading = true)
        }

        /** 冷启动后或从其他页面返回时刷新；已有数据保留到新结果返回，避免闪烁。 */
        fun refresh() = loadAll(showLoading = false)

        fun retryInventory() = loadInventory(showLoading = true)

        fun retryKnowledge() = loadKnowledge(showLoading = true)

        private fun loadAll(showLoading: Boolean) {
            _uiState.update { it.copy(todayLabel = formatToday()) }
            loadInventory(showLoading)
            loadKnowledge(showLoading)
        }

        private fun loadInventory(showLoading: Boolean) {
            if (inventoryJob?.isActive == true) return
            inventoryJob =
                viewModelScope.launch {
                    if (showLoading) {
                        _uiState.update { it.copy(inventory = HomeSection.Loading) }
                    }
                    val next =
                        try {
                            when (val result = reader.inventorySummary()) {
                                is Outcome.Success -> HomeSection.Ready(result.value)
                                is Outcome.Failure -> HomeSection.Failed(HomeErrorMessages.describe(result.error))
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            HomeSection.Failed(HomeErrorMessages.UNEXPECTED)
                        }
                    _uiState.update { it.copy(inventory = next) }
                }
        }

        private fun loadKnowledge(showLoading: Boolean) {
            if (knowledgeJob?.isActive == true) return
            knowledgeJob =
                viewModelScope.launch {
                    if (showLoading) {
                        _uiState.update { it.copy(knowledge = HomeSection.Loading) }
                    }
                    val next =
                        try {
                            when (val result = reader.knowledgeSummary()) {
                                is Outcome.Success -> HomeSection.Ready(result.value)
                                is Outcome.Failure -> HomeSection.Failed(HomeErrorMessages.describe(result.error))
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            HomeSection.Failed(HomeErrorMessages.UNEXPECTED)
                        }
                    _uiState.update { it.copy(knowledge = next) }
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
