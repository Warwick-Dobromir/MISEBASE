package com.culinary.core.platform.di

import com.culinary.core.domain.platform.BackupScheduler
import com.culinary.core.platform.work.WorkManagerBackupScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** 平台端口绑定；实现只在本模块内可见（A03A §6.4）。 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PlatformModule {
    @Binds
    @Singleton
    abstract fun bindBackupScheduler(impl: WorkManagerBackupScheduler): BackupScheduler
}
