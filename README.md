# CULINARY

**专业个人烹饪档案与研发管理系统 — 技术架构与开发前期准备**

| 项目 | 内容 |
|---|---|
| 架构基线版本 | `0.2.0`（修订版，待 G1 签收） |
| 当前阶段 | 技术架构设计与开发前期准备（未进入正式开发） |
| 文档日期 | 2026-11-04 |
| 目标设备 | Android 手机（主）、平板（兼容） |
| 核心约束 | 离线优先、单用户、本地数据、长期可维护 |
| 文档总状态 | `Review`（待项目负责人签收） |

> 本仓库当前只包含**架构、数据、工程治理与准备文档**，以及隔离的依赖验证工程（`tools/dependency-spike/`），不包含产品 UI、业务功能代码或产品 APK。
> 这是刻意为之：正式框架搭建必须在本文档集签收后开始。

## 先读什么

1. [框架制作启动报告](docs/04-readiness/R04-FRAMEWORK-START-REPORT.md) — 结论、风险、开工条件。
2. [产品需求基线](docs/01-product/P01-PRODUCT-BASELINE.md) — 产品边界与已确认规则。
3. [技术选型对比与最终决策](docs/02-architecture/A01-TECH-STACK-DECISION.md) — 锁定哪些技术，为什么。
4. [软件整体架构设计书](docs/02-architecture/A02-SYSTEM-ARCHITECTURE.md) — 分层与依赖规则。
5. [数据模型 ER 图](docs/02-architecture/A05-DATA-MODEL-ER.md) 与 [数据字典](docs/02-architecture/A06-DATA-DICTIONARY.md)。
6. [框架制作前置检查清单](docs/04-readiness/R03-PREFLIGHT-CHECKLIST.md) — 当前环境还差什么。

完整导航见 [文档索引](docs/00-governance/DOCUMENT-INDEX.md)。

## 当前结论（摘要）

- **平台**：原生 Android，Kotlin + Jetpack Compose；不采用 Flutter / React Native。
- **数据**：Room + Android 平台 SQLite（WAL），单进程、单写者、显式事务边界。
- **架构**：`app → feature → core:domain ← core:data → core:database` 的严格分层；UI 不得直接访问数据库。
- **领域难点**：配方递归展开、循环检测、单位不可换算、版本快照、批次库存、预留与实扣分离。
- **工程底线**：数据库迁移禁止破坏性回退；恢复/导入必须先备份、先校验、可回滚；代码回档不等于数据回档。
- **依赖验证**：AGP 8.13 / Kotlin 2.2.20 / Hilt 2.57.2 / Room 2.8.5 / Compose 2025.10.01 已实际构建通过；v1 网络权限可在 manifest 中移除并经 `aapt` 验证。见 [V01 技术验证](docs/04-readiness/V01-DEPENDENCY-VALIDATION.md)。
- **当前门禁**：架构层面已具备开工条件；框架开工只剩 G1（8 项决策整体签收，D-004/D-011 待复核）。Release 签名已改为发布前门禁。见 [R04 启动报告](docs/04-readiness/R04-FRAMEWORK-START-REPORT.md)。
- **未决事项**：见 [决策登记册](docs/00-governance/DECISION-REGISTER.md)。所有 `PROPOSED` 决策需负责人签收后才可标为 `Approved`。

## 文档状态规则

| 状态 | 含义 | 是否可用于正式开发 |
|---|---|---|
| `Draft` | 草案，内容可能不完整 | 否 |
| `Review` | 已自检，等待负责人确认 | 仅可作为讨论依据 |
| `Approved` | 已由项目负责人明确签收 | 是 |
| `Deprecated` | 已废弃，保留追溯 | 否 |

当前没有文档被标记为 `Approved`。这是符合任务书要求的：**未经用户确认的方案不得冒充已确认方案。**

## 版本与变更

架构基线遵循语义化版本。任何影响模块边界、数据库结构、备份格式或锁定技术栈的修改，必须：

1. 新增或修订 ADR；
2. 更新受影响文档的版本号与变更记录；
3. 通过“整体一致性审查”清单；
4. 由项目负责人签收后，才可进入实现。
