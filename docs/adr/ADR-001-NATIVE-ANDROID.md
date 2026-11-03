# ADR-001：采用原生 Android（Kotlin + Compose）

**状态**：Proposed
**版本**：`0.1.0`
**日期**：2026-11-04
**决策者**：首席架构师 / 项目负责人
**关联**：D-001、A01

## 背景

CULINARY 只面向 Android 手机，核心能力包括本地数据库事务、文件与图片、相机扫码、SAF 备份、后台任务。项目为单用户、离线优先、长期维护。

## 决策

采用原生 Android：

- Kotlin 2.2.20
- Jetpack Compose + Material 3
- 单 Activity + Navigation Compose
- 不采用 Flutter、React Native、KMP UI

## 理由

1. 无跨平台需求，跨平台收益接近零。
2. 平台能力（SQLite/文件/相机/SAF/WorkManager）无需桥接，故障面更小。
3. 官方工具链、调试、性能与测试路径最短。
4. 长期维护可依赖 Android 官方生态。

## 后果

- 只能运行在 Android；未来若需 iOS/桌面，需要独立实现表现层。
- Compose/AGP/Kotlin 版本联动较紧，需要版本目录与升级纪律。
- 需要处理 Android 版本差异（min 26）。

## 替代方案

- Flutter/React Native：跨平台但平台桥接增加风险，本项目管理类应用不需要。
- KMP：当前无多端需求，构建复杂度不划算。

## 复审条件

- 项目负责人明确要求 iOS/桌面端；
- Android 平台能力无法满足核心场景；
- Compose 出现长期不可维护的破坏性变更。
