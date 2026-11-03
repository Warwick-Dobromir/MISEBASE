package com.culinary.core.database.converter

import androidx.room.TypeConverter
import com.culinary.core.database.model.ConversionStatusDb
import com.culinary.core.database.model.PlanItemStatusDb
import com.culinary.core.database.model.PlanStatusDb
import com.culinary.core.database.model.ReservationStatusDb

/** 计划与预留枚举转换。 */
class PlanConverters {
    @TypeConverter
    fun fromPlanStatus(value: PlanStatusDb): String = value.name

    @TypeConverter
    fun toPlanStatus(value: String): PlanStatusDb = PlanStatusDb.valueOf(value)

    @TypeConverter
    fun fromPlanItemStatus(value: PlanItemStatusDb): String = value.name

    @TypeConverter
    fun toPlanItemStatus(value: String): PlanItemStatusDb = PlanItemStatusDb.valueOf(value)

    @TypeConverter
    fun fromConversionStatus(value: ConversionStatusDb): String = value.name

    @TypeConverter
    fun toConversionStatus(value: String): ConversionStatusDb = ConversionStatusDb.valueOf(value)

    @TypeConverter
    fun fromReservationStatus(value: ReservationStatusDb): String = value.name

    @TypeConverter
    fun toReservationStatus(value: String): ReservationStatusDb = ReservationStatusDb.valueOf(value)
}
