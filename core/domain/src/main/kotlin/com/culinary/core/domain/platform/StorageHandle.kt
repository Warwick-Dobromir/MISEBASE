package com.culinary.core.domain.platform

/**
 * 平台存储的不透明句柄（A03A §8.1）。
 *
 * 内部可以保存 SAF `Uri` 字符串，但领域层不感知 Android 类型；`core:data` 只把它
 * 当字符串处理，不解析、不拼接。
 */
@JvmInline
value class StorageHandle(val value: String) {
    init {
        require(value.isNotBlank()) { "storage handle must not be blank" }
    }
}
