package com.culinary.core.database.converter

import androidx.room.TypeConverter
import com.culinary.core.database.model.BackupKindDb
import com.culinary.core.database.model.BackupStatusDb
import com.culinary.core.database.model.ChangeActionDb
import com.culinary.core.database.model.ImageOwnerTypeDb
import com.culinary.core.database.model.ImageRoleDb

/** 横切表枚举转换。 */
class CrossCuttingConverters {
    @TypeConverter
    fun fromImageOwnerType(value: ImageOwnerTypeDb): String = value.name

    @TypeConverter
    fun toImageOwnerType(value: String): ImageOwnerTypeDb = ImageOwnerTypeDb.valueOf(value)

    @TypeConverter
    fun fromImageRole(value: ImageRoleDb): String = value.name

    @TypeConverter
    fun toImageRole(value: String): ImageRoleDb = ImageRoleDb.valueOf(value)

    @TypeConverter
    fun fromChangeAction(value: ChangeActionDb): String = value.name

    @TypeConverter
    fun toChangeAction(value: String): ChangeActionDb = ChangeActionDb.valueOf(value)

    @TypeConverter
    fun fromBackupKind(value: BackupKindDb): String = value.name

    @TypeConverter
    fun toBackupKind(value: String): BackupKindDb = BackupKindDb.valueOf(value)

    @TypeConverter
    fun fromBackupStatus(value: BackupStatusDb): String = value.name

    @TypeConverter
    fun toBackupStatus(value: String): BackupStatusDb = BackupStatusDb.valueOf(value)
}
