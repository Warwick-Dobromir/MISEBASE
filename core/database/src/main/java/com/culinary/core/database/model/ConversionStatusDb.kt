package com.culinary.core.database.model

/** 需求换算状态（A06 §2）；不可换算必须显式标记，不能静默丢弃。 */
enum class ConversionStatusDb {
    OK,
    UNCONVERTIBLE,
    MISSING_PRODUCT,
    UNIT_MISMATCH,
}
