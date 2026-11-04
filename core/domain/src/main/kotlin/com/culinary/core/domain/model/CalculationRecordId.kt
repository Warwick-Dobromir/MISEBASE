package com.culinary.core.domain.model

/** 计算记录标识；计算记录是旧结果回放的载体（MB-D-017）。 */
@JvmInline
value class CalculationRecordId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "calculation record id must not be blank" }
    }

    override fun toString(): String = value
}
