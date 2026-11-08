package com.culinary.core.designsystem.component

/** 详情页关联行；由调用方把领域关系映射成展示文案。 */
data class CulinaryRelationItem(
    val eyebrow: String,
    val title: String,
    val meta: String? = null,
    val onClick: () -> Unit,
)
