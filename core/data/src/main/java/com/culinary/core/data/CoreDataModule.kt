package com.culinary.core.data

/**
 * `:core:data` 骨架占位（阶段 1）。
 *
 * 职责：Repository 实现、Mapper、TransactionRunner、文件/图片存储、备份恢复引擎。
 * 阶段 4 由真实实现替换；Hilt 绑定落在本模块的 di 包（D-012）。
 */
object CoreDataModule {
    const val MODULE_PATH: String = "core:data"
}
