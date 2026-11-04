package com.culinary.core.data.di

import com.culinary.core.database.CulinaryDatabase
import com.culinary.core.database.dao.IngredientUnitConversionDao
import com.culinary.core.database.dao.RecipeComponentDao
import com.culinary.core.database.dao.RecipeDao
import com.culinary.core.database.dao.RecipeVersionDao
import com.culinary.core.database.dao.SauceComponentDao
import com.culinary.core.database.dao.SauceDao
import com.culinary.core.database.dao.SauceVersionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** 三大档案库所需的 DAO 装配；与基础 DAO 分开，避免单模块函数过多。 */
@Module
@InstallIn(SingletonComponent::class)
object ArchiveDaoModule {
    @Provides
    @Singleton
    fun provideIngredientUnitConversionDao(database: CulinaryDatabase): IngredientUnitConversionDao =
        database.ingredientUnitConversionDao()

    @Provides
    @Singleton
    fun provideSauceDao(database: CulinaryDatabase): SauceDao = database.sauceDao()

    @Provides
    @Singleton
    fun provideSauceVersionDao(database: CulinaryDatabase): SauceVersionDao = database.sauceVersionDao()

    @Provides
    @Singleton
    fun provideSauceComponentDao(database: CulinaryDatabase): SauceComponentDao = database.sauceComponentDao()

    @Provides
    @Singleton
    fun provideRecipeDao(database: CulinaryDatabase): RecipeDao = database.recipeDao()

    @Provides
    @Singleton
    fun provideRecipeVersionDao(database: CulinaryDatabase): RecipeVersionDao = database.recipeVersionDao()

    @Provides
    @Singleton
    fun provideRecipeComponentDao(database: CulinaryDatabase): RecipeComponentDao = database.recipeComponentDao()
}
