package com.culinary.core.data.di

import com.culinary.core.database.CulinaryDatabase
import com.culinary.core.database.dao.InventoryBatchDao
import com.culinary.core.database.dao.InventoryLedgerDao
import com.culinary.core.database.dao.ProductDao
import com.culinary.core.database.dao.StorageLocationDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** 库存与商品所需的 DAO 装配；不向 feature 暴露实现。 */
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
    fun provideProductDao(database: CulinaryDatabase): ProductDao = database.productDao()

    @Provides
    @Singleton
    fun provideStorageLocationDao(database: CulinaryDatabase): StorageLocationDao = database.storageLocationDao()
}
