package com.culinary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.culinary.core.database.model.BatchStatusDb

/**
 * 库存批次（A06 §6.2）：余额是物化值，流水是事实；批次不硬删，耗尽置 DEPLETED。
 * `quantity_reserved <= quantity_on_hand` 等约束由触发器兜底。
 */
@Entity(
    tableName = "inventory_batch",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = StorageLocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["location_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = UnitEntity::class,
            parentColumns = ["id"],
            childColumns = ["stock_unit_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["product_id", "status"]),
        Index(value = ["expiry_date"]),
        Index(value = ["location_id"]),
        Index(value = ["stock_unit_id"]),
    ],
)
data class InventoryBatchEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    /** 来源采购商品（可空：酱汁现货等档案不来自采购商品）。 */
    @ColumnInfo(name = "product_id")
    val productId: String? = null,
    /** 归属库存档案（v9）；迁移前历史批次由 v8→v9 回填，来源商品仍保留在 product_id。 */
    @ColumnInfo(name = "archive_id")
    val archiveId: String? = null,
    @ColumnInfo(name = "location_id")
    val locationId: String,
    @ColumnInfo(name = "batch_code")
    val batchCode: String? = null,
    @ColumnInfo(name = "received_at")
    val receivedAt: Long,
    @ColumnInfo(name = "production_date")
    val productionDate: String? = null,
    @ColumnInfo(name = "expiry_date")
    val expiryDate: String? = null,
    @ColumnInfo(name = "quantity_on_hand")
    val quantityOnHand: Double = 0.0,
    @ColumnInfo(name = "quantity_reserved")
    val quantityReserved: Double = 0.0,
    @ColumnInfo(name = "stock_unit_id")
    val stockUnitId: String,
    @ColumnInfo(name = "unit_cost_minor")
    val unitCostMinor: Long? = null,
    @ColumnInfo(name = "currency_code")
    val currencyCode: String = "CNY",
    @ColumnInfo(name = "source_type")
    val sourceType: String? = null,
    @ColumnInfo(name = "source_id")
    val sourceId: String? = null,
    @ColumnInfo(name = "status")
    val status: BatchStatusDb = BatchStatusDb.ACTIVE,
    /** 冻结时间与原因（v9）；解冻后清空，历史留在 freeze_record。 */
    @ColumnInfo(name = "frozen_at")
    val frozenAt: Long? = null,
    @ColumnInfo(name = "frozen_reason")
    val frozenReason: String? = null,
    /** 归零进回收站的时间（v9）；30 天后由启动维护清理。 */
    @ColumnInfo(name = "recycled_at")
    val recycledAt: Long? = null,
    /** 临时库存标记（v9）：与正式库存明确区分，超过阈值只提醒不删除。 */
    @ColumnInfo(name = "is_temporary", defaultValue = "0")
    val isTemporary: Boolean = false,
    @ColumnInfo(name = "temporary_since")
    val temporarySince: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "revision")
    val revision: Int = 1,
)
