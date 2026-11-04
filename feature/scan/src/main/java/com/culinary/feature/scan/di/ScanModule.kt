package com.culinary.feature.scan.di

import com.culinary.core.domain.barcode.ScanFrameGate
import com.culinary.core.domain.repository.InventoryRepository
import com.culinary.core.domain.usecase.scan.LookupScanImportUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 扫码模块装配点。
 *
 * [ScanFrameGate] 故意不加 `@Singleton`：每次进入扫码页都应有一段新的去抖/消费状态，
 * 复用单例会把上一次的 `consumed` 带进来，导致返回页面后不再识别。
 *
 * [LookupScanImportUseCase] 是只读查询，放在这里装配可以避免让 `feature:scan` 直接依赖
 * `core:data` 的实现模块（它只看到 `core:domain` 的端口）。
 */
@Module
@InstallIn(SingletonComponent::class)
object ScanModule {
    @Provides
    fun provideScanFrameGate(): ScanFrameGate = ScanFrameGate()

    @Provides
    fun provideLookupScanImportUseCase(inventoryRepository: InventoryRepository): LookupScanImportUseCase =
        LookupScanImportUseCase(inventoryRepository)
}
