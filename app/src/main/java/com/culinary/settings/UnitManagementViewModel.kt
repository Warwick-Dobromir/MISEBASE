package com.culinary.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.culinary.core.domain.model.Unit
import com.culinary.core.domain.model.UnitDimension
import com.culinary.core.domain.model.UnitId
import com.culinary.core.domain.repository.UnitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class UnitManagementUiState(
    val units: List<Unit> = emptyList(),
    val loading: Boolean = true,
    val message: String? = null,
)

@HiltViewModel
class UnitManagementViewModel
    @Inject
    constructor(private val repository: UnitRepository) : ViewModel() {
        private val _uiState = MutableStateFlow(UnitManagementUiState())
        val uiState = _uiState.asStateFlow()

        init { refresh() }

        fun refresh() {
            viewModelScope.launch {
                val units = withContext(Dispatchers.IO) { repository.allIncludingInactive() }
                _uiState.value = UnitManagementUiState(units = units, loading = false)
            }
        }

        fun save(
            id: String?,
            name: String,
            symbol: String,
            dimension: UnitDimension,
            factor: Double,
        ) {
            val cleanName = name.trim()
            val cleanSymbol = symbol.trim()
            if (cleanName.isBlank() || cleanSymbol.isBlank()) {
                _uiState.value = _uiState.value.copy(message = "单位名称和显示符号不能为空")
                return
            }
            viewModelScope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        if (id == null) {
                            val code = "custom_" + UUID.randomUUID().toString().replace("-", "")
                            repository.create(
                                Unit(
                                    id = UnitId(UUID.randomUUID().toString()),
                                    code = code,
                                    name = cleanName,
                                    symbol = cleanSymbol,
                                    dimension = dimension,
                                    baseFactor = if (dimension == UnitDimension.COUNT) 1.0 else factor,
                                    isBase = false,
                                    isApproximate = false,
                                    displayPrecision = if (dimension == UnitDimension.COUNT) 0 else 2,
                                ),
                            )
                        } else {
                            val current = repository.find(UnitId(id)) ?: error("单位不存在")
                            repository.update(current.copy(name = cleanName, symbol = cleanSymbol))
                        }
                    }
                }.onSuccess {
                    refresh()
                    _uiState.value = _uiState.value.copy(message = if (id == null) "单位已添加" else "单位已更新")
                }.onFailure {
                    _uiState.value = _uiState.value.copy(message = it.message ?: "保存失败")
                }
            }
        }

        fun setActive(unit: Unit, active: Boolean) {
            viewModelScope.launch {
                runCatching {
                    withContext(Dispatchers.IO) { repository.setActive(unit.id, active) }
                }.onSuccess {
                    refresh()
                }.onFailure {
                    _uiState.value = _uiState.value.copy(message = it.message ?: "操作失败")
                }
            }
        }

        fun dismissMessage() {
            _uiState.value = _uiState.value.copy(message = null)
        }
    }
