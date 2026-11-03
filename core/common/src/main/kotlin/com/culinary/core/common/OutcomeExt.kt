package com.culinary.core.common

/** 成功时转换值，失败时原样保留错误。 */
inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> =
    when (this) {
        is Outcome.Success -> Outcome.Success(transform(value))
        is Outcome.Failure -> this
    }

/** 成功时继续链式调用，失败时短路。 */
inline fun <T, R> Outcome<T>.flatMap(transform: (T) -> Outcome<R>): Outcome<R> =
    when (this) {
        is Outcome.Success -> transform(value)
        is Outcome.Failure -> this
    }

fun <T> Outcome<T>.valueOrNull(): T? = (this as? Outcome.Success)?.value

fun <T> Outcome<T>.errorOrNull(): CulinaryError? = (this as? Outcome.Failure)?.error
