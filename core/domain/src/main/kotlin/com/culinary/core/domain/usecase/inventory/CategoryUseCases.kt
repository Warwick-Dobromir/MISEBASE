package com.culinary.core.domain.usecase.inventory

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.ArchiveCategory
import com.culinary.core.domain.model.ArchiveLibrary
import com.culinary.core.domain.repository.ArchiveCategoryRepository
import com.culinary.core.domain.repository.InventoryArchiveRepository

/** 库存分类的新增/重命名/移动/排序/删除（v9）；归属固定为 INVENTORY 库。 */
class InventoryCategoryUseCases(
    private val categoryRepository: ArchiveCategoryRepository,
    private val archiveRepository: InventoryArchiveRepository,
) {
    suspend fun create(
        parentId: String?,
        name: String,
    ): Outcome<ArchiveCategory> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return invalid("name", "must not be blank")
        if (trimmed.length > 20) return invalid("name", "must be at most 20 characters")
        if (parentId != null) {
            val parent = categoryRepository.get(parentId)
            if (parent == null || parent.library != ArchiveLibrary.INVENTORY) {
                return invalid("parentId", "parent category does not belong to inventory")
            }
        }
        return categoryRepository.createCategory(
            library = ArchiveLibrary.INVENTORY,
            parentId = parentId,
            name = trimmed,
        )
    }

    suspend fun rename(
        id: String,
        name: String,
    ): Outcome<ArchiveCategory> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return invalid("name", "must not be blank")
        if (trimmed.length > 20) return invalid("name", "must be at most 20 characters")
        return categoryRepository.renameCategory(id, trimmed)
    }

    suspend fun move(
        id: String,
        newParentId: String?,
    ): Outcome<ArchiveCategory> =
        when (val result = categoryRepository.moveCategory(id, newParentId)) {
            is Outcome.Failure -> result
            is Outcome.Success -> result
        }

    suspend fun reorder(orderedIds: List<String>): Outcome<Unit> =
        if (orderedIds.isEmpty()) {
            Outcome.success(Unit)
        } else {
            categoryRepository.reorderCategories(orderedIds)
        }

    /** 只有"无子分类 + 无档案归属"的空分类才允许删除。 */
    suspend fun delete(id: String): Outcome<Unit> {
        val categories =
            when (val result = categoryRepository.listByLibrary(ArchiveLibrary.INVENTORY)) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        if (categories.any { it.parentId == id }) {
            return invalid("id", "category still has child categories")
        }
        val archives =
            when (val result = archiveRepository.listArchives()) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        if (archives.any { it.categoryId == id }) {
            return invalid("id", "category still contains inventory archives")
        }
        return categoryRepository.deleteCategory(id)
    }

    private fun invalid(
        field: String,
        rule: String,
    ): Outcome<Nothing> = Outcome.failure(CulinaryError.Validation(field = field, rule = rule))
}

/**
 * 库存分类初始化的六类默认值（可按任务书 §3.4 修改，不是系统锁死分类）。
 *
 * 只在 INVENTORY 库当前没有任何分类时写入一次，之后完全由用户维护。
 */
class EnsureInventoryCategoriesUseCase(
    private val categoryRepository: ArchiveCategoryRepository,
) {
    suspend operator fun invoke(): Outcome<List<ArchiveCategory>> {
        val existing =
            when (val result = categoryRepository.listByLibrary(ArchiveLibrary.INVENTORY)) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        if (existing.isNotEmpty()) return Outcome.success(existing)
        val created = mutableListOf<ArchiveCategory>()
        for ((index, name) in DEFAULT_CATEGORIES.withIndex()) {
            when (
                val result =
                    categoryRepository.createCategory(
                        library = ArchiveLibrary.INVENTORY,
                        parentId = null,
                        name = name,
                        englishName = null,
                    )
            ) {
                is Outcome.Failure -> return result
                is Outcome.Success -> created += result.value
            }
        }
        return Outcome.success(created)
    }

    private companion object {
        val DEFAULT_CATEGORIES =
            listOf(
                "肉类与禽类",
                "海鲜",
                "乳制品与蛋类",
                "蔬菜与水果",
                "干货与调味品",
                "其他",
            )
    }
}
