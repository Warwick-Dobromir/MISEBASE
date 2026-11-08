package com.culinary.core.domain.repository

import com.culinary.core.domain.model.KitchenRelatedRecord
import kotlinx.coroutines.flow.Flow

/** 厨房档案关系读取端口；写入由种子导入器在数据层完成。 */
interface KitchenRelationRepository {
    fun observeForRecord(recordId: String): Flow<List<KitchenRelatedRecord>>
}
