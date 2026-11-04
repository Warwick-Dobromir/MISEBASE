package com.culinary.core.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * 新建批次输入：余额从 0 开始，首笔入库流水负责增加余额。
 *
 * 批次与流水必须在同一事务内写入，避免出现"批次存在但余额/流水缺失"。
 */
data class NewInventoryBatch(
    val productId: ProductId,
    val locationId: LocationId,
    val stockUnit: Unit,
    val batchCode: String? = null,
    val receivedAt: Instant,
    val expiresOn: LocalDate? = null,
)
