package com.culinary.core.data.di

import com.culinary.core.data.repository.CalculationRecordRepositoryImpl
import com.culinary.core.data.repository.IngredientRepositoryImpl
import com.culinary.core.data.repository.InventoryRepositoryImpl
import com.culinary.core.data.repository.RecipeRepositoryImpl
import com.culinary.core.data.repository.UnitRepositoryImpl
import com.culinary.core.domain.repository.CalculationRecordRepository
import com.culinary.core.domain.repository.IngredientRepository
import com.culinary.core.domain.repository.InventoryRepository
import com.culinary.core.domain.repository.RecipeRepository
import com.culinary.core.domain.repository.UnitRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** 只通过 Hilt 绑定 domain 端口，不向 feature 暴露实现类型（A03A §6.2）。 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindIngredientRepository(impl: IngredientRepositoryImpl): IngredientRepository

    @Binds
    @Singleton
    abstract fun bindUnitRepository(impl: UnitRepositoryImpl): UnitRepository

    @Binds
    @Singleton
    abstract fun bindRecipeRepository(impl: RecipeRepositoryImpl): RecipeRepository

    @Binds
    @Singleton
    abstract fun bindInventoryRepository(impl: InventoryRepositoryImpl): InventoryRepository

    @Binds
    @Singleton
    abstract fun bindCalculationRecordRepository(impl: CalculationRecordRepositoryImpl): CalculationRecordRepository
}
