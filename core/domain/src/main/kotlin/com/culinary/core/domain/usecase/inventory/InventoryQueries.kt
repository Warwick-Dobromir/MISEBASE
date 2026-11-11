package com.culinary.core.domain.usecase.inventory

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.BatchId
import com.culinary.core.domain.model.ComponentTarget
import com.culinary.core.domain.model.InventoryBatchSummary
import com.culinary.core.domain.model.InventoryFormOptions
import com.culinary.core.domain.model.InventoryItem
import com.culinary.core.domain.model.InventoryItemDetail
import com.culinary.core.domain.model.InventoryLedgerRow
import com.culinary.core.domain.model.InventoryOverviewRow
import com.culinary.core.domain.model.InventoryProductDetail
import com.culinary.core.domain.model.StorageLocation
import com.culinary.core.domain.repository.IngredientRepository
import com.culinary.core.domain.repository.InventoryRepository
import com.culinary.core.domain.repository.RecipeRepository
import com.culinary.core.domain.repository.UnitRepository
import kotlinx.coroutines.flow.first

/** 库存总览：按商品聚合批次，展示在手/预留/可用/位置/最近到期。 */
class ListInventoryOverviewUseCase(
    private val inventoryRepository: InventoryRepository,
    private val ingredientRepository: IngredientRepository,
) {
    suspend operator fun invoke(): Outcome<List<InventoryOverviewRow>> {
        val items =
            when (val result = inventoryRepository.listItems()) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        val products =
            when (val result = inventoryRepository.listProducts()) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        val locations =
            when (val result = inventoryRepository.listLocations()) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        val ingredients =
            runCatching { ingredientRepository.observeActive().first() }.getOrDefault(emptyList())
        val ingredientNames = ingredients.associate { it.id to it.name }
        val productsById = products.associateBy { it.id }
        val locationsById = locations.associateBy { it.id }

        val rows =
            items
                .groupBy { it.productId }
                .map { (productId, batches) ->
                    val product = productsById[productId]
                    val unit = product?.stockUnit ?: batches.first().stockUnit
                    val onHand = batches.sumOf { it.onHand }
                    val reserved = batches.sumOf { it.reserved }
                    InventoryOverviewRow(
                        productId = productId,
                        productName = product?.name ?: "未命名商品",
                        ingredientId =
                            product?.ingredientId ?: com.culinary.core.domain.model
                                .IngredientId(""),
                        ingredientName =
                            product?.ingredientId?.let { ingredientNames[it] } ?: "未关联食材",
                        stockUnit = unit,
                        onHand = onHand,
                        reserved = reserved,
                        available = onHand - reserved,
                        batchCount = batches.size,
                        nearestExpiry = batches.mapNotNull { it.expiresOn }.minOrNull(),
                        locationNames =
                            batches
                                .mapNotNull { locationsById[it.locationId]?.name }
                                .distinct()
                                .sorted(),
                    )
                }.sortedWith(compareBy({ it.productName }, { it.productId.value }))
        return Outcome.success(rows)
    }
}

/** 物料详情：单批次余额、位置、流水与关联菜谱。 */
class GetInventoryItemDetailUseCase(
    private val inventoryRepository: InventoryRepository,
    private val ingredientRepository: IngredientRepository,
    private val recipeRepository: RecipeRepository,
) {
    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    suspend operator fun invoke(batchId: BatchId): Outcome<InventoryItemDetail> {
        val item =
            when (val result = inventoryRepository.findItem(batchId)) {
                is Outcome.Failure -> return result
                is Outcome.Success ->
                    result.value
                        ?: return Outcome.failure(
                            CulinaryError.NotFound(entity = "inventory_batch", id = batchId.value),
                        )
            }
        val product =
            when (val result = inventoryRepository.findProduct(item.productId)) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        val location =
            when (val result = inventoryRepository.findLocation(item.locationId)) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        val ledger =
            when (val result = inventoryRepository.ledgerForBatch(batchId)) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        val ingredientName =
            product?.ingredientId?.let { ingredientId ->
                when (val result = ingredientRepository.get(ingredientId)) {
                    is Outcome.Success -> result.value.name
                    is Outcome.Failure -> "未关联食材"
                }
            } ?: "未关联食材"
        val related =
            product?.ingredientId?.let { relatedRecipesFor(recipeRepository, it) }.orEmpty()
        return Outcome.success(
            InventoryItemDetail(
                batch = summary(item, location),
                product = product,
                ingredientName = ingredientName,
                ledger = ledger.sortedByDescending { it.occurredAt },
                relatedRecipeNames = related,
            ),
        )
    }

    private fun summary(
        item: InventoryItem,
        location: StorageLocation?,
    ): InventoryBatchSummary =
        InventoryBatchSummary(
            item = item,
            batchCode = item.batchCode,
            receivedAt = item.receivedAt,
            locationName = location?.name,
        )
}

/** 商品级详情：全部批次、流水与关联菜谱，供物料详情页使用。 */
class GetInventoryProductDetailUseCase(
    private val inventoryRepository: InventoryRepository,
    private val ingredientRepository: IngredientRepository,
    private val recipeRepository: RecipeRepository,
) {
    @Suppress("ReturnCount")
    suspend operator fun invoke(productId: com.culinary.core.domain.model.ProductId): Outcome<InventoryProductDetail> {
        val product =
            when (val result = inventoryRepository.findProduct(productId)) {
                is Outcome.Failure -> return result
                is Outcome.Success ->
                    result.value
                        ?: return Outcome.failure(CulinaryError.NotFound("product", productId.value))
            }
        val items =
            when (val result = inventoryRepository.listItems()) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value.filter { it.productId == productId }
            }
        val locations =
            when (val result = inventoryRepository.listLocations()) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        val ledger =
            when (val result = inventoryRepository.ledger()) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value.filter { it.productId == productId }
            }
        val ingredientName =
            when (val result = ingredientRepository.get(product.ingredientId)) {
                is Outcome.Success -> result.value.name
                is Outcome.Failure -> "未关联食材"
            }
        val locationsById = locations.associateBy { it.id }
        return Outcome.success(
            InventoryProductDetail(
                product = product,
                ingredientName = ingredientName,
                batches =
                    items
                        .sortedWith(compareBy({ it.expiresOn ?: java.time.LocalDate.MAX }, { it.batchId.value }))
                        .map { item ->
                            InventoryBatchSummary(
                                item = item,
                                batchCode = item.batchCode,
                                receivedAt = item.receivedAt,
                                locationName = locationsById[item.locationId]?.name,
                            )
                        },
                ledger = ledger.sortedWith(compareByDescending { it.occurredAt }),
                relatedRecipeNames = relatedRecipesFor(recipeRepository, product.ingredientId),
            ),
        )
    }
}

internal suspend fun relatedRecipesFor(
    recipeRepository: RecipeRepository,
    ingredientId: com.culinary.core.domain.model.IngredientId,
): List<String> {
    val recipes = runCatching { recipeRepository.observeActive().first() }.getOrDefault(emptyList())
    return recipes
        .filter { recipe ->
            recipeRepository.listVersions(recipe.id).any { version ->
                version.components.any { component ->
                    (component.target as? ComponentTarget.Ingredient)?.id == ingredientId
                }
            }
        }.map { it.name }
        .distinct()
        .sorted()
}

/** 库存流水：按时间倒序，筛选由 UI 在已解析行上完成。 */
class ListInventoryLedgerUseCase(
    private val inventoryRepository: InventoryRepository,
    private val ingredientRepository: IngredientRepository,
) {
    suspend operator fun invoke(): Outcome<List<InventoryLedgerRow>> {
        val entries =
            when (val result = inventoryRepository.ledger()) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        val products =
            when (val result = inventoryRepository.listProducts()) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        val locations =
            when (val result = inventoryRepository.listLocations()) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        val ingredients =
            runCatching { ingredientRepository.observeActive().first() }.getOrDefault(emptyList())
        val productsById = products.associateBy { it.id }
        val ingredientNames = ingredients.associate { it.id to it.name }
        val locationsById = locations.associateBy { it.id }
        return Outcome.success(
            entries
                .sortedWith(
                    compareByDescending<com.culinary.core.domain.model.InventoryLedgerEntry> { it.occurredAt }
                        .thenByDescending { it.id.value },
                ).map { entry ->
                    val product = entry.productId?.let(productsById::get)
                    InventoryLedgerRow(
                        entry = entry,
                        productName = product?.name ?: entry.archiveId?.let { "库存档案" } ?: "未命名商品",
                        ingredientName =
                            product?.ingredientId?.let { ingredientNames[it] } ?: "未关联食材",
                        locationName = locationsById[entry.locationId]?.name ?: "未命名位置",
                    )
                },
        )
    }
}

/** 表单选项：商品、位置与食材；只读，不创建任何记录。 */
class ListInventoryFormOptionsUseCase(
    private val inventoryRepository: InventoryRepository,
    private val ingredientRepository: IngredientRepository,
    private val unitRepository: UnitRepository,
) {
    suspend operator fun invoke(): Outcome<InventoryFormOptions> {
        val products =
            when (val result = inventoryRepository.listProducts()) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        val locations =
            when (val result = inventoryRepository.listLocations()) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        val items =
            when (val result = inventoryRepository.listItems()) {
                is Outcome.Failure -> return result
                is Outcome.Success -> result.value
            }
        val ingredients =
            runCatching { ingredientRepository.observeActive().first() }.getOrDefault(emptyList())
        val locationsById = locations.associateBy { it.id }
        return Outcome.success(
            InventoryFormOptions(
                products = products.sortedBy { it.name },
                locations = locations.sortedBy { it.name },
                ingredients = ingredients.sortedBy { it.name },
                units = unitRepository.all(),
                batches =
                    items
                        .map { item ->
                            InventoryBatchSummary(
                                item = item,
                                batchCode = item.batchCode,
                                receivedAt = item.receivedAt,
                                locationName = locationsById[item.locationId]?.name,
                            )
                        }.sortedWith(
                            compareBy(
                                { it.item.expiresOn ?: java.time.LocalDate.MAX },
                                { it.item.batchId.value },
                            ),
                        ),
            ),
        )
    }
}
