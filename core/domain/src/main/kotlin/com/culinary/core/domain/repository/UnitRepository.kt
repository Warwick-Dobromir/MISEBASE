package com.culinary.core.domain.repository

import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.IngredientUnitConversion
import com.culinary.core.domain.model.Unit
import com.culinary.core.domain.model.UnitId

/** 单位与食材级换算的只读端口；写入在设置/食材功能中扩展。 */
interface UnitRepository {
    suspend fun all(): List<Unit>

    suspend fun find(id: UnitId): Unit?

    /** 返回该食材已确认的换算依据；没有记录时返回空列表，换算必须失败而不是猜。 */
    suspend fun conversionsFor(ingredientId: IngredientId): List<IngredientUnitConversion>
}
