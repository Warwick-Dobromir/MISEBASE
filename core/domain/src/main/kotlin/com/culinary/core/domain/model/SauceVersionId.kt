package com.culinary.core.domain.model

/**
 * 酱汁版本标识。与菜谱版本一样，引用方固定版本号，不跟随"当前版本"漂移（D-006）。
 */
@JvmInline
value class SauceVersionId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "sauce version id must not be blank" }
    }

    override fun toString(): String = value
}
