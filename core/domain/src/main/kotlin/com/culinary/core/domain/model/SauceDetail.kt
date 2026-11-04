package com.culinary.core.domain.model

/**
 * 酱汁详情投影：档案 + 当前版本 + 版本历史 + 已解析的组成行标签。
 *
 * 组成行的名称解析在用例内完成，UI 不直接访问 Repository。
 */
data class SauceDetail(
    val sauce: Sauce,
    val currentVersion: SauceVersion?,
    val versions: List<SauceVersion>,
    val lines: List<SauceLineView>,
) {
    val hasPublishedVersion: Boolean get() = versions.any { it.isPublished }
    val hasDraftVersion: Boolean get() = versions.any { it.status == VersionStatus.DRAFT }
}

/** 组成行展示模型：数量保留原单位，不在这里做换算。 */
data class SauceLineView(
    val lineNo: Int,
    val label: String,
    val quantity: Quantity,
    val isOptional: Boolean,
)
