package com.culinary.core.common

import java.util.Locale
import java.util.UUID

/**
 * 基于 UUID v4 的主键生成（D-005 已接受：小写、36 字符、TEXT 存储）。
 */
class UuidV4Generator : IdGenerator {
    override fun newId(): String = UUID.randomUUID().toString().lowercase(Locale.ROOT)
}
