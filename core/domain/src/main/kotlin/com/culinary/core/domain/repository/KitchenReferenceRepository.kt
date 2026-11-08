package com.culinary.core.domain.repository

import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.KitchenReference
import com.culinary.core.domain.model.KitchenReferenceId
import com.culinary.core.domain.model.KitchenReferenceKind
import kotlinx.coroutines.flow.Flow

/**
 * 厨房新增参考资料聚合的读写端口。
 *
 * 只服务烹饪技术 / 制作标准 / 储存标准；既有酱汁、食材、菜谱仍走各自 Repository。
 */
interface KitchenReferenceRepository {
    fun observeActive(kind: KitchenReferenceKind): Flow<List<KitchenReference>>

    fun observeAllActive(): Flow<List<KitchenReference>>

    suspend fun get(id: KitchenReferenceId): Outcome<KitchenReference>

    suspend fun save(reference: KitchenReference): Outcome<KitchenReferenceId>

    suspend fun archive(id: KitchenReferenceId): Outcome<Unit>
}
