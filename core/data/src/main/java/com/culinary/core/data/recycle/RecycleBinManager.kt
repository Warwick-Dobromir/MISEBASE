package com.culinary.core.data.recycle

import androidx.room.withTransaction
import com.culinary.core.database.CulinaryDatabase
import com.culinary.core.database.entity.ArchiveCategoryEntity
import com.culinary.core.database.entity.IngredientEntity
import com.culinary.core.database.entity.KitchenReferenceEntity
import com.culinary.core.database.entity.RecipeEntity
import com.culinary.core.database.entity.SauceEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/** Central registry for currently archived records. New deletable types must be registered here. */
data class RecycleBinItem(
    val id: String,
    val type: String,
    val title: String,
    val detail: String,
    val deletedAt: Long,
)

@Singleton
class RecycleBinManager @Inject constructor(private val database: CulinaryDatabase) {
    fun observeItems(): Flow<List<RecycleBinItem>> =
        combine(
            database.ingredientDao().observeArchived(),
            database.sauceDao().observeArchived(),
            database.recipeDao().observeArchived(),
            database.kitchenReferenceDao().observeArchived(),
            database.archiveCategoryDao().observeArchived(),
        ) { ingredients, sauces, recipes, references, categories ->
            buildList {
                ingredients.forEach { add(it.toRecycleItem("食材")) }
                sauces.forEach { add(it.toRecycleItem("酱汁")) }
                recipes.forEach { add(it.toRecycleItem("菜谱")) }
                references.forEach { add(it.toRecycleItem(it.kind.name)) }
                categories.forEach { add(it.toRecycleItem()) }
            }.sortedByDescending { it.deletedAt }
        }

    suspend fun restore(item: RecycleBinItem) {
        database.withTransaction {
            val now = System.currentTimeMillis()
            when (item.type) {
                "食材" -> database.ingredientDao().restore(item.id, now)
                "酱汁" -> database.sauceDao().restore(item.id, now)
                "菜谱" -> database.recipeDao().restore(item.id, now)
                "分类目录" -> database.archiveCategoryDao().restore(item.id, now)
                else -> database.kitchenReferenceDao().restore(item.id, now)
            }
        }
    }

    suspend fun permanentlyDelete(item: RecycleBinItem) {
        database.withTransaction {
            when (item.type) {
                "食材" -> {
                    database.archiveCategoryAssignmentDao().deleteByRecord("INGREDIENT", item.id)
                    database.ingredientDao().deleteById(item.id)
                }
                "酱汁" -> {
                    database.archiveCategoryAssignmentDao().deleteByRecord("SAUCE", item.id)
                    database.sauceDao().deleteById(item.id)
                }
                "菜谱" -> {
                    database.archiveCategoryAssignmentDao().deleteByRecord("RECIPE", item.id)
                    database.recipeDao().deleteById(item.id)
                }
                "分类目录" -> {
                    require(database.archiveCategoryDao().listAll().none { it.parentId == item.id }) {
                        "请先删除该目录下的子目录"
                    }
                    database.archiveCategoryAssignmentDao().deleteByCategory(item.id)
                    database.archiveCategoryDao().deleteById(item.id)
                }
                else -> {
                    listOf("TECHNIQUE", "PRODUCTION_STANDARD", "STORAGE_STANDARD").forEach { kind ->
                        database.archiveCategoryAssignmentDao().deleteByRecord(kind, item.id)
                    }
                    database.kitchenReferenceDao().deleteById(item.id)
                }
            }
        }
    }

    private fun IngredientEntity.toRecycleItem(type: String) =
        RecycleBinItem(id, type, name, category.orEmpty(), deletedAt ?: updatedAt)

    private fun SauceEntity.toRecycleItem(type: String) =
        RecycleBinItem(id, type, name, family.orEmpty(), deletedAt ?: updatedAt)

    private fun RecipeEntity.toRecycleItem(type: String) =
        RecycleBinItem(id, type, name, category.name, deletedAt ?: updatedAt)

    private fun KitchenReferenceEntity.toRecycleItem(type: String) =
        RecycleBinItem(id, type, name, category.orEmpty(), deletedAt ?: updatedAt)

    private fun ArchiveCategoryEntity.toRecycleItem() =
        RecycleBinItem(id, "分类目录", name, "$library · ${parentId ?: "顶级目录"}", updatedAt)
}
