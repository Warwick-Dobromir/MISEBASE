package com.culinary.core.data.di

import android.content.Context
import com.culinary.core.common.DispatcherProvider
import com.culinary.core.common.IdGenerator
import com.culinary.core.data.debug.DebugInfoProvider
import com.culinary.core.data.startup.StartupCoordinator
import com.culinary.core.data.transaction.RoomTransactionRunner
import com.culinary.core.database.CulinaryDatabase
import com.culinary.core.domain.repository.IngredientRepository
import com.culinary.core.domain.transaction.TransactionRunner
import com.culinary.core.domain.usecase.ingredient.ArchiveIngredientUseCase
import com.culinary.core.domain.usecase.ingredient.ListIngredientsUseCase
import com.culinary.core.domain.usecase.ingredient.SaveIngredientUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.sync.Mutex
import java.io.File
import javax.inject.Provider
import javax.inject.Singleton

/**
 * 应用服务装配：事务协调、启动协调与调试信息。
 *
 * 与 [DataModule] 分开，避免单个 Hilt 模块函数过多、职责混杂。
 */
@Module
@InstallIn(SingletonComponent::class)
object AppServicesModule {
    @Provides
    @Singleton
    fun provideStartupCoordinator(
        @ApplicationContext context: Context,
        databaseProvider: Provider<CulinaryDatabase>,
    ): StartupCoordinator = StartupCoordinator(context, databaseProvider)

    @Provides
    @Singleton
    fun provideDebugInfoProvider(
        @ApplicationContext context: Context,
        databaseProvider: Provider<CulinaryDatabase>,
    ): DebugInfoProvider =
        DebugInfoProvider(
            context = context,
            databaseProvider = databaseProvider,
            crashLogDirectory = File(context.filesDir, "crash-logs"),
        )

    @Provides
    @Singleton
    fun provideListIngredientsUseCase(repository: IngredientRepository): ListIngredientsUseCase =
        ListIngredientsUseCase(repository)

    @Provides
    @Singleton
    fun provideSaveIngredientUseCase(
        repository: IngredientRepository,
        idGenerator: IdGenerator,
        transactionRunner: TransactionRunner,
    ): SaveIngredientUseCase = SaveIngredientUseCase(repository, idGenerator, transactionRunner)

    @Provides
    @Singleton
    fun provideArchiveIngredientUseCase(
        repository: IngredientRepository,
        transactionRunner: TransactionRunner,
    ): ArchiveIngredientUseCase = ArchiveIngredientUseCase(repository, transactionRunner)

    @Provides
    @Singleton
    fun provideTransactionRunner(
        database: CulinaryDatabase,
        dispatchers: DispatcherProvider,
        writeMutex: Mutex,
    ): TransactionRunner = RoomTransactionRunner(database, dispatchers, writeMutex)
}
