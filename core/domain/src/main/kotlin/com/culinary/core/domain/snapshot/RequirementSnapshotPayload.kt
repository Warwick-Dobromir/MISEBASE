package com.culinary.core.domain.snapshot

import com.culinary.core.domain.model.Quantity
import com.culinary.core.domain.rule.requirement.RequirementGroup
import kotlinx.serialization.Serializable

/** 计划快照中的一行需求（A06 §9，稳定 JSON 字段）。 */
@Serializable
data class RequirementSnapshotLine(
    val ingredientId: String,
    val amount: Double,
    val unitCode: String,
    val optional: Boolean,
)

/**
 * 需求快照 payload。
 *
 * 字段是快照兼容性契约：只允许新增可选字段，不得重命名/复用旧字段名。
 */
@Serializable
data class RequirementSnapshotPayload(
    val generatedAt: String,
    val lines: List<RequirementSnapshotLine>,
) {
    companion object {
        fun from(
            groups: List<RequirementGroup>,
            unitCode: (Quantity) -> String,
            generatedAt: String,
        ): RequirementSnapshotPayload =
            RequirementSnapshotPayload(
                generatedAt = generatedAt,
                lines =
                    groups.flatMap { group ->
                        group.totals.map { total ->
                            RequirementSnapshotLine(
                                ingredientId = group.ingredientId.value,
                                amount = total.amount,
                                unitCode = unitCode(total),
                                optional = group.isOptional,
                            )
                        }
                    },
            )
    }
}
