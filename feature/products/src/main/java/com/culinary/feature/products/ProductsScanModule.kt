package com.culinary.feature.products

import com.culinary.core.domain.scan.ScanIngredientGateway
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 把 `feature:products` 的扫码食材接口绑到 `core:domain` 端口。
 *
 * `feature:scan` 只看到 [ScanIngredientGateway]，看不到本模块实现类型（A03A §6.2）。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ProductsScanModule {
    @Binds
    @Singleton
    abstract fun bindScanIngredientGateway(impl: ProductScanIngredientGateway): ScanIngredientGateway
}
