package com.culinary.core.domain.model

/** 库存批次标识；批次是库存记账与 FEFO 分配的最小单位（A06 §6.2）。 */
@JvmInline
value class BatchId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "batch id must not be blank" }
    }

    override fun toString(): String = value
}
