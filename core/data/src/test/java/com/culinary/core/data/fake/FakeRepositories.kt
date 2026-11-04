package com.culinary.core.data.fake

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.DomainDetail
import com.culinary.core.common.ErrorCode
import com.culinary.core.common.Outcome
import com.culinary.core.domain.model.CalculationRecord
import com.culinary.core.domain.model.CalculationRecordId
import com.culinary.core.domain.model.InventoryItem
import com.culinary.core.domain.model.InventoryLedgerEntry
import com.culinary.core.domain.model.Recipe
import com.culinary.core.domain.model.RecipeVersion
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.model.Sauce
import com.culinary.core.domain.model.SauceVersion
import com.culinary.core.domain.model.SauceVersionId
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

    override fun observeActive(): Flow<List<Recipe>> = flowOf(recipes.filter { it.status == com.culinary.core.domain.model.EntityStatus.ACTIVE })

    override suspend fun getVersion(id: RecipeVersionId): RecipeVersion? = versions[id]

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

    override suspend fun listItems(): Outcome<List<InventoryItem>> = Outcome.success(items.values.toList())

    override suspend fun findItem(batchId: com.culinary.core.domain.model.BatchId): Outcome<InventoryItem?> =
        Outcome.success(items[batchId])

    override suspend fun findByIdempotencyKey(key: String): Outcome<InventoryLedgerEntry?> =
        Outcome.success(ledger.firstOrNull { it.idempotencyKey == key })

    override suspend fun appendIfAbsent(entry: InventoryLedgerEntry): Outcome<Boolean> {
        if (ledger.any { it.idempotencyKey == entry.idempotencyKey }) return Outcome.success(false)
        val item = items.getValue(entry.batchId)
        val newOnHand = item.onHand + entry.onHandDelta
        if (newOnHand < 0.0) {
            return Outcome.failure(
                CulinaryError.Domain(
                    detail =
                        DomainDetail.InsufficientStock(
                            productId = entry.productId.value,
                            requested = -entry.onHandDelta,
                            available = item.onHand,
                        ),
                    code = ErrorCode.INV_003,
                ),
            )
        }
        items[entry.batchId] = item.copy(onHand = newOnHand, reserved = item.reserved + entry.reservedDelta)
        ledger += entry
        return Outcome.success(true)
    }

    override suspend fun ledger(): Outcome<List<InventoryLedgerEntry>> = Outcome.success(ledger.toList())
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

    override fun observeActive(): Flow<List<Sauce>> = flowOf(sauces.filter { it.isActive })

    override suspend fun getVersion(id: SauceVersionId): Outcome<SauceVersion> =
        versions[id]?.let { Outcome.success(it) }
            ?: Outcome.failure(CulinaryError.NotFound(entity = "sauce_version", id = id.value))

    override suspend fun saveDraft(version: SauceVersion): Outcome<SauceVersionId> {
        versions[version.id] = version
        return Outcome.success(version.id)
    }

    fun addSauce(sauce: Sauce) {
        sauces += sauce
    }
}
