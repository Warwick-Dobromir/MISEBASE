package com.culinary.feature.inventory

import com.culinary.core.domain.scan.ScanStockGateway
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 把 `feature:inventory` 的扫码入库接口绑到 `core:domain` 端口。
 *
 * `feature:scan` 只看到 [ScanStockGateway]，看不到本模块实现类型（A03A §6.2）。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class InventoryScanModule {
    @Binds
    @Singleton
    abstract fun bindScanStockGateway(impl: InventoryScanStockGateway): ScanStockGateway
}
