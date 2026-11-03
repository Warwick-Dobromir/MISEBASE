package com.culinary.core.common

import kotlinx.coroutines.Dispatchers

/** 生产实现；Android 上 `Dispatchers.Main` 由平台主线程调度器提供。 */
class DefaultDispatcherProvider : DispatcherProvider {
    override val main get() = Dispatchers.Main
    override val default get() = Dispatchers.Default
    override val io get() = Dispatchers.IO
}
