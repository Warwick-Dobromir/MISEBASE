package com.culinary.core.data.fake

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.DomainDetail
import com.culinary.core.common.ErrorCode
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.BatchId
import com.culinary.core.domain.model.CalculationRecord
import com.culinary.core.domain.model.CalculationRecordId
import com.culinary.core.domain.model.EntityStatus
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.InventoryItem
import com.culinary.core.domain.model.InventoryLedgerEntry
import com.culinary.core.domain.model.LocationId
import com.culinary.core.domain.model.NewInventoryBatch
import com.culinary.core.domain.model.Product
import com.culinary.core.domain.model.ProductId
import com.culinary.core.domain.model.Recipe
import com.culinary.core.domain.model.RecipeVersion
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.model.Sauce
import com.culinary.core.domain.model.SauceId
import com.culinary.core.domain.model.SauceVersion
import com.culinary.core.domain.model.SauceVersionId
import com.culinary.core.domain.model.StorageLocation
import com.culinary.core.domain.model.VersionStatus
import com.culinary.core.domain.repository.CalculationRecordRepository
import com.culinary.core.domain.repository.InventoryRepository
import com.culinary.core.domain.repository.RecipeRepository
import com.culinary.core.domain.repository.SauceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * 内存 fake：供契约测试与用例测试使用，不连接 Room、不修改 Schema。
 *
 * 行为约定与正式实现一致：菜谱"当前版本"唯一；库存流水只追加；
 * 计算记录只接受已确认记录且不覆盖、不重复 payload。
 */
class InMemoryRecipeRepository : RecipeRepository {
    private val recipes = mutableListOf<Recipe>()
    private val versions = mutableMapOf<RecipeVersionId, RecipeVersion>()
    private val current = mutableMapOf<String, RecipeVersionId>()

    override fun observeActive(): Flow<List<Recipe>> = flowOf(recipes.filter { it.status == EntityStatus.ACTIVE })

    override suspend fun get(id: com.culinary.core.domain.model.RecipeId): Outcome<Recipe> =
        recipes.firstOrNull { it.id == id }?.let { Outcome.success(it) }
            ?: Outcome.failure(CulinaryError.NotFound(entity = "recipe", id = id.value))

    override suspend fun getVersion(id: RecipeVersionId): RecipeVersion? = versions[id]

    override suspend fun getCurrentVersion(recipeId: com.culinary.core.domain.model.RecipeId): RecipeVersion? =
        current[recipeId.value]?.let { versions[it] }

    override suspend fun listVersions(recipeId: com.culinary.core.domain.model.RecipeId): List<RecipeVersion> =
        versions.values.filter { it.recipeId == recipeId }.sortedByDescending { it.versionNo }

    override suspend fun nextVersionNo(recipeId: com.culinary.core.domain.model.RecipeId): Int =
        (versions.values.filter { it.recipeId == recipeId }.maxOfOrNull { it.versionNo } ?: 0) + 1

    override suspend fun save(recipe: Recipe): Outcome<com.culinary.core.domain.model.RecipeId> {
        recipes.removeAll { it.id == recipe.id }
        recipes += recipe
        return Outcome.success(recipe.id)
    }

    override suspend fun saveDraft(version: RecipeVersion): RecipeVersionId {
        versions[version.id] = version
        return version.id
    }

    override suspend fun savePublished(version: RecipeVersion): Outcome<RecipeVersionId> {
        versions[version.id] = version
        current[version.recipeId.value] = version.id
        return Outcome.success(version.id)
    }

    fun currentVersionId(recipeId: String): RecipeVersionId? = current[recipeId]

    fun addRecipe(recipe: Recipe) {
        recipes += recipe
    }
}

class InMemoryInventoryRepository(
    items: List<InventoryItem> = emptyList(),
) : InventoryRepository {
    val items = items.associateBy { it.batchId }.toMutableMap()
    val ledger = mutableListOf<InventoryLedgerEntry>()
    val products = mutableListOf<Product>()
    val locations = mutableListOf<StorageLocation>()
    private var batchCounter = 0

    override suspend fun listItems(): Outcome<List<InventoryItem>> = Outcome.success(items.values.toList())

    override suspend fun findItem(batchId: BatchId): Outcome<InventoryItem?> = Outcome.success(items[batchId])

    override suspend fun findByIdempotencyKey(key: String): Outcome<InventoryLedgerEntry?> =
        Outcome.success(ledger.firstOrNull { it.idempotencyKey == key })

    override suspend fun appendIfAbsent(entry: InventoryLedgerEntry): Outcome<Boolean> {
        if (ledger.any { it.idempotencyKey == entry.idempotencyKey }) return Outcome.success(false)
        val item = items.getValue(entry.batchId)
        val newOnHand = item.onHand + entry.onHandDelta
        val newReserved = item.reserved + entry.reservedDelta
        if (newOnHand < 0.0 || newReserved < 0.0 || newReserved > newOnHand) {
            return Outcome.failure(
                CulinaryError.Domain(
                    detail =
                        DomainDetail.InsufficientStock(
                            productId = entry.productId.value,
                            requested = -entry.onHandDelta,
                            available = item.available,
                        ),
                    code = ErrorCode.INV_003,
                ),
            )
        }
        val applied = entry.copy(onHandAfter = newOnHand, reservedAfter = newReserved)
        items[entry.batchId] = item.copy(onHand = newOnHand, reserved = newReserved)
        ledger += applied
        return Outcome.success(true)
    }

    override suspend fun ledger(): Outcome<List<InventoryLedgerEntry>> = Outcome.success(ledger.toList())

    override suspend fun listProducts(): Outcome<List<Product>> = Outcome.success(products.toList())

    override suspend fun findProduct(id: ProductId): Outcome<Product?> =
        Outcome.success(products.firstOrNull { it.id == id })

    override suspend fun saveProduct(product: Product): Outcome<ProductId> {
        products.removeAll { it.id == product.id }
        products += product
        return Outcome.success(product.id)
    }

    override suspend fun findProductForIngredient(ingredientId: IngredientId): Outcome<Product?> =
        Outcome.success(products.firstOrNull { it.ingredientId == ingredientId && it.isActive })

    override suspend fun listLocations(): Outcome<List<StorageLocation>> = Outcome.success(locations.toList())

    override suspend fun findLocation(id: LocationId): Outcome<StorageLocation?> =
        Outcome.success(locations.firstOrNull { it.id == id })

    override suspend fun saveLocation(location: StorageLocation): Outcome<LocationId> {
        locations.removeAll { it.id == location.id }
        locations += location
        return Outcome.success(location.id)
    }

    override suspend fun ensureDefaultLocation(): Outcome<StorageLocation> {
        val existing = locations.firstOrNull { it.isActive }
        if (existing != null) return Outcome.success(existing)
        val created = StorageLocation(LocationId("location-default"), "默认储藏")
        locations += created
        return Outcome.success(created)
    }

    override suspend fun createBatch(batch: NewInventoryBatch): Outcome<BatchId> {
        val id = BatchId("batch-${batchCounter++}")
        items[id] =
            InventoryItem(
                batchId = id,
                productId = batch.productId,
                locationId = batch.locationId,
                stockUnit = batch.stockUnit,
                onHand = 0.0,
                expiresOn = batch.expiresOn,
                batchCode = batch.batchCode,
                receivedAt = batch.receivedAt,
            )
        return Outcome.success(id)
    }

    override suspend fun ledgerForBatch(batchId: BatchId): Outcome<List<InventoryLedgerEntry>> =
        Outcome.success(ledger.filter { it.batchId == batchId })
}

class InMemoryCalculationRecordRepository : CalculationRecordRepository {
    val records = mutableListOf<CalculationRecord>()

    override suspend fun saveConfirmed(record: CalculationRecord): Outcome<CalculationRecord> {
        if (!record.isConfirmed) {
            return Outcome.failure(
                CulinaryError.Domain(
                    detail = DomainDetail.InvalidState("calculation_record", "CONFIRMED", record.status.name),
                    code = ErrorCode.INV_003,
                ),
            )
        }
        records.firstOrNull { it.id == record.id }?.let {
            return Outcome.failure(CulinaryError.Conflict(entity = "calculation_record"))
        }
        records.firstOrNull { it.payloadHash == record.payloadHash }?.let {
            return Outcome.success(it)
        }
        records += record
        return Outcome.success(record)
    }

    override suspend fun find(id: CalculationRecordId): Outcome<CalculationRecord?> =
        Outcome.success(records.firstOrNull { it.id == id })

    override suspend fun findByRecipeVersion(recipeVersionId: RecipeVersionId): Outcome<List<CalculationRecord>> =
        Outcome.success(records.filter { it.recipeVersionId == recipeVersionId })

    override suspend fun findByPayloadHash(payloadHash: String): Outcome<CalculationRecord?> =
        Outcome.success(records.firstOrNull { it.payloadHash == payloadHash })
}

class InMemorySauceRepository : SauceRepository {
    private val sauces = mutableListOf<Sauce>()
    private val versions = mutableMapOf<SauceVersionId, SauceVersion>()
    private val current = mutableMapOf<String, SauceVersionId>()

    override fun observeActive(): Flow<List<Sauce>> = flowOf(sauces.filter { it.isActive })

    override suspend fun get(id: SauceId): Outcome<Sauce> =
        sauces.firstOrNull { it.id == id }?.let { Outcome.success(it) }
            ?: Outcome.failure(CulinaryError.NotFound(entity = "sauce", id = id.value))

    override suspend fun getVersion(id: SauceVersionId): Outcome<SauceVersion> =
        versions[id]?.let { Outcome.success(it) }
            ?: Outcome.failure(CulinaryError.NotFound(entity = "sauce_version", id = id.value))

    override suspend fun listVersions(sauceId: SauceId): List<SauceVersion> =
        versions.values.filter { it.sauceId == sauceId }.sortedByDescending { it.versionNo }

    override suspend fun findCurrentVersion(sauceId: SauceId): SauceVersion? =
        current[sauceId.value]?.let { versions[it] }

    override suspend fun nextVersionNo(sauceId: SauceId): Int =
        (versions.values.filter { it.sauceId == sauceId }.maxOfOrNull { it.versionNo } ?: 0) + 1

    override suspend fun save(sauce: Sauce): Outcome<SauceId> {
        sauces.removeAll { it.id == sauce.id }
        sauces += sauce
        return Outcome.success(sauce.id)
    }

    override suspend fun saveDraft(version: SauceVersion): Outcome<SauceVersionId> {
        versions[version.id] = version
        return Outcome.success(version.id)
    }

    override suspend fun savePublished(version: SauceVersion): Outcome<SauceVersionId> {
        val published = version.copy(status = VersionStatus.PUBLISHED, isCurrent = true)
        versions.values.filter { it.sauceId == version.sauceId }.forEach { existing ->
            versions[existing.id] = existing.copy(isCurrent = false)
        }
        versions[published.id] = published
        current[version.sauceId.value] = published.id
        return Outcome.success(published.id)
    }

    fun addSauce(sauce: Sauce) {
        sauces += sauce
    }
}
