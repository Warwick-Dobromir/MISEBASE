package com.culinary.core.common

import java.time.Instant

/**
 * 可注入的时间源；业务禁止直接调用 `System.currentTimeMillis()`，否则无法测试。
 */
fun interface Clock {
    fun now(): Instant
}
