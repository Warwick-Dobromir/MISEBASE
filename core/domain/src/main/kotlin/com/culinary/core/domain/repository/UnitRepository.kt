package com.culinary.core.domain.repository

import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.IngredientUnitConversion
import com.culinary.core.domain.model.Unit
import com.culinary.core.domain.model.UnitId

/** 单位读取、维护与食材级换算端口。 */
interface UnitRepository {
    suspend fun all(): List<Unit>

    suspend fun allIncludingInactive(): List<Unit>

    suspend fun find(id: UnitId): Unit?

    suspend fun create(unit: Unit)

    suspend fun update(unit: Unit)

    /** 停用而非物理删除，避免破坏历史档案中的单位引用。 */
    suspend fun setActive(id: UnitId, active: Boolean)

    /** 返回该食材已确认的换算依据；没有记录时返回空列表，换算必须失败而不是猜。 */
    suspend fun conversionsFor(ingredientId: IngredientId): List<IngredientUnitConversion>
}
