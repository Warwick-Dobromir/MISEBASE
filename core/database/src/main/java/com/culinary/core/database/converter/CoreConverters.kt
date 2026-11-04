package com.culinary.core.database.converter

import androidx.room.TypeConverter
import com.culinary.core.database.model.CalculationRecordStatusDb
import com.culinary.core.database.model.ComponentTypeDb
import com.culinary.core.database.model.EntityStatusDb
import com.culinary.core.database.model.RecipeCategoryDb
import com.culinary.core.database.model.StorageLocationKindDb
import com.culinary.core.database.model.UnitDimensionDb
import com.culinary.core.database.model.VersionStatusDb

/**
 * 知识体系与位置的枚举转换（A06 §1.2）。
 *
 * 枚举以 TEXT 存储；`valueOf` 遇到未知值会抛异常而不是静默回退。
 */
class CoreConverters {
    @TypeConverter
    fun fromUnitDimension(value: UnitDimensionDb): String = value.name

    @TypeConverter
    fun toUnitDimension(value: String): UnitDimensionDb = UnitDimensionDb.valueOf(value)

    @TypeConverter
    fun fromEntityStatus(value: EntityStatusDb): String = value.name

    @TypeConverter
    fun toEntityStatus(value: String): EntityStatusDb = EntityStatusDb.valueOf(value)

    @TypeConverter
    fun fromVersionStatus(value: VersionStatusDb): String = value.name

    @TypeConverter
    fun toVersionStatus(value: String): VersionStatusDb = VersionStatusDb.valueOf(value)

    @TypeConverter
    fun fromComponentType(value: ComponentTypeDb): String = value.name

    @TypeConverter
    fun toComponentType(value: String): ComponentTypeDb = ComponentTypeDb.valueOf(value)

    @TypeConverter
    fun fromRecipeCategory(value: RecipeCategoryDb): String = value.name

    @TypeConverter
    fun toRecipeCategory(value: String): RecipeCategoryDb = RecipeCategoryDb.valueOf(value)

    @TypeConverter
    fun fromStorageLocationKind(value: StorageLocationKindDb): String = value.name

    @TypeConverter
    fun toStorageLocationKind(value: String): StorageLocationKindDb = StorageLocationKindDb.valueOf(value)

    @TypeConverter
    fun fromCalculationRecordStatus(value: CalculationRecordStatusDb): String = value.name

    @TypeConverter
    fun toCalculationRecordStatus(value: String): CalculationRecordStatusDb = CalculationRecordStatusDb.valueOf(value)
}
