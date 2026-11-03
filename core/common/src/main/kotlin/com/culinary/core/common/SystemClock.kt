package com.culinary.core.common

import java.time.Instant

/** 生产环境时间源；测试使用固定 `Clock` 实现。 */
object SystemClock : Clock {
    override fun now(): Instant = Instant.now()
}
