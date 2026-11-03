package com.culinary.core.common

/**
 * 统一结果类型（E03 §4.1）。
 *
 * - 可预期失败用 [Failure] 返回，不抛异常；
 * - 不在 [Outcome] 里放堆栈，堆栈只进日志；
 * - 编程错误仍用 `require`/`check`，由全局边界记录。
 */
sealed interface Outcome<out T> {
    data class Success<out T>(
        val value: T,
    ) : Outcome<T>

    data class Failure(
        val error: CulinaryError,
    ) : Outcome<Nothing>

    companion object {
        fun <T> success(value: T): Outcome<T> = Success(value)

        fun failure(error: CulinaryError): Outcome<Nothing> = Failure(error)
    }
}
