package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.culinary.core.database.entity.KitchenReferenceContentEntity

@Dao
interface KitchenReferenceContentDao {
    @Insert
    suspend fun insertAll(entities: List<KitchenReferenceContentEntity>)

    @Query(
        "SELECT * FROM kitchen_reference_content WHERE reference_id = :referenceId " +
            "ORDER BY section, sort_order, id",
    )
    suspend fun listByReference(referenceId: String): List<KitchenReferenceContentEntity>

    @Query("DELETE FROM kitchen_reference_content WHERE reference_id = :referenceId")
    suspend fun deleteByReference(referenceId: String)
}
