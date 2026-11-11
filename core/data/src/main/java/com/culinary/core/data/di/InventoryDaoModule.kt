package com.culinary.core.data.di

import com.culinary.core.database.CulinaryDatabase
import com.culinary.core.database.dao.InventoryArchiveDao
import com.culinary.core.database.dao.InventoryArchiveProductDao
import com.culinary.core.database.dao.InventoryBatchDao
import com.culinary.core.database.dao.InventoryIssueDao
import com.culinary.core.database.dao.InventoryLedgerDao
import com.culinary.core.database.dao.InventoryLifecycleDao
import com.culinary.core.database.dao.InventoryStocktakeDao
import com.culinary.core.database.dao.ProductDao
import com.culinary.core.database.dao.StorageLocationDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** 库存与商品所需的 DAO 装配；不向 feature 暴露实现。 */
@Suppress("TooManyFunctions") // 库存 v9 的 DAO 唯一装配点；拆模块不会减少 DAO 数量
@Module
@InstallIn(SingletonComponent::class)
object InventoryDaoModule {
    @Provides
    @Singleton
    fun provideInventoryBatchDao(database: CulinaryDatabase): InventoryBatchDao = database.inventoryBatchDao()

    @Provides
    @Singleton
    fun provideInventoryLedgerDao(database: CulinaryDatabase): InventoryLedgerDao = database.inventoryLedgerDao()

    @Provides
    @Singleton
    fun provideInventoryArchiveDao(database: CulinaryDatabase): InventoryArchiveDao = database.inventoryArchiveDao()

    @Provides
    @Singleton
    fun provideInventoryArchiveProductDao(database: CulinaryDatabase): InventoryArchiveProductDao =
        database.inventoryArchiveProductDao()

    @Provides
    @Singleton
    fun provideInventoryLifecycleDao(database: CulinaryDatabase): InventoryLifecycleDao =
        database.inventoryLifecycleDao()

    @Provides
    @Singleton
    fun provideInventoryIssueDao(database: CulinaryDatabase): InventoryIssueDao = database.inventoryIssueDao()

    @Provides
    @Singleton
    fun provideInventoryStocktakeDao(database: CulinaryDatabase): InventoryStocktakeDao =
        database.inventoryStocktakeDao()

    @Provides
    @Singleton
    fun provideProductDao(database: CulinaryDatabase): ProductDao = database.productDao()

    @Provides
    @Singleton
    fun provideStorageLocationDao(database: CulinaryDatabase): StorageLocationDao = database.storageLocationDao()
}
