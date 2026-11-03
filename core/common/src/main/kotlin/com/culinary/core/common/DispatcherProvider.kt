package com.culinary.core.common

import kotlinx.coroutines.CoroutineDispatcher

/**
 * 统一的调度器入口（E01 §6.4）。
 *
 * 业务代码不得直接使用 `Dispatchers.IO` 等全局对象；测试用 `kotlinx-coroutines-test`
 * 的调度器替换。
 */
interface DispatcherProvider {
    val main: CoroutineDispatcher
    val default: CoroutineDispatcher
    val io: CoroutineDispatcher
}
