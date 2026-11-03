package com.culinary.core.common

/**
 * `:core:common` 骨架占位（阶段 1）。
 *
 * 职责：Outcome、错误模型/错误码、Clock、IdGenerator、DispatcherProvider、日志门面。
 * 阶段 2 由真实契约替换；保留本类型用于证明模块与依赖真实存在（R01 §4.1）。
 */
object CoreCommonModule {
    const val MODULE_PATH: String = "core:common"
}
