package com.culinary.startup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.culinary.core.data.startup.StartupCoordinator
import com.culinary.core.data.startup.StartupState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 启动状态持有者。
 *
 * 只做状态编排与重试触发；数据库打开/版本判断在 [StartupCoordinator]。
 */
@HiltViewModel
class StartupViewModel
    @Inject
    constructor(
        private val coordinator: StartupCoordinator,
    ) : ViewModel() {
        private val _state = MutableStateFlow<StartupState?>(null)
        val state: StateFlow<StartupState?> = _state.asStateFlow()

        init {
            start()
        }

        fun retry() = start()

        private fun start() {
            _state.value = null
            viewModelScope.launch {
                _state.value = coordinator.start()
            }
        }
    }
