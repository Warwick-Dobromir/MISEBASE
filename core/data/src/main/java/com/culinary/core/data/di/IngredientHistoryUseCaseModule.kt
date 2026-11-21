package com.culinary.core.data.di

import com.culinary.core.domain.repository.IngredientRepository
import com.culinary.core.domain.usecase.ingredient.ListIngredientVersionsUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** 食材历史版本查询用例装配。 */
@Module
@InstallIn(SingletonComponent::class)
object IngredientHistoryUseCaseModule {
    @Provides
    @Singleton
    fun provideListIngredientVersions(repository: IngredientRepository): ListIngredientVersionsUseCase =
        ListIngredientVersionsUseCase(repository)
}
