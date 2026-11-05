package com.culinary.core.designsystem.component

/**
 * 按钮语义变体（MB-UI-001 §3.9）。
 *
 * 独立成文件，避免 `CulinaryButtons.kt` 同时承载枚举与多个可组合函数时
 * 触发 detekt 的 MatchingDeclarationName 规则。
 */
enum class CulinaryButtonVariant {
    Primary,
    Secondary,
    Ghost,
    Danger,
}
