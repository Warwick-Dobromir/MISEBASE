package com.culinary.core.database.converter

import androidx.room.TypeConverter
import com.culinary.core.database.model.ProcurementStatusDb
import com.culinary.core.database.model.PurchaseOrderItemStatusDb
import com.culinary.core.database.model.PurchaseOrderStatusDb
import com.culinary.core.database.model.ReceiptStatusDb

/** 采购与收货枚举转换。 */
class ProcurementConverters {
    @TypeConverter
    fun fromProcurementStatus(value: ProcurementStatusDb): String = value.name

    @TypeConverter
    fun toProcurementStatus(value: String): ProcurementStatusDb = ProcurementStatusDb.valueOf(value)

    @TypeConverter
    fun fromPurchaseOrderStatus(value: PurchaseOrderStatusDb): String = value.name

    @TypeConverter
    fun toPurchaseOrderStatus(value: String): PurchaseOrderStatusDb = PurchaseOrderStatusDb.valueOf(value)

    @TypeConverter
    fun fromPurchaseOrderItemStatus(value: PurchaseOrderItemStatusDb): String = value.name

    @TypeConverter
    fun toPurchaseOrderItemStatus(value: String): PurchaseOrderItemStatusDb = PurchaseOrderItemStatusDb.valueOf(value)

    @TypeConverter
    fun fromReceiptStatus(value: ReceiptStatusDb): String = value.name

    @TypeConverter
    fun toReceiptStatus(value: String): ReceiptStatusDb = ReceiptStatusDb.valueOf(value)
}
