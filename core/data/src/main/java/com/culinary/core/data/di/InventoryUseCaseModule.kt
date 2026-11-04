package com.culinary.core.data.di

import com.culinary.core.common.Clock
import com.culinary.core.common.IdGenerator
import com.culinary.core.domain.repository.CalculationRecordRepository
import com.culinary.core.domain.repository.IngredientRepository
import com.culinary.core.domain.repository.InventoryRepository
import com.culinary.core.domain.repository.RecipeRepository
import com.culinary.core.domain.repository.UnitRepository
import com.culinary.core.domain.transaction.TransactionRunner
import com.culinary.core.domain.usecase.inventory.AdjustStockUseCase
import com.culinary.core.domain.usecase.inventory.BuildRequirementPlanUseCase
import com.culinary.core.domain.usecase.inventory.ConfirmRequirementDeductionUseCase
import com.culinary.core.domain.usecase.inventory.GeneratePurchaseListUseCase
import com.culinary.core.domain.usecase.inventory.GetCalculationRecordUseCase
import com.culinary.core.domain.usecase.inventory.GetInventoryItemDetailUseCase
import com.culinary.core.domain.usecase.inventory.ListCalculationRecordsUseCase
import com.culinary.core.domain.usecase.inventory.ListInventoryFormOptionsUseCase
import com.culinary.core.domain.usecase.inventory.ListInventoryLedgerUseCase
import com.culinary.core.domain.usecase.inventory.ListInventoryOverviewUseCase
import com.culinary.core.domain.usecase.inventory.RecordStockIssueUseCase
import com.culinary.core.domain.usecase.inventory.RecordStockReceiptUseCase
import com.culinary.core.domain.usecase.recipe.ExpandRecipeUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** 库存用例装配（Stage 4）。 */
@Module
@InstallIn(SingletonComponent::class)
@Suppress("TooManyFunctions") // Hilt 装配点集中在本模块，拆分不会降低复杂度
object InventoryUseCaseModule {
    @Provides
    @Singleton
    fun provideListInventoryOverviewUseCase(
        inventoryRepository: InventoryRepository,
        ingredientRepository: IngredientRepository,
    ): ListInventoryOverviewUseCase = ListInventoryOverviewUseCase(inventoryRepository, ingredientRepository)

    @Provides
    @Singleton
    fun provideGetInventoryItemDetailUseCase(
        inventoryRepository: InventoryRepository,
        ingredientRepository: IngredientRepository,
        recipeRepository: RecipeRepository,
    ): GetInventoryItemDetailUseCase =
        GetInventoryItemDetailUseCase(inventoryRepository, ingredientRepository, recipeRepository)

    @Provides
    @Singleton
    fun provideListInventoryLedgerUseCase(
        inventoryRepository: InventoryRepository,
        ingredientRepository: IngredientRepository,
    ): ListInventoryLedgerUseCase = ListInventoryLedgerUseCase(inventoryRepository, ingredientRepository)

    @Provides
    @Singleton
    fun provideListInventoryFormOptionsUseCase(
        inventoryRepository: InventoryRepository,
        ingredientRepository: IngredientRepository,
    ): ListInventoryFormOptionsUseCase = ListInventoryFormOptionsUseCase(inventoryRepository, ingredientRepository)

    @Provides
    @Singleton
    fun provideRecordStockReceiptUseCase(
        inventoryRepository: InventoryRepository,
        ingredientRepository: IngredientRepository,
        unitRepository: UnitRepository,
        transactionRunner: TransactionRunner,
        idGenerator: IdGenerator,
        clock: Clock,
    ): RecordStockReceiptUseCase =
        RecordStockReceiptUseCase(
            inventoryRepository,
            ingredientRepository,
            unitRepository,
            transactionRunner,
            idGenerator,
            clock,
        )

    @Provides
    @Singleton
    fun provideRecordStockIssueUseCase(
        inventoryRepository: InventoryRepository,
        transactionRunner: TransactionRunner,
        idGenerator: IdGenerator,
        clock: Clock,
    ): RecordStockIssueUseCase = RecordStockIssueUseCase(inventoryRepository, transactionRunner, idGenerator, clock)

    @Provides
    @Singleton
    fun provideAdjustStockUseCase(
        inventoryRepository: InventoryRepository,
        transactionRunner: TransactionRunner,
        idGenerator: IdGenerator,
        clock: Clock,
    ): AdjustStockUseCase = AdjustStockUseCase(inventoryRepository, transactionRunner, idGenerator, clock)

    @Provides
    @Singleton
    fun provideBuildRequirementPlanUseCase(
        expandRecipe: ExpandRecipeUseCase,
        recipeRepository: RecipeRepository,
        ingredientRepository: IngredientRepository,
        inventoryRepository: InventoryRepository,
        unitRepository: UnitRepository,
        idGenerator: IdGenerator,
        clock: Clock,
    ): BuildRequirementPlanUseCase =
        BuildRequirementPlanUseCase(
            expandRecipe = expandRecipe,
            recipeRepository = recipeRepository,
            ingredientRepository = ingredientRepository,
            inventoryRepository = inventoryRepository,
            unitRepository = unitRepository,
            idGenerator = idGenerator,
            clock = clock,
        )

    @Provides
    @Singleton
    fun provideGeneratePurchaseListUseCase(
        buildPlan: BuildRequirementPlanUseCase,
        calculationRepository: CalculationRecordRepository,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): GeneratePurchaseListUseCase =
        GeneratePurchaseListUseCase(buildPlan, calculationRepository, transactionRunner, clock)

    @Provides
    @Singleton
    fun provideConfirmRequirementDeductionUseCase(
        buildPlan: BuildRequirementPlanUseCase,
        inventoryRepository: InventoryRepository,
        unitRepository: UnitRepository,
        calculationRepository: CalculationRecordRepository,
        transactionRunner: TransactionRunner,
        idGenerator: IdGenerator,
        clock: Clock,
    ): ConfirmRequirementDeductionUseCase =
        ConfirmRequirementDeductionUseCase(
            buildPlan,
            inventoryRepository,
            unitRepository,
            calculationRepository,
            transactionRunner,
            idGenerator,
            clock,
        )

    @Provides
    @Singleton
    fun provideListCalculationRecordsUseCase(repository: CalculationRecordRepository): ListCalculationRecordsUseCase =
        ListCalculationRecordsUseCase(repository)

    @Provides
    @Singleton
    fun provideGetCalculationRecordUseCase(repository: CalculationRecordRepository): GetCalculationRecordUseCase =
        GetCalculationRecordUseCase(repository)
}
