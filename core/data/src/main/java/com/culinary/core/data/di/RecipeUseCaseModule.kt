package com.culinary.core.data.di

import com.culinary.core.common.IdGenerator
import com.culinary.core.domain.repository.IngredientRepository
import com.culinary.core.domain.repository.KitchenRelationRepository
import com.culinary.core.domain.repository.RecipeImageRepository
import com.culinary.core.domain.repository.RecipeRepository
import com.culinary.core.domain.repository.SauceRepository
import com.culinary.core.domain.transaction.TransactionRunner
import com.culinary.core.domain.usecase.recipe.ExpandRecipeUseCase
import com.culinary.core.domain.usecase.recipe.GetRecipeDetailUseCase
import com.culinary.core.domain.usecase.recipe.ListRecipeOptionsUseCase
import com.culinary.core.domain.usecase.recipe.ListRecipesUseCase
import com.culinary.core.domain.usecase.recipe.PublishRecipeDraftUseCase
import com.culinary.core.domain.usecase.recipe.PublishStoredRecipeVersionUseCase
import com.culinary.core.domain.usecase.recipe.RecipeImageUseCase
import com.culinary.core.domain.usecase.recipe.SaveRecipeDraftUseCase
import com.culinary.core.domain.usecase.recipe.SaveRecipeRelationsUseCase
import com.culinary.core.domain.usecase.recipe.SaveRecipeUseCase
import com.culinary.core.domain.usecase.sauce.ListSauceOptionsUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** 菜谱库用例装配（Stage 3）。 */
@Module
@InstallIn(SingletonComponent::class)
object RecipeUseCaseModule {
    @Provides
    @Singleton
    fun provideListRecipesUseCase(repository: RecipeRepository): ListRecipesUseCase = ListRecipesUseCase(repository)

    @Provides
    @Singleton
    fun provideListRecipeOptionsUseCase(repository: RecipeRepository): ListRecipeOptionsUseCase =
        ListRecipeOptionsUseCase(repository)

    @Provides
    @Singleton
    fun provideListSauceOptionsUseCase(repository: SauceRepository): ListSauceOptionsUseCase =
        ListSauceOptionsUseCase(repository)

    @Provides
    @Singleton
    fun provideGetRecipeDetailUseCase(
        recipeRepository: RecipeRepository,
        ingredientRepository: IngredientRepository,
        sauceRepository: SauceRepository,
    ): GetRecipeDetailUseCase = GetRecipeDetailUseCase(recipeRepository, ingredientRepository, sauceRepository)

    @Provides
    @Singleton
    fun provideSaveRecipeUseCase(
        repository: RecipeRepository,
        idGenerator: IdGenerator,
        transactionRunner: TransactionRunner,
    ): SaveRecipeUseCase = SaveRecipeUseCase(repository, idGenerator, transactionRunner)

    @Provides
    @Singleton
    fun provideSaveRecipeDraftUseCase(
        repository: RecipeRepository,
        idGenerator: IdGenerator,
        transactionRunner: TransactionRunner,
    ): SaveRecipeDraftUseCase = SaveRecipeDraftUseCase(repository, idGenerator, transactionRunner)

    @Provides
    @Singleton
    fun providePublishRecipeDraftUseCase(
        repository: RecipeRepository,
        idGenerator: IdGenerator,
        transactionRunner: TransactionRunner,
    ): PublishRecipeDraftUseCase = PublishRecipeDraftUseCase(repository, idGenerator, transactionRunner)

    @Provides
    @Singleton
    fun providePublishStoredRecipeVersionUseCase(
        repository: RecipeRepository,
        transactionRunner: TransactionRunner,
    ): PublishStoredRecipeVersionUseCase = PublishStoredRecipeVersionUseCase(repository, transactionRunner)

    @Provides
    @Singleton
    fun provideRecipeImageUseCase(repository: RecipeImageRepository): RecipeImageUseCase =
        RecipeImageUseCase(repository)

    @Provides
    @Singleton
    fun provideSaveRecipeRelationsUseCase(repository: KitchenRelationRepository): SaveRecipeRelationsUseCase =
        SaveRecipeRelationsUseCase(repository)

    @Provides
    @Singleton
    fun provideExpandRecipeUseCase(
        recipeRepository: RecipeRepository,
        ingredientRepository: IngredientRepository,
        sauceRepository: SauceRepository,
    ): ExpandRecipeUseCase = ExpandRecipeUseCase(recipeRepository, ingredientRepository, sauceRepository)
}
