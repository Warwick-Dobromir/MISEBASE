package com.culinary.core.domain.usecase.inventory

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.ErrorCode
import com.culinary.core.common.IdGenerator
import com.culinary.core.domain.model.BatchLifecycleStatus
import com.culinary.core.domain.model.ComponentTarget
import com.culinary.core.domain.model.EntityStatus
import com.culinary.core.domain.model.Ingredient
import com.culinary.core.domain.model.IngredientId
import com.culinary.core.domain.model.IssueRole
import com.culinary.core.domain.model.IssueType
import com.culinary.core.domain.model.LedgerEntryType
import com.culinary.core.domain.model.Quantity
import com.culinary.core.domain.model.Recipe
import com.culinary.core.domain.model.RecipeCategory
import com.culinary.core.domain.model.RecipeComponent
import com.culinary.core.domain.model.RecipeId
import com.culinary.core.domain.model.RecipeVersion
import com.culinary.core.domain.model.RecipeVersionId
import com.culinary.core.domain.model.Sauce
import com.culinary.core.domain.model.SauceComponent
import com.culinary.core.domain.model.SauceId
import com.culinary.core.domain.model.SauceVersion
import com.culinary.core.domain.model.SauceVersionId
import com.culinary.core.domain.model.VersionStatus
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.Instant

/**
 * 三层出库（v9）：酱汁现货优先、缺口按最小制作容量取整展开、FIFO 与冻结批次、
 * 手工出库缺口，以及确认出库的整单校验与幂等。
 */
class IssuePlanningTest {
    private val repository = FakeInventoryArchiveRepository()
    private val recipes = FakeRecipeRepository()
    private val sauces = FakeSauceRepository()
    private val ingredients = FakeIngredientRepository()
    private val units = FakeUnitRepository(Gram)
    private var counter = 0
    private val ids = IdGenerator { "id-${counter++}" }

    private val buildRecipePlan =
        BuildRecipeIssuePlanUseCase(repository, recipes, sauces, ingredients, units)
    private val buildManualPlan = BuildManualIssuePlanUseCase(repository, units)
    private val confirmIssue = ConfirmIssueUseCase(repository, ImmediateRunner, ids, TestClock)

    // ---- 1. 酱汁现货足够 ----

    @Test
    fun `sauce spot stock covers the requirement without expanding the recipe`() =
        runBlocking {
            seedColdButterSauce(sauceStock = 60.0)

            val plan = buildRecipePlan(RECIPE_VERSION, 1.0).successValue()

            assertThat(plan.type).isEqualTo(IssueType.RECIPE)
            val line = plan.lines.single()
            assertThat(line.role).isEqualTo(IssueRole.SAUCE)
            assertThat(line.archiveId).isEqualTo(SAUCE_ARCHIVE)
            assertThat(line.required).isWithin(1e-9).of(50.0)
            assertThat(line.allocated).isWithin(1e-9).of(50.0)
            assertThat(line.isCovered).isTrue()
            assertThat(line.allocations.map { it.batchId }).containsExactly("sauce-batch")
            // 现货够用时不得展开酱汁配方，因此没有任何 SAUCE_GAP 行。
            assertThat(plan.lines.map { it.role }).doesNotContain(IssueRole.SAUCE_GAP)
            assertThat(plan.lines.map { it.archiveId }).doesNotContain(BUTTER_ARCHIVE)
            val gap = plan.sauceGaps.single()
            assertThat(gap.spot).isWithin(1e-9).of(50.0)
            assertThat(gap.gap).isWithin(1e-9).of(0.0)
            assertThat(gap.productionQty).isWithin(1e-9).of(0.0)
        }

    // ---- 2. 酱汁现货不足 ----

    @Test
    fun `sauce gap uses spot stock first and expands only the gap`() =
        runBlocking {
            seedColdButterSauce(sauceStock = 20.0)

            val plan = buildRecipePlan(RECIPE_VERSION, 1.0).successValue()

            assertThat(plan.lines.map { it.role })
                .containsExactly(IssueRole.SAUCE, IssueRole.SAUCE_GAP)
            val spot = plan.lines.first { it.role == IssueRole.SAUCE }
            assertThat(spot.required).isWithin(1e-9).of(20.0)
            assertThat(spot.allocated).isWithin(1e-9).of(20.0)
            val expanded = plan.lines.first { it.role == IssueRole.SAUCE_GAP }
            assertThat(expanded.archiveId).isEqualTo(BUTTER_ARCHIVE)
            assertThat(expanded.required).isWithin(1e-9).of(30.0)
            assertThat(expanded.isCovered).isTrue()
            val gap = plan.sauceGaps.single()
            assertThat(gap.required).isWithin(1e-9).of(50.0)
            assertThat(gap.spot).isWithin(1e-9).of(20.0)
            assertThat(gap.gap).isWithin(1e-9).of(30.0)
            assertThat(gap.productionQty).isWithin(1e-9).of(30.0)
            assertThat(gap.minimumBatch).isEqualTo(10.0)
        }

    @Test
    fun `a small sauce gap rounds production up to the minimum batch size`() =
        runBlocking {
            seedColdButterSauce(sauceStock = 35.0)

            val plan = buildRecipePlan(RECIPE_VERSION, 1.0).successValue()

            val gap = plan.sauceGaps.single()
            assertThat(gap.spot).isWithin(1e-9).of(35.0)
            assertThat(gap.gap).isWithin(1e-9).of(15.0)
            // 15g 缺口按 10g 最小制作容量向上取整为 20g。
            assertThat(gap.productionQty).isWithin(1e-9).of(20.0)
            assertThat(gap.roundedUp).isTrue()
            val expanded = plan.lines.single { it.role == IssueRole.SAUCE_GAP }
            assertThat(expanded.required).isWithin(1e-9).of(20.0)
            assertThat(expanded.allocated).isWithin(1e-9).of(20.0)
        }

    // ---- 3. 现货为零 ----

    @Test
    fun `zero spot expands the whole requirement without double counting stock`() =
        runBlocking {
            seedColdButterSauce(sauceStock = 0.0)

            val plan = buildRecipePlan(RECIPE_VERSION, 1.0).successValue()

            assertThat(plan.lines).hasSize(1)
            val line = plan.lines.single()
            assertThat(line.role).isEqualTo(IssueRole.SAUCE_GAP)
            assertThat(line.archiveId).isEqualTo(BUTTER_ARCHIVE)
            assertThat(line.required).isWithin(1e-9).of(50.0)
            assertThat(line.allocated).isWithin(1e-9).of(50.0)
            // 现货为 0 时只展开一次 50g，不再额外产出"0g 现货"行。
            assertThat(plan.lines.map { it.archiveId }).doesNotContain(SAUCE_ARCHIVE)
            val gap = plan.sauceGaps.single()
            assertThat(gap.spot).isWithin(1e-9).of(0.0)
            assertThat(gap.gap).isWithin(1e-9).of(50.0)
            assertThat(gap.productionQty).isWithin(1e-9).of(50.0)
        }

    @Test
    fun `a frozen sauce batch is not counted as spot stock`() =
        runBlocking {
            seedColdButterSauce(sauceStock = 60.0)
            val freeze = FreezeBatchUseCase(repository, ImmediateRunner, ids, TestClock)

            freeze("sauce-batch", "待检").successValue()
            val plan = buildRecipePlan(RECIPE_VERSION, 1.0).successValue()

            assertThat(repository.batches.getValue("sauce-batch").status)
                .isEqualTo(BatchLifecycleStatus.FROZEN)
            // 冻结批次不出库也不算现货，整份 50g 走缺口展开。
            val gap = plan.sauceGaps.single()
            assertThat(gap.spot).isWithin(1e-9).of(0.0)
            assertThat(gap.productionQty).isWithin(1e-9).of(50.0)
            assertThat(plan.lines.map { it.role }).containsExactly(IssueRole.SAUCE_GAP)
            val freezeEntry = repository.ledger.single()
            assertThat(freezeEntry.type).isEqualTo(LedgerEntryType.FREEZE)
            assertThat(freezeEntry.onHandDelta).isWithin(1e-9).of(0.0)
            assertThat(freezeEntry.onHandAfter).isWithin(1e-9).of(60.0)
            assertThat(repository.batches.getValue("sauce-batch").onHand).isWithin(1e-9).of(60.0)
        }

    // ---- 4. 直接食材缺少库存档案 ----

    @Test
    fun `a direct ingredient without an inventory archive fails the plan`() =
        runBlocking {
            seedColdButterSauce(sauceStock = 60.0)
            recipes.versions[RECIPE_VERSION] =
                recipes.versions.getValue(RECIPE_VERSION).copy(
                    components =
                        listOf(
                            RecipeComponent(1, ComponentTarget.Ingredient(MISSING), Quantity(50.0, Gram)),
                        ),
                )

            val error = buildRecipePlan(RECIPE_VERSION, 1.0).failureError()

            assertThat(error).isInstanceOf(CulinaryError.Validation::class.java)
            assertThat((error as CulinaryError.Validation).field).isEqualTo("archive")
            assertThat(repository.ledger).isEmpty()
        }

    // ---- 5. FIFO 顺序与冻结批次 ----

    @Test
    fun `fifo allocation follows received time and skips frozen batches`() =
        runBlocking {
            val archive = ingredientArchive(BUTTER_ARCHIVE, "冷黄油")
            repository.seedArchive(archive)
            repository.seedBatch(
                "batch-frozen",
                archive,
                onHand = 100.0,
                receivedAt = Instant.ofEpochMilli(1_000),
                status = BatchLifecycleStatus.FROZEN,
            )
            repository.seedBatch("batch-late", archive, onHand = 4.0, receivedAt = Instant.ofEpochMilli(3_000))
            repository.seedBatch("batch-early", archive, onHand = 4.0, receivedAt = Instant.ofEpochMilli(2_000))
            repository.seedBatch("batch-middle", archive, onHand = 4.0, receivedAt = Instant.ofEpochMilli(2_500))

            val allocations = FifoBatchAllocator(repository).allocate(archive, 10.0).successValue()

            // 冻结批次入库最早，若参与分配会排在最前；这里必须被跳过。
            assertThat(allocations.map { it.batchId })
                .containsExactly("batch-early", "batch-middle", "batch-late")
                .inOrder()
            assertThat(allocations.map { it.quantity }).containsExactly(4.0, 4.0, 2.0).inOrder()
            assertThat(allocations.map { it.batchId }).doesNotContain("batch-frozen")
            assertThat(allocations.sumOf { it.quantity }).isWithin(1e-9).of(10.0)
        }

    @Test
    fun `fifo allocator never hands out the same batch quantity twice`() =
        runBlocking {
            val archive = ingredientArchive(BUTTER_ARCHIVE, "冷黄油")
            repository.seedArchive(archive)
            repository.seedBatch("batch-1", archive, onHand = 10.0)
            val allocator = FifoBatchAllocator(repository)

            val first = allocator.allocate(archive, 6.0).successValue()
            val second = allocator.allocate(archive, 6.0).successValue()

            assertThat(first.single().quantity).isWithin(1e-9).of(6.0)
            assertThat(second.single().quantity).isWithin(1e-9).of(4.0)
        }

    // ---- 6. 手工出库计划 ----

    @Test
    fun `manual issue plan reports the uncovered shortfall`() =
        runBlocking {
            val archive = ingredientArchive(BUTTER_ARCHIVE, "冷黄油")
            repository.seedArchive(archive)
            repository.seedBatch("batch-1", archive, onHand = 10.0)

            val plan = buildManualPlan(archive.id, quantity = 15.0, unitId = Gram.id.value).successValue()

            assertThat(plan.type).isEqualTo(IssueType.MANUAL)
            val line = plan.lines.single()
            assertThat(line.role).isEqualTo(IssueRole.INGREDIENT)
            assertThat(line.required).isWithin(1e-9).of(15.0)
            assertThat(line.allocated).isWithin(1e-9).of(10.0)
            assertThat(line.shortfall).isWithin(1e-9).of(5.0)
            assertThat(line.isCovered).isFalse()
            assertThat(plan.isCovered).isFalse()
            assertThat(plan.shortfalls).hasSize(1)
        }

    @Test
    fun `manual issue plan rejects an explicitly chosen frozen batch`() =
        runBlocking {
            val archive = ingredientArchive(BUTTER_ARCHIVE, "冷黄油")
            repository.seedArchive(archive)
            repository.seedBatch(
                "batch-frozen",
                archive,
                onHand = 10.0,
                status = BatchLifecycleStatus.FROZEN,
            )

            val error =
                buildManualPlan(
                    archiveId = archive.id,
                    quantity = 5.0,
                    unitId = Gram.id.value,
                    manualBatchId = "batch-frozen",
                ).failureError()

            assertThat(error).isInstanceOf(CulinaryError.Validation::class.java)
            assertThat((error as CulinaryError.Validation).field).isEqualTo("batchId")
        }

    // ---- 7. 确认出库 ----

    @Test
    fun `confirming an order with any gap writes no ledger and no slip`() =
        runBlocking {
            val archive = ingredientArchive(BUTTER_ARCHIVE, "冷黄油")
            repository.seedArchive(archive)
            repository.seedBatch("batch-1", archive, onHand = 4.0)
            repository.seedBatch("batch-2", archive, onHand = 3.0)

            val result = confirmIssue(intent("issue-gap", archive.id, 10.0))

            assertThat(result.failureError().code).isEqualTo(ErrorCode.INV_003)
            assertThat(repository.ledger).isEmpty()
            assertThat(repository.appendLedgerAttempts).isEqualTo(0)
            assertThat(repository.slipSaveCalls).isEqualTo(0)
            assertThat(repository.printJobCalls).isEqualTo(0)
            assertThat(repository.issueSlips).isEmpty()
            assertThat(repository.batches.getValue("batch-1").onHand).isWithin(1e-9).of(4.0)
            assertThat(repository.batches.getValue("batch-2").onHand).isWithin(1e-9).of(3.0)
        }

    @Test
    fun `confirming a covered order writes one ledger row per allocated batch`() =
        runBlocking {
            seedConfirmBatches()

            val result = confirmIssue(intent("issue-ok", BUTTER_ARCHIVE, 6.0)).successValue()

            assertThat(result.idempotentReplay).isFalse()
            assertThat(result.ledgerEntries).isEqualTo(2)
            assertThat(repository.ledger).hasSize(2)
            assertThat(repository.ledger.map { it.batchId.value })
                .containsExactly("batch-early", "batch-late")
                .inOrder()
            assertThat(repository.ledger.map { it.type })
                .containsExactly(LedgerEntryType.PRODUCTION_ISSUE, LedgerEntryType.PRODUCTION_ISSUE)
            assertThat(repository.ledger.map { it.onHandDelta }).containsExactly(-4.0, -2.0).inOrder()
            assertThat(repository.ledger.first().referenceType).isEqualTo("issue_slip")
            assertThat(repository.ledger.first().referenceId).isEqualTo(result.slip.id)
            // 出库单保存一次，逐批次明细行数等于流水条数。
            assertThat(repository.slipSaveCalls).isEqualTo(1)
            assertThat(repository.slipCreates).isEqualTo(1)
            assertThat(repository.printJobCalls).isEqualTo(1)
            assertThat(repository.lastSavedSlip?.lines).hasSize(2)
            assertThat(result.slip.lines).hasSize(2)
            assertThat(result.slip.type).isEqualTo(IssueType.MANUAL)
            assertThat(result.printJob).isNotNull()
            // 余额真实扣减，冻结批次保持原样。
            assertThat(repository.batches.getValue("batch-early").onHand).isWithin(1e-9).of(0.0)
            assertThat(repository.batches.getValue("batch-late").onHand).isWithin(1e-9).of(1.0)
            assertThat(repository.batches.getValue("batch-frozen").onHand).isWithin(1e-9).of(100.0)
        }

    @Test
    fun `repeating the idempotency key replays the saved slip`() =
        runBlocking {
            seedConfirmBatches()
            val first = confirmIssue(intent("issue-ok", BUTTER_ARCHIVE, 6.0)).successValue()

            val second = confirmIssue(intent("issue-ok", BUTTER_ARCHIVE, 6.0)).successValue()

            assertThat(second.idempotentReplay).isTrue()
            assertThat(second.slip.id).isEqualTo(first.slip.id)
            assertThat(second.ledgerEntries).isEqualTo(0)
            assertThat(repository.ledger).hasSize(2)
            assertThat(repository.slipSaveCalls).isEqualTo(1)
            assertThat(repository.slipCreates).isEqualTo(1)
            assertThat(repository.printJobCalls).isEqualTo(1)
            assertThat(repository.batches.getValue("batch-early").onHand).isWithin(1e-9).of(0.0)
        }

    // ---- 夹具 ----

    private fun seedConfirmBatches() {
        val archive = ingredientArchive(BUTTER_ARCHIVE, "冷黄油")
        repository.seedArchive(archive)
        repository.seedBatch(
            "batch-frozen",
            archive,
            onHand = 100.0,
            receivedAt = Instant.ofEpochMilli(1_000),
            status = BatchLifecycleStatus.FROZEN,
        )
        repository.seedBatch("batch-early", archive, onHand = 4.0, receivedAt = Instant.ofEpochMilli(2_000))
        repository.seedBatch("batch-late", archive, onHand = 3.0, receivedAt = Instant.ofEpochMilli(3_000))
    }

    private fun intent(
        idempotencyKey: String,
        archiveId: String,
        quantity: Double,
    ): IssueIntent =
        IssueIntent(
            type = IssueType.MANUAL,
            items =
                listOf(
                    IssueIntentItem(archiveId = archiveId, role = IssueRole.INGREDIENT, quantity = quantity),
                ),
            idempotencyKey = idempotencyKey,
        )

    /** 菜谱：1 份需要 50g 冷黄油酱；酱汁版本产量 100g，最小制作容量 10g。 */
    private fun seedColdButterSauce(sauceStock: Double) {
        val butter = ingredientArchive(BUTTER_ARCHIVE, "冷黄油")
        val sauceArchive = sauceSpotArchive(SAUCE_ARCHIVE, "冷黄油酱现货", SAUCE)
        repository.seedArchive(butter)
        repository.seedArchive(sauceArchive)
        repository.seedIngredientArchive(BUTTER, butter.id)
        repository.seedBatch("butter-batch", butter, onHand = 1_000.0)
        repository.seedBatch("sauce-batch", sauceArchive, onHand = sauceStock)

        ingredients.ingredients[BUTTER] =
            Ingredient(
                id = BUTTER,
                name = "冷黄油",
                normalizedName = "冷黄油",
                defaultUnitId = Gram.id,
                status = EntityStatus.ACTIVE,
            )
        sauces.sauces[SAUCE] =
            Sauce(
                id = SAUCE,
                name = "冷黄油酱",
                normalizedName = "冷黄油酱",
                status = EntityStatus.ACTIVE,
            )
        sauces.versions[SAUCE_VERSION] =
            SauceVersion(
                id = SAUCE_VERSION,
                sauceId = SAUCE,
                versionNo = 1,
                status = VersionStatus.PUBLISHED,
                yield = Quantity(100.0, Gram),
                components =
                    listOf(
                        SauceComponent(1, ComponentTarget.Ingredient(BUTTER), Quantity(100.0, Gram)),
                    ),
                isCurrent = true,
                minRange = Quantity(10.0, Gram),
            )
        recipes.recipes[RECIPE] =
            Recipe(
                id = RECIPE,
                name = "黄油煎鱼",
                normalizedName = "黄油煎鱼",
                category = RecipeCategory.DISH,
                status = EntityStatus.ACTIVE,
            )
        recipes.versions[RECIPE_VERSION] =
            RecipeVersion(
                id = RECIPE_VERSION,
                recipeId = RECIPE,
                versionNo = 1,
                status = VersionStatus.PUBLISHED,
                yield = Quantity(100.0, Gram),
                servings = 1,
                components =
                    listOf(
                        RecipeComponent(1, ComponentTarget.Sauce(SAUCE, SAUCE_VERSION), Quantity(50.0, Gram)),
                    ),
                isCurrent = true,
            )
    }

    private companion object {
        val BUTTER = IngredientId("butter")
        val MISSING = IngredientId("ingredient-without-archive")
        const val BUTTER_ARCHIVE = "archive-butter"
        const val SAUCE_ARCHIVE = "archive-sauce"
        val SAUCE = SauceId("sauce-cold-butter")
        val SAUCE_VERSION = SauceVersionId("sauce-cold-butter-v1")
        val RECIPE = RecipeId("recipe-butter-fish")
        val RECIPE_VERSION = RecipeVersionId("recipe-butter-fish-v1")
    }
}
