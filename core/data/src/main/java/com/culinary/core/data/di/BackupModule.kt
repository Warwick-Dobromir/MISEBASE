package com.culinary.core.data.di

import android.content.Context
import com.culinary.core.common.AppLogger
import com.culinary.core.common.DispatcherProvider
import com.culinary.core.data.backup.AutoBackupRunnerImpl
import com.culinary.core.data.backup.QuiescedDatabaseSnapshotter
import com.culinary.core.data.backup.WriteBarrier
import com.culinary.core.database.CulinaryDatabase
import com.culinary.core.database.dao.BackupRecordDao
import com.culinary.core.domain.platform.AutoBackupRunner
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Provider
import javax.inject.Singleton

/** 备份编排装配：写屏障快照器、自动备份运行器与 `backup_record` DAO。 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BackupModule {
    @Binds
    @Singleton
    abstract fun bindAutoBackupRunner(impl: AutoBackupRunnerImpl): AutoBackupRunner

    companion object {
        @Provides
        @Singleton
        fun provideBackupRecordDao(database: CulinaryDatabase): BackupRecordDao = database.backupRecordDao()

        @Provides
        @Singleton
        fun provideSnapshotter(
            @ApplicationContext context: Context,
            databaseProvider: Provider<CulinaryDatabase>,
            writeBarrier: WriteBarrier,
            dispatchers: DispatcherProvider,
            logger: AppLogger,
        ): QuiescedDatabaseSnapshotter =
            QuiescedDatabaseSnapshotter(
                databaseProvider = databaseProvider,
                writeBarrier = writeBarrier,
                snapshotDirectory = File(context.cacheDir, "backup-snapshots"),
                dispatchers = dispatchers,
                logger = logger,
            )
    }
}
