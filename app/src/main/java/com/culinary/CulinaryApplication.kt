package com.culinary

import android.app.Application
import com.culinary.crash.CrashLogWriter
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

/**
 * 应用入口。
 *
 * 阶段 1 只建立 Hilt 根组件；启动流程、日志与恢复编排在阶段 6 实现（R01 §9）。
 * 这里不写任何业务规则（A03A §4.1）。
 */
@HiltAndroidApp
class CulinaryApplication : Application() {
    @Inject
    lateinit var crashLogWriter: CrashLogWriter

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        Thread.setDefaultUncaughtExceptionHandler(
            crashLogWriter.install(Thread.getDefaultUncaughtExceptionHandler()),
        )
    }
}
