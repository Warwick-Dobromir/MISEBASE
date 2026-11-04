package com.culinary.core.domain.usecase.unit

import com.culinary.core.domain.model.Unit
import com.culinary.core.domain.repository.UnitRepository

/** 读取全部启用单位（表单选择器与数量展示共用）。 */
class ListUnitsUseCase(
    private val repository: UnitRepository,
) {
    suspend operator fun invoke(): List<Unit> = repository.all()
}
