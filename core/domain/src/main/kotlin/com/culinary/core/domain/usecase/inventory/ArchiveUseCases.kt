package com.culinary.core.domain.usecase.inventory

import com.culinary.core.common.Clock
import com.culinary.core.common.CulinaryError
import com.culinary.core.common.IdGenerator
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.ArchiveCategory
import com.culinary.core.domain.model.ArchiveKind
import com.culinary.core.domain.model.ArchiveLibrary
import com.culinary.core.domain.model.ArchiveListFilter
import com.culinary.core.domain.model.ArchiveProductLink
import com.culinary.core.domain.model.ComponentTarget
import com.culinary.core.domain.model.EntityStatus
import com.culinary.core.domain.model.InventoryArchive
import com.culinary.core.domain.model.InventoryArchiveDetail
import com.culinary.core.domain.model.InventoryArchiveRow
import com.culinary.core.domain.model.InventoryBatchRecord
import com.culinary.core.domain.model.InventoryCategoryNode
import com.culinary.core.domain.model.InventoryHomeSummary
import com.culinary.core.domain.model.LocationId
import com.culinary.core.domain.model.Product
import com.culinary.core.domain.model.SauceId
import com.culinary.core.domain.model.StockStatus
import com.culinary.core.domain.model.StorageLocation
import com.culinary.core.domain.model.Unit
import com.culinary.core.domain.repository.ArchiveCategoryRepository
import com.culinary.core.domain.repository.InventoryArchiveRepository
import com.culinary.core.domain.repository.RecipeRepository
import com.culinary.core.domain.repository.SauceRepository
import com.culinary.core.domain.repository.UnitRepository
import com.culinary.core.domain.rule.common.NameText
import kotlinx.coroutines.flow.first

/** 档案列表结果：筛选后的行 + 全量状态统计。 */
data class InventoryArchiveList(
    val rows: List<InventoryArchiveRow>,
    val summary: InventoryHomeSummary,
)

/** 档案表单选项：单位、库位、采购商品、酱汁。 */
data class ArchiveFormOptions(
    val units: List<Unit> = emptyList(),
    val locations: List<StorageLocation> = emptyList(),
    val products: List<Product> = emptyList(),
    val sauces: List<SauceOption> = emptyList(),
    val categories: List<ArchiveCategory> = emptyList(),
)

/** 可绑定为酱汁现货档案的酱汁。 */
data class SauceOption(
    val id: String,
    val name: String,
)

/** 读取档案表单所需的全部参照数据；只读。 */
class ListArchiveFormOptionsUseCase(
    private val archiveRepository: InventoryArchiveRepository,
    private val inventoryRepository: com.culinary.core.domain.repository.InventoryRepository,
    private val unitRepository: UnitRepository,
    private val sauceRepository: SauceRepository,
    private val categoryRepository: ArchiveCategoryRepository,
) {
    suspend operator fun invoke(): Outcome<ArchiveFormOptions> {
        val locations =
            when (val result = archiveRepository.listLocations()) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        val categories =
            when (val result = categoryRepository.listByLibrary(ArchiveLibrary.INVENTORY)) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        val products =
            when (val result = inventoryRepository.listProducts()) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        val sauces =
            runCatching {
                sauceRepository.observeActive().first().map { SauceOption(it.id.value, it.name) }
            }.getOrDefault(emptyList())
        return Outcome.success(
            ArchiveFormOptions(
                units = unitRepository.all(),
                locations = locations,
                products = products,
                sauces = sauces,
                categories = categories,
            ),
        )
    }
}

/**
 * 新建或编辑库存档案（v9）。
 *
 * 档案编辑不改变任何库存数量：数量只由批次与流水决定。
 * 图片、备注、阈值等字段允许为空；编辑不会清除既有图片。
 */
class SaveInventoryArchiveUseCase(
    private val repository: InventoryArchiveRepository,
    private val categoryRepository: ArchiveCategoryRepository,
    private val idGenerator: IdGenerator,
    private val clock: Clock,
) {
    suspend operator fun invoke(archive: InventoryArchive): Outcome<String> {
        val name = archive.name.trim()
        if (name.isEmpty()) return invalid("name", "must not be blank")
        if (name.length > 40) return invalid("name", "must be at most 40 characters")
        val minimum = archive.minimumStock
        val target = archive.targetStock
        if (minimum != null && (!minimum.isFinite() || minimum < 0.0)) {
            return invalid("minimumStock", "must be a finite non negative number")
        }
        if (target != null && (!target.isFinite() || target < 0.0)) {
            return invalid("targetStock", "must be a finite non negative number")
        }
        if (minimum != null && target != null && target < minimum) {
            return invalid("targetStock", "target must not be below minimum")
        }
        if (archive.kind == ArchiveKind.SAUCE && archive.sauceId.isNullOrBlank()) {
            return invalid("sauceId", "sauce archive must reference a sauce")
        }
        val categoryId = archive.categoryId
        if (categoryId != null) {
            val category = categoryRepository.get(categoryId)
            if (category == null || category.library != ArchiveLibrary.INVENTORY) {
                return invalid("categoryId", "category does not belong to inventory")
            }
        }
        val now = clock.now()
        val existing =
            repository.findArchive(archive.id).let { result ->
                when (result) {
                    is Outcome.Failure -> return result
                    is Outcome.Success -> result.value
                }
            }
        val id = if (existing == null) idGenerator.newId() else existing.id
        val normalized = NameText.normalize(name)
        val duplicate =
            when (val result = repository.listArchives()) {
                is Outcome.Failure -> return result
                is Outcome.Success ->
                    result.value.firstOrNull {
                        it.id != id && NameText.normalize(it.name) == normalized
                    }
            }
        if (duplicate != null) {
            return Outcome.failure(
                CulinaryError.Validation(field = "name", rule = "archive name already exists"),
            )
        }
        val saved =
            InventoryArchive(
                id = id,
                name = name,
                englishName = archive.englishName?.trim()?.takeIf { it.isNotEmpty() },
                categoryId = categoryId,
                kind = archive.kind,
                sauceId = archive.sauceId?.takeIf { it.isNotBlank() },
                stockUnit = archive.stockUnit,
                minimumStock = minimum,
                targetStock = target,
                imageUri = archive.imageUri?.takeIf { it.isNotBlank() },
                storageMethod = archive.storageMethod?.trim()?.takeIf { it.isNotEmpty() },
                storageTemperature = archive.storageTemperature?.trim()?.takeIf { it.isNotEmpty() },
                defaultLocationId = archive.defaultLocationId,
                description = archive.description?.trim()?.takeIf { it.isNotEmpty() },
                status = EntityStatus.ACTIVE,
            )
        return repository.saveArchive(saved).also {
            if (it is Outcome.Success && existing == null) {
                // 新档案默认库位保持用户选择；未选择时由入库时回落默认库位。
                Unit
            }
        }
    }

    private fun invalid(
        field: String,
        rule: String,
    ): Outcome<Nothing> = Outcome.failure(CulinaryError.Validation(field = field, rule = rule))
}

/** 档案列表：支持搜索、状态筛选与分类子树筛选；汇总数字始终基于全部档案。 */
class ListInventoryArchivesUseCase(
    private val repository: InventoryArchiveRepository,
    private val categoryRepository: ArchiveCategoryRepository,
) {
    suspend operator fun invoke(
        filter: ArchiveListFilter = ArchiveListFilter.ALL,
        query: String = "",
        categoryId: String? = null,
    ): Outcome<InventoryArchiveList> {
        val archives =
            when (val result = repository.listArchives()) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        val batches =
            when (val result = repository.listLiveBatches()) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        val categories =
            when (val result = categoryRepository.listByLibrary(ArchiveLibrary.INVENTORY)) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        val categoryNames = categories.associate { it.id to it.name }
        val subtree = categorySubtreeIds(categories, categoryId)
        val rows =
            archives.map { archive ->
                row(
                    archive = archive,
                    batches = batches.filter { it.archiveId == archive.id },
                    categoryName = archive.categoryId?.let(categoryNames::get),
                )
            }
        val summary =
            InventoryHomeSummary(
                total = rows.size,
                normal = rows.count { it.status == StockStatus.NORMAL },
                low = rows.count { it.status == StockStatus.LOW },
                out = rows.count { it.status == StockStatus.OUT },
            )
        val keyword = query.trim()
        val filtered =
            rows
                .filter { row ->
                    val statusMatches =
                        when (filter) {
                            ArchiveListFilter.ALL -> true
                            ArchiveListFilter.NORMAL -> row.status == StockStatus.NORMAL
                            ArchiveListFilter.LOW -> row.status == StockStatus.LOW
                            ArchiveListFilter.OUT -> row.status == StockStatus.OUT
                        }
                    val queryMatches =
                        keyword.isEmpty() ||
                            row.archive.name.contains(keyword, ignoreCase = true) ||
                            row.archive.englishName?.contains(keyword, ignoreCase = true) == true
                    val categoryMatches = subtree == null || row.archive.categoryId in subtree
                    statusMatches && queryMatches && categoryMatches
                }.sortedWith(compareBy({ it.archive.name }, { it.archive.id }))
        return Outcome.success(InventoryArchiveList(rows = filtered, summary = summary))
    }

    fun row(
        archive: InventoryArchive,
        batches: List<InventoryBatchRecord>,
        categoryName: String?,
    ): InventoryArchiveRow {
        val live = batches.filter { it.isLive }
        return InventoryArchiveRow(
            archive = archive,
            onHand = live.sumOf { it.onHand },
            reserved = live.sumOf { it.reserved },
            batchCount = live.size,
            nearestExpiry = live.mapNotNull { it.expiresOn }.minOrNull(),
            locationNames = live.mapNotNull { it.locationName }.distinct().sorted(),
            categoryName = categoryName,
        )
    }

    private fun categorySubtreeIds(
        categories: List<ArchiveCategory>,
        rootId: String?,
    ): Set<String>? {
        if (rootId == null) return null
        val children = categories.groupBy { it.parentId }
        val result = mutableSetOf<String>()
        val queue = ArrayDeque(listOf(rootId))
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (!result.add(current)) continue
            children[current].orEmpty().forEach { queue.addLast(it.id) }
        }
        return result
    }
}

/** 档案详情：档案、批次、流水、采购商品与关联菜谱。 */
class GetInventoryArchiveDetailUseCase(
    private val repository: InventoryArchiveRepository,
    private val categoryRepository: ArchiveCategoryRepository,
    private val recipeRepository: RecipeRepository,
    private val sauceRepository: SauceRepository,
) {
    @Suppress("ReturnCount", "LongMethod")
    suspend operator fun invoke(archiveId: String): Outcome<InventoryArchiveDetail> {
        val archive =
            when (val result = repository.findArchive(archiveId)) {
                is Outcome.Failure -> return result
                is Outcome.Success ->
                    result.value
                        ?: return Outcome.failure(CulinaryError.NotFound("inventory_archive", archiveId))
            }
        val batches =
            when (val result = repository.listBatches(archiveId)) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        val ledger =
            when (val result = repository.ledgerForArchive(archiveId)) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        val links =
            when (val result = repository.archiveLinks(archiveId)) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        val categories =
            when (val result = categoryRepository.listByLibrary(ArchiveLibrary.INVENTORY)) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        val categoryPath =
            archive.categoryId?.let { id ->
                categories.firstOrNull { it.id == id }?.pathLabel
            }
        val row =
            ListInventoryArchivesUseCase(repository, categoryRepository).row(
                archive = archive,
                batches = batches,
                categoryName = archive.categoryId?.let { id -> categories.firstOrNull { it.id == id }?.name },
            )
        val ingredientIds = links.map { it.product.ingredientId }.distinct()
        val related = relatedRecipes(ingredientIds)
        val sauceName =
            archive.sauceId?.let { sauceId ->
                when (val result = sauceRepository.get(SauceId(sauceId))) {
                    is Outcome.Success -> result.value.name
                    is Outcome.Failure -> null
                }
            }
        return Outcome.success(
            InventoryArchiveDetail(
                archive = archive,
                row = row,
                categoryPath = categoryPath,
                links = links,
                batches = batches.sortedWith(compareBy({ it.fifoKey }, { it.id })),
                ledger = ledger.sortedByDescending { it.occurredAt },
                relatedRecipeNames = related,
                sauceName = sauceName,
            ),
        )
    }

    private suspend fun relatedRecipes(
        ingredientIds: List<com.culinary.core.domain.model.IngredientId>,
    ): List<String> {
        if (ingredientIds.isEmpty()) return emptyList()
        val recipes = runCatching { recipeRepository.observeActive().first() }.getOrDefault(emptyList())
        return recipes
            .filter { recipe ->
                recipeRepository.listVersions(recipe.id).any { version ->
                    version.components.any { component ->
                        val target = component.target
                        target is ComponentTarget.Ingredient && target.id in ingredientIds
                    }
                }
            }.map { it.name }
            .distinct()
            .sorted()
    }
}

/** 库存分类目录：树形节点 + 直接/含后代档案数；库存分类归属独立（library = INVENTORY）。 */
class ListInventoryCategoryTreeUseCase(
    private val repository: InventoryArchiveRepository,
    private val categoryRepository: ArchiveCategoryRepository,
) {
    suspend operator fun invoke(parentId: String? = null): Outcome<List<InventoryCategoryNode>> {
        val categories =
            when (val result = categoryRepository.listByLibrary(ArchiveLibrary.INVENTORY)) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        val archives =
            when (val result = repository.listArchives()) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        val byParent = categories.groupBy { it.parentId }
        val directCounts = archives.groupingBy { it.categoryId }.eachCount()
        return Outcome.success(
            byParent[parentId].orEmpty().sortedWith(compareBy({ it.sortOrder }, { it.name })).map { category ->
                val descendants = descendantsOf(categories, category.id)
                InventoryCategoryNode(
                    category = category,
                    childCount = byParent[category.id].orEmpty().size,
                    directArchiveCount = directCounts[category.id] ?: 0,
                    totalArchiveCount = descendants.sumOf { directCounts[it] ?: 0 },
                )
            },
        )
    }

    private fun descendantsOf(
        categories: List<ArchiveCategory>,
        rootId: String,
    ): List<String> {
        val byParent = categories.groupBy { it.parentId }
        val result = mutableListOf<String>()
        val queue = ArrayDeque(listOf(rootId))
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (result.contains(current)) continue
            result += current
            byParent[current].orEmpty().forEach { queue.addLast(it.id) }
        }
        return result
    }
}

/** 读取全部库位（库位管理页与表单共用）。 */
class ListStorageLocationsUseCase(
    private val repository: InventoryArchiveRepository,
) {
    suspend operator fun invoke(): Outcome<List<StorageLocation>> = repository.listLocations()
}

/** 新建或重命名库位；`kind` 与 `sortOrder` 只影响展示。 */
class SaveStorageLocationUseCase(
    private val repository: InventoryArchiveRepository,
    private val idGenerator: IdGenerator,
) {
    suspend operator fun invoke(
        id: String?,
        name: String,
        parentId: LocationId?,
        kind: String?,
        sortOrder: Int,
    ): Outcome<LocationId> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return Outcome.failure(CulinaryError.Validation(field = "location.name", rule = "must not be blank"))
        }
        if (trimmed.length > 30) {
            return Outcome.failure(
                CulinaryError.Validation(field = "location.name", rule = "must be at most 30 characters"),
            )
        }
        val location =
            StorageLocation(
                id = LocationId(id ?: idGenerator.newId()),
                name = trimmed,
                parentId = parentId,
                status = EntityStatus.ACTIVE,
            )
        return repository.saveLocation(location, kind, sortOrder)
    }
}

/** 绑定采购商品到档案（一个商品只归属一个档案）。 */
class LinkArchiveProductUseCase(
    private val repository: InventoryArchiveRepository,
) {
    suspend operator fun invoke(
        archiveId: String,
        productId: String,
        conversionRatio: Double,
        isDefault: Boolean,
    ): Outcome<kotlin.Unit> {
        if (!conversionRatio.isFinite() || conversionRatio <= 0.0) {
            return Outcome.failure(
                CulinaryError.Validation(field = "conversionRatio", rule = "must be finite and > 0"),
            )
        }
        return repository.linkProduct(archiveId, productId, conversionRatio, isDefault)
    }
}

/** 解绑采购商品。 */
class UnlinkArchiveProductUseCase(
    private val repository: InventoryArchiveRepository,
) {
    suspend operator fun invoke(
        archiveId: String,
        productId: String,
    ): Outcome<kotlin.Unit> = repository.unlinkProduct(archiveId, productId)
}

/** 档案内可用的采购商品（详情页展示）。 */
class ListArchiveProductLinksUseCase(
    private val repository: InventoryArchiveRepository,
) {
    suspend operator fun invoke(archiveId: String): Outcome<List<ArchiveProductLink>> =
        repository.archiveLinks(archiveId)
}
