package com.culinary.core.domain.model

/**
 * 单位标识。
 *
 * 只承诺非空不透明字符串；存储格式与生成规则属于仍为 `PROPOSED` 的 D-005，
 * 这里不解析、不校验 UUID 结构。
 */
@JvmInline
value class UnitId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "unit id must not be blank" }
    }

    override fun toString(): String = value
}
