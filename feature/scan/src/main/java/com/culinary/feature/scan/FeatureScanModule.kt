package com.culinary.feature.scan

/**
 * `:feature:scan` 模块标识（MB-D-004，Stage 5 起第 14 个模块）。
 *
 * 职责：扫码页面、CameraX 生命周期、ML Kit（bundled）条码/二维码识别、
 * 相同码去抖与一次性消费、识别结果展示与导入确认入口。
 *
 * 不负责：食材创建/映射（`feature:products`）、库存入库（`feature:inventory`）、
 * 商品主数据编辑。本模块不依赖其它 feature，也不访问 database/data 实现。
 */
object FeatureScanModule {
    const val MODULE_PATH: String = "feature:scan"
}
