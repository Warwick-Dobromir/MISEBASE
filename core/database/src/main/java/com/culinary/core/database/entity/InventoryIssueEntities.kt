package com.culinary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.culinary.core.database.model.IssueSlipStatusDb
import com.culinary.core.database.model.IssueSlipTypeDb
import com.culinary.core.database.model.PrintJobKindDb
import com.culinary.core.database.model.PrintJobStatusDb

/**
 * 出库单（v9）：一次出库操作一张单，屏幕与热敏打印共用同一份数据。
 *
 * `idempotency_key` 唯一，防止撕单动画或重复点击造成二次扣减；
 * 只有整单校验通过的出库才会落行，失败单不写库。
 */
@Entity(
    tableName = "inventory_issue_slip",
    indices = [
        Index(value = ["slip_no"], unique = true),
        Index(value = ["idempotency_key"], unique = true),
        Index(value = ["occurred_at"]),
        Index(value = ["recipe_version_id"]),
    ],
)
data class InventoryIssueSlipEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "slip_no")
    val slipNo: String,
    @ColumnInfo(name = "type")
    val type: IssueSlipTypeDb,
    @ColumnInfo(name = "status")
    val status: IssueSlipStatusDb = IssueSlipStatusDb.CONFIRMED,
    @ColumnInfo(name = "recipe_version_id")
    val recipeVersionId: String? = null,
    @ColumnInfo(name = "recipe_name")
    val recipeName: String? = null,
    @ColumnInfo(name = "total_lines")
    val totalLines: Int = 0,
    @ColumnInfo(name = "idempotency_key")
    val idempotencyKey: String,
    @ColumnInfo(name = "note")
    val note: String? = null,
    @ColumnInfo(name = "operator")
    val operator: String? = null,
    @ColumnInfo(name = "occurred_at")
    val occurredAt: Long,
)

/** 出库单明细（v9）：逐批次记录实际扣减的数量与库位，打印单据此展开。 */
@Entity(
    tableName = "inventory_issue_slip_line",
    foreignKeys = [
        ForeignKey(
            entity = InventoryIssueSlipEntity::class,
            parentColumns = ["id"],
            childColumns = ["slip_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["slip_id", "line_no"]),
        Index(value = ["batch_id"]),
        Index(value = ["archive_id"]),
    ],
)
data class InventoryIssueSlipLineEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "slip_id")
    val slipId: String,
    @ColumnInfo(name = "line_no")
    val lineNo: Int,
    @ColumnInfo(name = "archive_id")
    val archiveId: String,
    @ColumnInfo(name = "archive_name")
    val archiveName: String,
    @ColumnInfo(name = "batch_id")
    val batchId: String,
    @ColumnInfo(name = "batch_code")
    val batchCode: String? = null,
    @ColumnInfo(name = "location_id")
    val locationId: String,
    @ColumnInfo(name = "location_name")
    val locationName: String? = null,
    @ColumnInfo(name = "quantity")
    val quantity: Double,
    @ColumnInfo(name = "unit_id")
    val unitId: String,
    @ColumnInfo(name = "unit_symbol")
    val unitSymbol: String,
    @ColumnInfo(name = "role")
    val role: String = ROLE_INGREDIENT,
) {
    companion object {
        /** 第一层：菜谱直接使用的食材。 */
        const val ROLE_INGREDIENT = "INGREDIENT"

        /** 第二层：已制作入库的酱汁现货。 */
        const val ROLE_SAUCE = "SAUCE"

        /** 第三层：为补足酱汁缺口而展开的基础食材。 */
        const val ROLE_SAUCE_GAP = "SAUCE_GAP"
    }
}

/** 打印任务（v9）：业务单据与打印设备解耦；打印失败不影响库存结果。 */
@Entity(
    tableName = "inventory_print_job",
    indices = [
        Index(value = ["ref_type", "ref_id"]),
        Index(value = ["status"]),
        Index(value = ["kind"]),
    ],
)
data class InventoryPrintJobEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "kind")
    val kind: PrintJobKindDb,
    @ColumnInfo(name = "ref_type")
    val refType: String,
    @ColumnInfo(name = "ref_id")
    val refId: String,
    @ColumnInfo(name = "status")
    val status: PrintJobStatusDb,
    @ColumnInfo(name = "attempts")
    val attempts: Int = 0,
    @ColumnInfo(name = "reprint_count")
    val reprintCount: Int = 0,
    @ColumnInfo(name = "last_error")
    val lastError: String? = null,
    @ColumnInfo(name = "paper_width_mm")
    val paperWidthMm: Int = 80,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "printed_at")
    val printedAt: Long? = null,
)
