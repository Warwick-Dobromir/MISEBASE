package com.culinary.core.database.converter

import androidx.room.TypeConverter
import com.culinary.core.database.model.ArchiveKindDb
import com.culinary.core.database.model.BatchStatusDb
import com.culinary.core.database.model.FreezeActionDb
import com.culinary.core.database.model.IssueSlipStatusDb
import com.culinary.core.database.model.IssueSlipTypeDb
import com.culinary.core.database.model.LedgerTypeDb
import com.culinary.core.database.model.PrintJobKindDb
import com.culinary.core.database.model.PrintJobStatusDb
import com.culinary.core.database.model.StocktakeScopeDb
import com.culinary.core.database.model.StocktakeStatusDb

/** 库存枚举转换：批次状态、流水类型、档案类型、出库/打印/盘点枚举。 */
class InventoryConverters {
    @TypeConverter
    fun fromBatchStatus(value: BatchStatusDb): String = value.name

    @TypeConverter
    fun toBatchStatus(value: String): BatchStatusDb = BatchStatusDb.valueOf(value)

    @TypeConverter
    fun fromLedgerType(value: LedgerTypeDb): String = value.name

    @TypeConverter
    fun toLedgerType(value: String): LedgerTypeDb = LedgerTypeDb.valueOf(value)

    @TypeConverter
    fun fromArchiveKind(value: ArchiveKindDb): String = value.name

    @TypeConverter
    fun toArchiveKind(value: String): ArchiveKindDb = ArchiveKindDb.valueOf(value)

    @TypeConverter
    fun fromIssueSlipType(value: IssueSlipTypeDb): String = value.name

    @TypeConverter
    fun toIssueSlipType(value: String): IssueSlipTypeDb = IssueSlipTypeDb.valueOf(value)

    @TypeConverter
    fun fromIssueSlipStatus(value: IssueSlipStatusDb): String = value.name

    @TypeConverter
    fun toIssueSlipStatus(value: String): IssueSlipStatusDb = IssueSlipStatusDb.valueOf(value)

    @TypeConverter
    fun fromPrintJobKind(value: PrintJobKindDb): String = value.name

    @TypeConverter
    fun toPrintJobKind(value: String): PrintJobKindDb = PrintJobKindDb.valueOf(value)

    @TypeConverter
    fun fromPrintJobStatus(value: PrintJobStatusDb): String = value.name

    @TypeConverter
    fun toPrintJobStatus(value: String): PrintJobStatusDb = PrintJobStatusDb.valueOf(value)

    @TypeConverter
    fun fromFreezeAction(value: FreezeActionDb): String = value.name

    @TypeConverter
    fun toFreezeAction(value: String): FreezeActionDb = FreezeActionDb.valueOf(value)

    @TypeConverter
    fun fromStocktakeScope(value: StocktakeScopeDb): String = value.name

    @TypeConverter
    fun toStocktakeScope(value: String): StocktakeScopeDb = StocktakeScopeDb.valueOf(value)

    @TypeConverter
    fun fromStocktakeStatus(value: StocktakeStatusDb): String = value.name

    @TypeConverter
    fun toStocktakeStatus(value: String): StocktakeStatusDb = StocktakeStatusDb.valueOf(value)
}
