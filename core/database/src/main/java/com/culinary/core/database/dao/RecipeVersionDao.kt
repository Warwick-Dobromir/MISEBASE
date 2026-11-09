package com.culinary.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.culinary.core.database.entity.RecipeVersionEntity
import com.culinary.core.database.model.VersionStatusDb

/** 菜谱版本读写。 */
@Dao
interface RecipeVersionDao {
    @Insert
    suspend fun insert(entity: RecipeVersionEntity)

    @Query("SELECT * FROM recipe_version WHERE id = :id")
    suspend fun findById(id: String): RecipeVersionEntity?

    @Query("SELECT * FROM recipe_version WHERE recipe_id = :recipeId AND is_current = 1")
    suspend fun findCurrent(recipeId: String): RecipeVersionEntity?

    @Query("SELECT * FROM recipe_version WHERE recipe_id = :recipeId ORDER BY version_no DESC")
    suspend fun listByRecipe(recipeId: String): List<RecipeVersionEntity>

    @Query("SELECT COALESCE(MAX(version_no), 0) FROM recipe_version WHERE recipe_id = :recipeId")
    suspend fun maxVersionNo(recipeId: String): Int

    @Query("UPDATE recipe_version SET status = 'PUBLISHED', published_at = :at WHERE id = :id")
    suspend fun publish(
        id: String,
        at: Long,
    )

    @Query("UPDATE recipe_version SET status = :status, published_at = :at WHERE id = :id")
    suspend fun setStatus(
        id: String,
        status: VersionStatusDb,
        at: Long?,
    )

    @Query("UPDATE recipe_version SET is_current = :current WHERE id = :id")
    suspend fun setCurrent(
        id: String,
        current: Boolean,
    )

    @Query("UPDATE recipe_version SET is_current = 0 WHERE recipe_id = :recipeId AND is_current = 1")
    suspend fun clearCurrent(recipeId: String)

    @Query("UPDATE recipe_version SET yield_qty = :qty WHERE id = :id")
    suspend fun updateYield(
        id: String,
        qty: Double,
    )

    /** 只更新未发布草稿的内容；状态与当前指针由发布流程单独维护。 */
    @Query(
        "UPDATE recipe_version SET " +
            "yield_qty = :yieldQty, yield_unit_id = :yieldUnitId, servings = :servings, " +
            "instructions_md = :instructionsMd, steps_json = :stepsJson WHERE id = :id",
    )
    suspend fun updateDraft(
        id: String,
        yieldQty: Double,
        yieldUnitId: String,
        servings: Int,
        instructionsMd: String?,
        stepsJson: String?,
    )

    @Query("SELECT COUNT(*) FROM recipe_version")
    suspend fun count(): Int
}
