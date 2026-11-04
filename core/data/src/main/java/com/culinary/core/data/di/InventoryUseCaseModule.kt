package com.culinary.core.data.di

import com.culinary.core.common.Clock
import com.culinary.core.common.IdGenerator
import com.culinary.core.domain.repository.IngredientRepository
import com.culinary.core.domain.repository.InventoryRepository
import com.culinary.core.domain.repository.RecipeRepository
import com.culinary.core.domain.repository.UnitRepository
import com.culinary.core.domain.transaction.TransactionRunner
import com.culinary.core.domain.usecase.inventory.AdjustStockUseCase
import com.culinary.core.domain.usecase.inventory.GetInventoryItemDetailUseCase
import com.culinary.core.domain.usecase.inventory.ListInventoryFormOptionsUseCase
import com.culinary.core.domain.usecase.inventory.ListInventoryLedgerUseCase
import com.culinary.core.domain.usecase.inventory.ListInventoryOverviewUseCase
import com.culinary.core.domain.usecase.inventory.RecordStockIssueUseCase
import com.culinary.core.domain.usecase.inventory.RecordStockReceiptUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** 库存用例装配（Stage 4）。 */
@Module
@InstallIn(SingletonComponent::class)
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
}
