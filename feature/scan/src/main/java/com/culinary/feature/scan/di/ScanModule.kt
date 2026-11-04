package com.culinary.feature.scan.di

import com.culinary.core.domain.barcode.ScanFrameGate
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 扫码模块装配点。
 *
 * [ScanFrameGate] 故意不加 `@Singleton`：每次进入扫码页都应有一段新的去抖/消费状态，
 * 复用单例会把上一次的 `consumed` 带进来，导致返回页面后不再识别。
 */
@Module
@InstallIn(SingletonComponent::class)
object ScanModule {
    @Provides
    fun provideScanFrameGate(): ScanFrameGate = ScanFrameGate()
}
