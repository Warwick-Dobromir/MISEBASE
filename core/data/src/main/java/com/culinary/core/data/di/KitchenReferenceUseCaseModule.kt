package com.culinary.core.data.di

import com.culinary.core.common.IdGenerator
import com.culinary.core.domain.repository.KitchenReferenceRepository
import com.culinary.core.domain.repository.KitchenRelationRepository
import com.culinary.core.domain.transaction.TransactionRunner
import com.culinary.core.domain.usecase.kitchen.ArchiveKitchenReferenceUseCase
import com.culinary.core.domain.usecase.kitchen.GetKitchenReferenceUseCase
import com.culinary.core.domain.usecase.kitchen.ListKitchenReferencesUseCase
import com.culinary.core.domain.usecase.kitchen.ObserveKitchenRelationsUseCase
import com.culinary.core.domain.usecase.kitchen.SaveKitchenReferenceUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** 厨房新增参考资料的用例装配。 */
@Module
@InstallIn(SingletonComponent::class)
object KitchenReferenceUseCaseModule {
    @Provides
    @Singleton
    fun provideListKitchenReferencesUseCase(repository: KitchenReferenceRepository): ListKitchenReferencesUseCase =
        ListKitchenReferencesUseCase(repository)

    @Provides
    @Singleton
    fun provideGetKitchenReferenceUseCase(repository: KitchenReferenceRepository): GetKitchenReferenceUseCase =
        GetKitchenReferenceUseCase(repository)

    @Provides
    @Singleton
    fun provideSaveKitchenReferenceUseCase(
        repository: KitchenReferenceRepository,
        idGenerator: IdGenerator,
        transactionRunner: TransactionRunner,
    ): SaveKitchenReferenceUseCase = SaveKitchenReferenceUseCase(repository, idGenerator, transactionRunner)

    @Provides
    @Singleton
    fun provideArchiveKitchenReferenceUseCase(
        repository: KitchenReferenceRepository,
        transactionRunner: TransactionRunner,
    ): ArchiveKitchenReferenceUseCase = ArchiveKitchenReferenceUseCase(repository, transactionRunner)

    @Provides
    @Singleton
    fun provideObserveKitchenRelationsUseCase(repository: KitchenRelationRepository): ObserveKitchenRelationsUseCase =
        ObserveKitchenRelationsUseCase(repository)
}
