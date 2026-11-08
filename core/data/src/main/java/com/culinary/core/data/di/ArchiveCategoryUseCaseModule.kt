package com.culinary.core.data.di

import com.culinary.core.domain.repository.ArchiveCategoryRepository
import com.culinary.core.domain.repository.ArchiveImageRepository
import com.culinary.core.domain.usecase.archive.ArchiveImageUseCase
import com.culinary.core.domain.usecase.archive.AssignArchiveCategoryUseCase
import com.culinary.core.domain.usecase.archive.ClearArchiveCategoryTestBatchUseCase
import com.culinary.core.domain.usecase.archive.GetArchiveCategoryForRecordUseCase
import com.culinary.core.domain.usecase.archive.ListArchiveCategoriesUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ArchiveCategoryUseCaseModule {
    @Provides
    @Singleton
    fun provideListArchiveCategories(repository: ArchiveCategoryRepository): ListArchiveCategoriesUseCase =
        ListArchiveCategoriesUseCase(repository)

    @Provides
    @Singleton
    fun provideGetArchiveCategoryForRecord(repository: ArchiveCategoryRepository): GetArchiveCategoryForRecordUseCase =
        GetArchiveCategoryForRecordUseCase(repository)

    @Provides
    @Singleton
    fun provideAssignArchiveCategory(repository: ArchiveCategoryRepository): AssignArchiveCategoryUseCase =
        AssignArchiveCategoryUseCase(repository)

    @Provides
    @Singleton
    fun provideClearArchiveCategoryTestBatch(
        repository: ArchiveCategoryRepository,
    ): ClearArchiveCategoryTestBatchUseCase = ClearArchiveCategoryTestBatchUseCase(repository)

    @Provides
    @Singleton
    fun provideArchiveImageUseCase(repository: ArchiveImageRepository): ArchiveImageUseCase =
        ArchiveImageUseCase(repository)
}
