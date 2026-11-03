# ADR-008：使用 Hilt 2.57.2

**状态**：Proposed
**版本**：`0.1.0`
**日期**：2026-11-04
**决策者**：首席架构师 / 项目负责人
**关联**：D-012、A01、V01

## 背景

16 个模块需要统一依赖装配、作用域、测试替换；手工 Composition Root 会膨胀。

## 决策

采用 Hilt `2.57.2` + KSP：

- `@HiltAndroidApp` 仅在 `:app`。
- 绑定在 `core:data` / `core:platform` 的 `di` 包。
- domain 不出现 Hilt 注解。
- 使用 Hilt Navigation Compose `1.3.0`。

**版本约束（已实测）：** Hilt 2.59+ 要求 AGP 9+；AGP 8.13 基线使用 2.57.2。

## 理由

- 编译期图校验，错误早暴露。
- Android 生命周期/ViewModel 集成成熟。
- 多模块测试替换方便。
- 不引入运行期服务定位器。

## 后果

- KSP/构建复杂度增加。
- 需要理解 scope/component 规则。
- Hilt 版本与 AGP 强耦合，升级需成组进行。

## 替代方案

- Koin：运行期才报错，大型图难追踪。
- 手写 DI：16 模块 Composition Root 过大。

## 复审条件

- AGP 升级到 9+ 时同步升级 Hilt；
- Hilt 构建时间/稳定性不可接受；
- 需要非 Android 目标（domain 已无 Hilt，可替换）。
