package com.culinary.core.domain.model

/**
 * 酱汁标识（酱汁本身，不含版本）。只承诺非空不透明字符串。
 */
@JvmInline
value class SauceId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "sauce id must not be blank" }
    }

    override fun toString(): String = value
}
