package com.culinary.feature.products

/**
 * `:feature:products` 骨架占位（MB-D-003 缩减后）。
 *
 * 职责：商品、包装规格、条码、商品-食材映射，为扫码与库存提供商品主数据。
 * 不包含供应商管理；不包含扫码 UI（扫码 UI 归属仍为 MB-D-004 PROPOSED，本阶段不新建 feature:scan）。
 * 本模块不依赖其它 feature，也不访问 database/data 实现。
 */
object FeatureProductsModule {
    const val MODULE_PATH: String = "feature:products"
}
