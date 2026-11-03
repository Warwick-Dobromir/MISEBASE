package com.culinary.di

import android.content.Context
import com.culinary.core.common.Clock
import com.culinary.crash.CrashLogWriter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

/** 应用级装配：崩溃日志目录固定在私有 `filesDir/crash-logs`。 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideCrashLogWriter(
        @ApplicationContext context: Context,
        clock: Clock,
    ): CrashLogWriter = CrashLogWriter(File(context.filesDir, "crash-logs"), clock)
}
