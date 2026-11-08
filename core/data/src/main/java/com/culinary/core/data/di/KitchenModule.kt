package com.culinary.core.data.di

import com.culinary.core.data.kitchen.AndroidAssetKitchenSeedReader
import com.culinary.core.data.kitchen.DefaultKitchenArchiveReader
import com.culinary.core.data.kitchen.DefaultKitchenCountsReader
import com.culinary.core.data.kitchen.DefaultKitchenReferenceStore
import com.culinary.core.data.kitchen.KitchenArchiveReader
import com.culinary.core.data.kitchen.KitchenCountsReader
import com.culinary.core.data.kitchen.KitchenReferenceStore
import com.culinary.core.data.kitchen.KitchenSeedReader
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** 厨房只读数据装配：app 只依赖端口，不接触 DAO / Use Case。 */
@Module
@InstallIn(SingletonComponent::class)
abstract class KitchenModule {
    @Binds
    @Singleton
    abstract fun bindKitchenCountsReader(impl: DefaultKitchenCountsReader): KitchenCountsReader

    @Binds
    @Singleton
    abstract fun bindKitchenArchiveReader(impl: DefaultKitchenArchiveReader): KitchenArchiveReader

    @Binds
    @Singleton
    abstract fun bindKitchenReferenceStore(impl: DefaultKitchenReferenceStore): KitchenReferenceStore

    @Binds
    @Singleton
    abstract fun bindKitchenSeedReader(impl: AndroidAssetKitchenSeedReader): KitchenSeedReader
}
