package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.culinary.core.database.entity.InventoryReservationEntity

/** 库存预留读写；释放与消耗是状态变更，不删行。 */
@Dao
interface InventoryReservationDao {
    @Insert
    suspend fun insert(entity: InventoryReservationEntity)

    @Query("SELECT * FROM inventory_reservation WHERE plan_id = :planId")
    suspend fun listByPlan(planId: String): List<InventoryReservationEntity>

    @Query("UPDATE inventory_reservation SET status = 'RELEASED', released_at = :at WHERE id = :id")
    suspend fun release(
        id: String,
        at: Long,
    )

    @Query("SELECT COUNT(*) FROM inventory_reservation")
    suspend fun count(): Int
}
