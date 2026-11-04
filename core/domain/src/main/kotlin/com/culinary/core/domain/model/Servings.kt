package com.culinary.core.domain.model

/**
 * 份数（servings）。
 *
 * 允许小数份（如 0.5 份）以支持"半份"缩放；必须为有限正数。
 * 份数缩放只影响展开倍数，不改变配方版本本身。
 */
@JvmInline
value class Servings(
    val count: Double,
) {
    init {
        require(count.isFinite()) { "servings must be finite, was $count" }
        require(count > 0.0) { "servings must be > 0, was $count" }
    }

    fun scaleFactor(relativeTo: Servings): Double = count / relativeTo.count
}
