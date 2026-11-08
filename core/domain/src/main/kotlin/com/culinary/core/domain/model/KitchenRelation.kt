package com.culinary.core.domain.model

/** 厨房六类档案的统一类型；用于关系跳转，不改变各聚合自身模型。 */
enum class KitchenRecordKind {
    SAUCE,
    INGREDIENT,
    RECIPE,
    TECHNIQUE,
    PRODUCTION_STANDARD,
    STORAGE_STANDARD,
}

/** 一条可跳转的厨房档案关系；标签在导入时快照，ID 用于精确定位。 */
data class KitchenRelatedRecord(
    val sourceRecordId: String,
    val sourceKind: KitchenRecordKind,
    val targetRecordId: String,
    val targetKind: KitchenRecordKind,
    val targetLabel: String,
    val relationLabel: String,
    val sortOrder: Int = 0,
)

/** 编辑器提交的一条手工关系；[sortOrder] 决定显示顺序。 */
data class KitchenRelationDraft(
    val targetRecordId: String,
    val targetKind: KitchenRecordKind,
    val targetLabel: String,
    val relationLabel: String,
    val sortOrder: Int,
)
