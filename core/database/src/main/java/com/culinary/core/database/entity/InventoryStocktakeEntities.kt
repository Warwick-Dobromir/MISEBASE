package com.culinary.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.culinary.core.database.model.StocktakeScopeDb
import com.culinary.core.database.model.StocktakeStatusDb

/**
 * 盘点单（v9）。
 *
 * 草稿阶段只保存报告，不改库存；确认后由用例统一调整库存并写流水。
 */
@Entity(
    tableName = "inventory_stocktake",
    indices = [
        Index(value = ["status"]),
        Index(value = ["created_at"]),
    ],
)
data class InventoryStocktakeEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "scope_type")
    val scopeType: StocktakeScopeDb = StocktakeScopeDb.ALL,
    @ColumnInfo(name = "scope_ref")
    val scopeRef: String? = null,
    @ColumnInfo(name = "scope_label")
    val scopeLabel: String? = null,
    @ColumnInfo(name = "status")
    val status: StocktakeStatusDb = StocktakeStatusDb.DRAFT,
    @ColumnInfo(name = "note")
    val note: String? = null,
    @ColumnInfo(name = "line_count")
    val lineCount: Int = 0,
    @ColumnInfo(name = "diff_count")
    val diffCount: Int = 0,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "confirmed_at")
    val confirmedAt: Long? = null,
)

/** 盘点明细（v9）：账面 vs 实盘，确认后差额转为调整流水。 */
@Entity(
    tableName = "inventory_stocktake_line",
    foreignKeys = [
        ForeignKey(
            entity = InventoryStocktakeEntity::class,
            parentColumns = ["id"],
            childColumns = ["stocktake_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["stocktake_id", "line_no"]),
        Index(value = ["batch_id"]),
        Index(value = ["archive_id"]),
    ],
)
data class InventoryStocktakeLineEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "stocktake_id")
    val stocktakeId: String,
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
    @ColumnInfo(name = "location_name")
    val locationName: String? = null,
    @ColumnInfo(name = "book_qty")
    val bookQty: Double,
    @ColumnInfo(name = "counted_qty")
    val countedQty: Double,
    @ColumnInfo(name = "unit_id")
    val unitId: String,
    @ColumnInfo(name = "unit_symbol")
    val unitSymbol: String,
    @ColumnInfo(name = "frozen")
    val frozen: Boolean = false,
)
