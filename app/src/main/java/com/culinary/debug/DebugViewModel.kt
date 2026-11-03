package com.culinary.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.culinary.core.common.Outcome
import com.culinary.core.data.debug.DebugInfoItem
import com.culinary.core.data.debug.DebugInfoProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DebugViewModel
    @Inject
    constructor(
        private val provider: DebugInfoProvider,
    ) : ViewModel() {
        private val _items = MutableStateFlow<List<DebugInfoItem>?>(null)
        val items: StateFlow<List<DebugInfoItem>?> = _items.asStateFlow()

        private val _error = MutableStateFlow<String?>(null)
        val error: StateFlow<String?> = _error.asStateFlow()

        init {
            load()
        }

        fun load() {
            viewModelScope.launch {
                _error.value = null
                when (val result = provider.read()) {
                    is Outcome.Success -> _items.value = result.value
                    is Outcome.Failure -> {
                        _items.value = emptyList()
                        _error.value = result.error.code.value
                    }
                }
            }
        }
    }
