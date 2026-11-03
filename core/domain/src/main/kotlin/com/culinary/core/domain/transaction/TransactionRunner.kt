package com.culinary.core.domain.transaction

import com.culinary.core.common.Outcome

/**
 * 写事务的统一入口（E02 §4.2）。
 *
 * - 事务边界在 Use Case，Repository 只提供原子操作片段；
 * - 实现负责单写者互斥、BUSY 重试与错误映射；
 * - **禁止嵌套**：实现不可重入，嵌套会死锁；
 * - 事务内不做文件/网络/图片解码。
 */
interface TransactionRunner {
    suspend fun <T> write(block: suspend () -> T): Outcome<T>
}
