package com.culinary.core.data.di

import com.culinary.core.common.Clock
import com.culinary.core.common.IdGenerator
import com.culinary.core.domain.repository.ArchiveCategoryRepository
import com.culinary.core.domain.repository.IngredientRepository
import com.culinary.core.domain.repository.InventoryArchiveRepository
import com.culinary.core.domain.repository.InventoryRepository
import com.culinary.core.domain.repository.RecipeRepository
import com.culinary.core.domain.repository.SauceRepository
import com.culinary.core.domain.repository.UnitRepository
import com.culinary.core.domain.transaction.TransactionRunner
import com.culinary.core.domain.usecase.inventory.BatchFreezeHistoryUseCase
import com.culinary.core.domain.usecase.inventory.BatchRecycleUseCases
import com.culinary.core.domain.usecase.inventory.BuildManualIssuePlanUseCase
import com.culinary.core.domain.usecase.inventory.BuildRecipeIssuePlanUseCase
import com.culinary.core.domain.usecase.inventory.ConfirmIssueUseCase
import com.culinary.core.domain.usecase.inventory.CreatePrintJobUseCase
import com.culinary.core.domain.usecase.inventory.FreezeBatchUseCase
import com.culinary.core.domain.usecase.inventory.GetInventoryArchiveDetailUseCase
import com.culinary.core.domain.usecase.inventory.InventoryCategoryUseCases
import com.culinary.core.domain.usecase.inventory.InventoryLedgerUseCases
import com.culinary.core.domain.usecase.inventory.InventoryRulesUseCases
import com.culinary.core.domain.usecase.inventory.LinkArchiveProductUseCase
import com.culinary.core.domain.usecase.inventory.ListArchiveFormOptionsUseCase
import com.culinary.core.domain.usecase.inventory.ListArchiveProductLinksUseCase
import com.culinary.core.domain.usecase.inventory.ListFrozenBatchesUseCase
import com.culinary.core.domain.usecase.inventory.ListInventoryArchivesUseCase
import com.culinary.core.domain.usecase.inventory.ListInventoryCategoryTreeUseCase
import com.culinary.core.domain.usecase.inventory.ListStorageLocationsUseCase
import com.culinary.core.domain.usecase.inventory.PrintJobUseCases
import com.culinary.core.domain.usecase.inventory.RecordArchiveReceiptUseCase
import com.culinary.core.domain.usecase.inventory.RecordPurchaseReceiptUseCase
import com.culinary.core.domain.usecase.inventory.SaveInventoryArchiveUseCase
import com.culinary.core.domain.usecase.inventory.SaveStorageLocationUseCase
import com.culinary.core.domain.usecase.inventory.StocktakeUseCases
import com.culinary.core.domain.usecase.inventory.TemporaryStockUseCases
import com.culinary.core.domain.usecase.inventory.TransferBatchLocationUseCase
import com.culinary.core.domain.usecase.inventory.UnfreezeBatchUseCase
import com.culinary.core.domain.usecase.inventory.UnlinkArchiveProductUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** 库存档案化（v9）用例装配；规则与写入内核全部复用既有实现。 */
@Module
@InstallIn(SingletonComponent::class)
object InventoryArchiveUseCaseModule {
    @Provides
    @Singleton
    fun provideListArchiveFormOptionsUseCase(
        archiveRepository: InventoryArchiveRepository,
        inventoryRepository: InventoryRepository,
        unitRepository: UnitRepository,
        sauceRepository: SauceRepository,
        categoryRepository: ArchiveCategoryRepository,
    ): ListArchiveFormOptionsUseCase =
        ListArchiveFormOptionsUseCase(
            archiveRepository,
            inventoryRepository,
            unitRepository,
            sauceRepository,
            categoryRepository,
        )

    @Provides
    @Singleton
    fun provideSaveInventoryArchiveUseCase(
        repository: InventoryArchiveRepository,
        categoryRepository: ArchiveCategoryRepository,
        idGenerator: IdGenerator,
        clock: Clock,
    ): SaveInventoryArchiveUseCase = SaveInventoryArchiveUseCase(repository, categoryRepository, idGenerator, clock)

    @Provides
    @Singleton
    fun provideListInventoryArchivesUseCase(
        repository: InventoryArchiveRepository,
        categoryRepository: ArchiveCategoryRepository,
    ): ListInventoryArchivesUseCase = ListInventoryArchivesUseCase(repository, categoryRepository)

    @Provides
    @Singleton
    fun provideGetInventoryArchiveDetailUseCase(
        repository: InventoryArchiveRepository,
        categoryRepository: ArchiveCategoryRepository,
        recipeRepository: RecipeRepository,
        sauceRepository: SauceRepository,
    ): GetInventoryArchiveDetailUseCase =
        GetInventoryArchiveDetailUseCase(repository, categoryRepository, recipeRepository, sauceRepository)

    @Provides
    @Singleton
    fun provideListInventoryCategoryTreeUseCase(
        repository: InventoryArchiveRepository,
        categoryRepository: ArchiveCategoryRepository,
    ): ListInventoryCategoryTreeUseCase = ListInventoryCategoryTreeUseCase(repository, categoryRepository)

    @Provides
    @Singleton
    fun provideInventoryCategoryUseCases(
        categoryRepository: ArchiveCategoryRepository,
        archiveRepository: InventoryArchiveRepository,
    ): InventoryCategoryUseCases = InventoryCategoryUseCases(categoryRepository, archiveRepository)

    @Provides
    @Singleton
    fun provideEnsureInventoryCategoriesUseCase(
        categoryRepository: ArchiveCategoryRepository,
    ): com.culinary.core.domain.usecase.inventory.EnsureInventoryCategoriesUseCase =
        com.culinary.core.domain.usecase.inventory.EnsureInventoryCategoriesUseCase(categoryRepository)

    @Provides
    @Singleton
    fun provideListStorageLocationsUseCase(
        repository: InventoryArchiveRepository,
    ): ListStorageLocationsUseCase = ListStorageLocationsUseCase(repository)

    @Provides
    @Singleton
    fun provideSaveStorageLocationUseCase(
        repository: InventoryArchiveRepository,
        idGenerator: IdGenerator,
    ): SaveStorageLocationUseCase = SaveStorageLocationUseCase(repository, idGenerator)

    @Provides
    @Singleton
    fun provideLinkArchiveProductUseCase(
        repository: InventoryArchiveRepository,
    ): LinkArchiveProductUseCase = LinkArchiveProductUseCase(repository)

    @Provides
    @Singleton
    fun provideUnlinkArchiveProductUseCase(
        repository: InventoryArchiveRepository,
    ): UnlinkArchiveProductUseCase = UnlinkArchiveProductUseCase(repository)

    @Provides
    @Singleton
    fun provideListArchiveProductLinksUseCase(
        repository: InventoryArchiveRepository,
    ): ListArchiveProductLinksUseCase = ListArchiveProductLinksUseCase(repository)

    @Provides
    @Singleton
    fun provideRecordArchiveReceiptUseCase(
        repository: InventoryArchiveRepository,
        unitRepository: UnitRepository,
        ingredientRepository: IngredientRepository,
        transactionRunner: TransactionRunner,
        idGenerator: IdGenerator,
        clock: Clock,
    ): RecordArchiveReceiptUseCase =
        RecordArchiveReceiptUseCase(
            repository,
            unitRepository,
            ingredientRepository,
            transactionRunner,
            idGenerator,
            clock,
        )

    @Provides
    @Singleton
    fun provideRecordPurchaseReceiptUseCase(
        recordReceipt: RecordArchiveReceiptUseCase,
        transactionRunner: TransactionRunner,
        idGenerator: IdGenerator,
    ): RecordPurchaseReceiptUseCase = RecordPurchaseReceiptUseCase(recordReceipt, transactionRunner, idGenerator)

    @Provides
    @Singleton
    fun provideBuildManualIssuePlanUseCase(
        repository: InventoryArchiveRepository,
        unitRepository: UnitRepository,
    ): BuildManualIssuePlanUseCase = BuildManualIssuePlanUseCase(repository, unitRepository)

    @Provides
    @Singleton
    fun provideBuildRecipeIssuePlanUseCase(
        repository: InventoryArchiveRepository,
        recipeRepository: RecipeRepository,
        sauceRepository: SauceRepository,
        ingredientRepository: IngredientRepository,
        unitRepository: UnitRepository,
    ): BuildRecipeIssuePlanUseCase =
        BuildRecipeIssuePlanUseCase(
            repository,
            recipeRepository,
            sauceRepository,
            ingredientRepository,
            unitRepository,
        )

    @Provides
    @Singleton
    fun provideConfirmIssueUseCase(
        repository: InventoryArchiveRepository,
        transactionRunner: TransactionRunner,
        idGenerator: IdGenerator,
        clock: Clock,
    ): ConfirmIssueUseCase = ConfirmIssueUseCase(repository, transactionRunner, idGenerator, clock)

    @Provides
    @Singleton
    fun provideStocktakeUseCases(
        repository: InventoryArchiveRepository,
        categoryRepository: ArchiveCategoryRepository,
        transactionRunner: TransactionRunner,
        idGenerator: IdGenerator,
        clock: Clock,
    ): StocktakeUseCases = StocktakeUseCases(repository, categoryRepository, transactionRunner, idGenerator, clock)

    @Provides
    @Singleton
    fun provideFreezeBatchUseCase(
        repository: InventoryArchiveRepository,
        transactionRunner: TransactionRunner,
        idGenerator: IdGenerator,
        clock: Clock,
    ): FreezeBatchUseCase = FreezeBatchUseCase(repository, transactionRunner, idGenerator, clock)

    @Provides
    @Singleton
    fun provideUnfreezeBatchUseCase(
        repository: InventoryArchiveRepository,
        transactionRunner: TransactionRunner,
        idGenerator: IdGenerator,
        clock: Clock,
    ): UnfreezeBatchUseCase = UnfreezeBatchUseCase(repository, transactionRunner, idGenerator, clock)

    @Provides
    @Singleton
    fun provideTransferBatchLocationUseCase(
        repository: InventoryArchiveRepository,
        transactionRunner: TransactionRunner,
        idGenerator: IdGenerator,
        clock: Clock,
    ): TransferBatchLocationUseCase =
        TransferBatchLocationUseCase(repository, transactionRunner, idGenerator, clock)

    @Provides
    @Singleton
    fun provideBatchRecycleUseCases(
        repository: InventoryArchiveRepository,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): BatchRecycleUseCases = BatchRecycleUseCases(repository, transactionRunner, clock)

    @Provides
    @Singleton
    fun provideTemporaryStockUseCases(
        repository: InventoryArchiveRepository,
        clock: Clock,
    ): TemporaryStockUseCases = TemporaryStockUseCases(repository, clock)

    @Provides
    @Singleton
    fun provideInventoryRulesUseCases(
        repository: InventoryArchiveRepository,
    ): InventoryRulesUseCases = InventoryRulesUseCases(repository)

    @Provides
    @Singleton
    fun providePrintJobUseCases(
        repository: InventoryArchiveRepository,
        clock: Clock,
    ): PrintJobUseCases = PrintJobUseCases(repository, clock)

    @Provides
    @Singleton
    fun provideInventoryLedgerUseCases(
        repository: InventoryArchiveRepository,
    ): InventoryLedgerUseCases = InventoryLedgerUseCases(repository)

    @Provides
    @Singleton
    fun provideListFrozenBatchesUseCase(
        repository: InventoryArchiveRepository,
    ): ListFrozenBatchesUseCase = ListFrozenBatchesUseCase(repository)

    @Provides
    @Singleton
    fun provideBatchFreezeHistoryUseCase(
        repository: InventoryArchiveRepository,
    ): BatchFreezeHistoryUseCase = BatchFreezeHistoryUseCase(repository)

    @Provides
    @Singleton
    fun provideCreatePrintJobUseCase(
        repository: InventoryArchiveRepository,
        clock: Clock,
    ): CreatePrintJobUseCase = CreatePrintJobUseCase(repository, clock)
}
