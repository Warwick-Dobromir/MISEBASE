package com.culinary.core.domain.model

/**
 * 酱汁档案（A06 §4.2 的最小领域视图 + Stage 3 档案字段）。
 *
 * 酱汁与菜谱对称：档案本身不承载配方内容，内容由不可变的 [SauceVersion] 表达。
 * 归档只影响新建引用，不改变既有版本与历史结果（D-020）。
 */
data class Sauce(
    val id: SauceId,
    val name: String,
    val normalizedName: String,
    val status: EntityStatus,
    val family: String? = null,
    val texture: String? = null,
    val flavorProfile: String? = null,
    val usageNotes: String? = null,
    val storageNotes: String? = null,
    val shelfLifeDays: Int? = null,
) {
    init {
        require(name.isNotBlank()) { "sauce name must not be blank" }
        require(normalizedName.isNotBlank()) { "sauce normalizedName must not be blank" }
        require(shelfLifeDays == null || shelfLifeDays >= 0) { "shelfLifeDays must be >= 0" }
    }

    val isActive: Boolean get() = status == EntityStatus.ACTIVE
}
