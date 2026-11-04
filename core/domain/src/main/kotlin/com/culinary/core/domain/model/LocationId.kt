package com.culinary.core.domain.model

/** 储存位置标识；调拨必须记录源/目标位置（A06 §6.1）。 */
@JvmInline
value class LocationId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "location id must not be blank" }
    }

    override fun toString(): String = value
}
