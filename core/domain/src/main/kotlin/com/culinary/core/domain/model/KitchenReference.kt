package com.culinary.core.domain.model

/** 厨房参考资料类型：当前只有任务书新增的三类。 */
enum class KitchenReferenceKind {
    TECHNIQUE,
    PRODUCTION_STANDARD,
    STORAGE_STANDARD,
    ;

    val isStandard: Boolean
        get() = this == PRODUCTION_STANDARD || this == STORAGE_STANDARD
}

@JvmInline
value class KitchenReferenceId(
    val value: String,
)

/**
 * 厨房参考资料（烹饪技术 / 制作标准 / 储存标准）。
 *
 * 现有酱汁、食材、菜谱继续使用各自聚合；本模型只承载任务书新增的三类，
 * 字段保持最小：名称、分类、说明，不把既有档案字段强行套入。
 */
data class KitchenReference(
    val id: KitchenReferenceId,
    val kind: KitchenReferenceKind,
    val name: String,
    val normalizedName: String,
    val category: String?,
    val description: String?,
    val status: EntityStatus,
    val englishName: String? = null,
    val seedNote: String? = null,
    val seedId: String? = null,
    val seedVersion: String? = null,
) {
    init {
        require(name.isNotBlank()) { "kitchen reference name must not be blank" }
        require(normalizedName.isNotBlank()) { "kitchen reference normalizedName must not be blank" }
    }

    val isActive: Boolean get() = status == EntityStatus.ACTIVE

    val isSeed: Boolean get() = seedId != null
}
