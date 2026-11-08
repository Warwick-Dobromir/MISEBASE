package com.culinary.core.data.di

import com.culinary.core.common.IdGenerator
import com.culinary.core.domain.repository.IngredientRepository
import com.culinary.core.domain.repository.RecipeRepository
import com.culinary.core.domain.repository.SauceImageRepository
import com.culinary.core.domain.repository.SauceRepository
import com.culinary.core.domain.repository.UnitRepository
import com.culinary.core.domain.transaction.TransactionRunner
import com.culinary.core.domain.usecase.sauce.ArchiveSauceUseCase
import com.culinary.core.domain.usecase.sauce.GetSauceDetailUseCase
import com.culinary.core.domain.usecase.sauce.ListSaucesUseCase
import com.culinary.core.domain.usecase.sauce.PublishSauceVersionUseCase
import com.culinary.core.domain.usecase.sauce.SauceImageUseCase
import com.culinary.core.domain.usecase.sauce.SaveSauceDraftUseCase
import com.culinary.core.domain.usecase.sauce.SaveSauceRelationsUseCase
import com.culinary.core.domain.usecase.sauce.SaveSauceUseCase
import com.culinary.core.domain.usecase.unit.ListUnitsUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** 酱汁库用例装配（Stage 3）；与食材/事务装配分开，避免单模块函数过多。 */
@Module
@InstallIn(SingletonComponent::class)
object SauceUseCaseModule {
    @Provides
    @Singleton
    fun provideListUnitsUseCase(repository: UnitRepository): ListUnitsUseCase = ListUnitsUseCase(repository)

    @Provides
    @Singleton
    fun provideListSaucesUseCase(repository: SauceRepository): ListSaucesUseCase = ListSaucesUseCase(repository)

    @Provides
    @Singleton
    fun provideGetSauceDetailUseCase(
        sauceRepository: SauceRepository,
        ingredientRepository: IngredientRepository,
        recipeRepository: RecipeRepository,
    ): GetSauceDetailUseCase = GetSauceDetailUseCase(sauceRepository, ingredientRepository, recipeRepository)

    @Provides
    @Singleton
    fun provideSaveSauceUseCase(
        repository: SauceRepository,
        idGenerator: IdGenerator,
        transactionRunner: TransactionRunner,
    ): SaveSauceUseCase = SaveSauceUseCase(repository, idGenerator, transactionRunner)

    @Provides
    @Singleton
    fun provideSaveSauceDraftUseCase(
        repository: SauceRepository,
        idGenerator: IdGenerator,
        transactionRunner: TransactionRunner,
    ): SaveSauceDraftUseCase = SaveSauceDraftUseCase(repository, idGenerator, transactionRunner)

    @Provides
    @Singleton
    fun providePublishSauceVersionUseCase(
        repository: SauceRepository,
        transactionRunner: TransactionRunner,
    ): PublishSauceVersionUseCase = PublishSauceVersionUseCase(repository, transactionRunner)

    @Provides
    @Singleton
    fun provideArchiveSauceUseCase(
        repository: SauceRepository,
        transactionRunner: TransactionRunner,
    ): ArchiveSauceUseCase = ArchiveSauceUseCase(repository, transactionRunner)

    @Provides
    @Singleton
    fun provideSauceImageUseCase(repository: SauceImageRepository): SauceImageUseCase = SauceImageUseCase(repository)

    @Provides
    @Singleton
    fun provideSaveSauceRelationsUseCase(
        repository: com.culinary.core.domain.repository.KitchenRelationRepository,
    ): SaveSauceRelationsUseCase = SaveSauceRelationsUseCase(repository)
}
