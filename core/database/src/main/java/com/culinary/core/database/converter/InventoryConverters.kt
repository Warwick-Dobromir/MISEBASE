package com.culinary.core.database.converter

import androidx.room.TypeConverter
import com.culinary.core.database.model.BatchStatusDb
import com.culinary.core.database.model.LedgerTypeDb

/** 库存枚举转换：批次状态与流水类型。 */
class InventoryConverters {
    @TypeConverter
    fun fromBatchStatus(value: BatchStatusDb): String = value.name

    @TypeConverter
    fun toBatchStatus(value: String): BatchStatusDb = BatchStatusDb.valueOf(value)

    @TypeConverter
    fun fromLedgerType(value: LedgerTypeDb): String = value.name

    @TypeConverter
    fun toLedgerType(value: String): LedgerTypeDb = LedgerTypeDb.valueOf(value)
}
