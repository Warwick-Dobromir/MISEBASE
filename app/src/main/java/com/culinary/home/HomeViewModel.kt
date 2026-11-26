package com.culinary.home

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * 首页状态持有者（HOME-001）。
 *
 * 当前阶段首页只使用本地演示数据：计划勾选是内存状态，采购单为只读列表。
 * 这里不读取也不写入 Room，避免模拟数据污染正式数据库。
 */
@HiltViewModel
class HomeViewModel
    @Inject
    constructor() : ViewModel() {
        private val _uiState = MutableStateFlow(HomeUiState())
        val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

        /** 点击圆圈或计划文字均可切换完成状态；未知 id 不改变任何数据。 */
        fun togglePlan(planId: String) {
            _uiState.update { state ->
                state.copy(
                    plans =
                        state.plans.map { plan ->
                            if (plan.id == planId) plan.copy(completed = !plan.completed) else plan
                        },
                )
            }
        }
    }
