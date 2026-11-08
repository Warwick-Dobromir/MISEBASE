package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.culinary.core.database.entity.ImageAssetEntity

/** 图片元数据读写。 */
@Dao
interface ImageAssetDao {
    @Insert
    suspend fun insert(entity: ImageAssetEntity)

    @Query("SELECT * FROM image_asset WHERE id = :id")
    suspend fun findById(id: String): ImageAssetEntity?

    @Query(
        "SELECT * FROM image_asset WHERE owner_type = :ownerType AND owner_id = :ownerId " +
            "AND deleted_at IS NULL ORDER BY sort_order",
    )
    suspend fun listByOwner(
        ownerType: String,
        ownerId: String,
    ): List<ImageAssetEntity>

    @Query("UPDATE image_asset SET deleted_at = :at WHERE id = :id")
    suspend fun softDelete(
        id: String,
        at: Long,
    )

    @Query("UPDATE image_asset SET step_no = :stepNo WHERE id = :id")
    suspend fun updateStepNo(
        id: String,
        stepNo: Int?,
    )

    @Query("SELECT COUNT(*) FROM image_asset")
    suspend fun count(): Int
}
