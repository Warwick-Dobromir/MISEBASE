package com.culinary.core.domain.model

/**
 * 单位维度（A06 §2，D-008）。
 *
 * 跨维度换算只允许通过食材级显式换算进行；没有依据时必须拒绝，不能猜密度。
 */
enum class UnitDimension {
    MASS,
    VOLUME,
    COUNT,
}
