package com.culinary.core.data.di

import android.content.Context
import com.culinary.core.common.AppLogger
import com.culinary.core.common.Clock
import com.culinary.core.common.DefaultDispatcherProvider
import com.culinary.core.common.DispatcherProvider
import com.culinary.core.common.IdGenerator
import com.culinary.core.common.SystemClock
import com.culinary.core.common.UuidV4Generator
import com.culinary.core.data.logging.TimberAppLogger
import com.culinary.core.database.CulinaryDatabase
import com.culinary.core.database.DatabaseFactory
import com.culinary.core.database.dao.IngredientDao
import com.culinary.core.database.dao.UnitDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Singleton

/**
 * 数据层装配（E02 §3.1 / §4）。
 *
 * 写事务使用单线程执行器 + 应用级 `Mutex`，读使用固定大小线程池；
 * 数据库单例由 Hilt 提供，Repository / Feature 不得自行 `databaseBuilder`。
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    @WriteExecutor
    fun provideWriteExecutor(): ExecutorService =
        Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "culinary-db-write") }

    @Provides
    @Singleton
    @ReadExecutor
    fun provideReadExecutor(): ExecutorService =
        Executors.newFixedThreadPool(4) { runnable -> Thread(runnable, "culinary-db-read") }

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        @WriteExecutor writeExecutor: ExecutorService,
        @ReadExecutor readExecutor: ExecutorService,
    ): CulinaryDatabase = DatabaseFactory.create(context, writeExecutor, readExecutor)

    @Provides
    @Singleton
    fun provideIngredientDao(database: CulinaryDatabase): IngredientDao = database.ingredientDao()

    @Provides
    @Singleton
    fun provideUnitDao(database: CulinaryDatabase): UnitDao = database.unitDao()

    @Provides
    @Singleton
    fun provideWriteMutex(): Mutex = Mutex()

    @Provides
    @Singleton
    fun provideDispatcherProvider(): DispatcherProvider = DefaultDispatcherProvider()

    @Provides
    @Singleton
    fun provideIdGenerator(): IdGenerator = UuidV4Generator()

    @Provides
    @Singleton
    fun provideClock(): Clock = SystemClock

    @Provides
    @Singleton
    fun provideAppLogger(): AppLogger = TimberAppLogger()
}
