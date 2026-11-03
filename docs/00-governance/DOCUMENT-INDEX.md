# CULINARY 文档索引

**文档编号**：GOV-INDEX
**版本**：`0.1.0`
**状态**：`Review`
**日期**：2026-11-04
**维护人**：首席架构师 / 技术负责人

---

## 1. 交付物总表

任务书要求 17 项交付物。本索引把它们映射到可维护的文档文件；其中“ER 图 + 数据字典”拆为两份文件，因此实际文件数为 18，但交付物仍为 17 项。

| # | 交付物 | 文档 | 版本 | 状态 | 主要读者 |
|---|---|---|---|---|---|
| 1 | 产品需求基线 | [P01 产品需求基线](../01-product/P01-PRODUCT-BASELINE.md) | 0.1.0 | Review | 产品、架构、测试 |
| 2 | 已确认需求与待确认事项清单 | [P02 需求与待确认清单](../01-product/P02-REQUIREMENTS-OPEN-ITEMS.md) | 0.1.0 | Review | 产品、架构 |
| 3 | 技术选型对比与最终决策 | [A01 技术选型](../02-architecture/A01-TECH-STACK-DECISION.md) | 0.1.0 | Review | 架构、开发 |
| 4 | 软件整体架构设计书 | [A02 整体架构](../02-architecture/A02-SYSTEM-ARCHITECTURE.md) | 0.1.0 | Review | 全体 |
| 5 | 模块职责与依赖关系说明 | [A03 模块与依赖](../02-architecture/A03-MODULE-DEPENDENCIES.md) | 0.1.0 | Review | 开发 |
| 6 | 核心业务流程设计 | [A04 业务流程](../02-architecture/A04-BUSINESS-FLOWS.md) | 0.1.0 | Review | 产品、开发、测试 |
| 7 | 数据库 ER 图与数据字典 | [A05 ER 图](../02-architecture/A05-DATA-MODEL-ER.md) + [A06 数据字典](../02-architecture/A06-DATA-DICTIONARY.md) | 0.1.0 | Review | 开发、测试 |
| 8 | 数据版本与迁移设计 | [A07 版本与迁移](../02-architecture/A07-VERSIONING-MIGRATION.md) | 0.1.0 | Review | 开发、测试 |
| 9 | 代码规范与工程目录规范 | [E01 代码与目录规范](../03-engineering/E01-CODE-DIRECTORY-STANDARDS.md) | 0.1.0 | Review | 开发 |
| 10 | 数据库并发与事务安全方案 | [E02 并发与事务](../03-engineering/E02-DATABASE-CONCURRENCY.md) | 0.1.0 | Review | 开发、测试 |
| 11 | 异常处理与日志规范 | [E03 异常与日志](../03-engineering/E03-ERROR-LOGGING.md) | 0.1.0 | Review | 开发 |
| 12 | 代码回档与版本管理方案 | [E04 Git 与回档](../03-engineering/E04-GIT-ROLLBACK.md) | 0.1.0 | Review | 开发 |
| 13 | 数据备份与恢复方案 | [E05 备份与恢复](../03-engineering/E05-BACKUP-RESTORE.md) | 0.1.0 | Review | 开发、测试 |
| 14 | 自动化测试与验收标准 | [E06 测试与验收](../03-engineering/E06-TESTING-ACCEPTANCE.md) | 0.1.0 | Review | 开发、测试 |
| 15 | 框架搭建实施顺序 | [R01 框架实施顺序](../04-readiness/R01-FRAMEWORK-SEQUENCE.md) | 0.1.0 | Review | 开发 |
| 16 | 开发环境与依赖清单 | [R02 环境与依赖](../04-readiness/R02-ENVIRONMENT-DEPENDENCIES.md) | 0.1.0 | Review | 开发 |
| 17 | 框架制作前置检查清单 | [R03 前置检查清单](../04-readiness/R03-PREFLIGHT-CHECKLIST.md) | 0.1.0 | Review | 项目负责人 |
| — | 框架制作启动报告 | [R04 启动报告](../04-readiness/R04-FRAMEWORK-START-REPORT.md) | 0.1.0 | Review | 项目负责人 |
| 附 | 依赖兼容性验证 | [V01 技术验证](../04-readiness/V01-DEPENDENCY-VALIDATION.md) | 0.1.0 | Review | 开发 |

## 2. 治理与决策文件

| 文档 | 用途 |
|---|---|
| [决策登记册](DECISION-REGISTER.md) | 所有需要签收的架构/产品决策，含默认值与影响 |
| [ADR-001 原生 Android](../adr/ADR-001-NATIVE-ANDROID.md) | 平台与 UI 技术栈 |
| [ADR-002 Room/SQLite](../adr/ADR-002-ROOM-SQLITE.md) | 本地数据库与数据访问 |
| [ADR-003 分层模块化](../adr/ADR-003-LAYERED-MODULES.md) | 分层、模块边界与依赖规则 |
| [ADR-004 版本与快照](../adr/ADR-004-VERSIONING-SNAPSHOT.md) | 配方版本、计划快照、历史不可变 |
| [ADR-005 单位换算](../adr/ADR-005-UNIT-CONVERSION.md) | 单位维度与“禁止无依据换算” |
| [ADR-006 库存台账](../adr/ADR-006-INVENTORY-LEDGER.md) | 批次、预留、流水、不可直接改数 |
| [ADR-007 备份格式](../adr/ADR-007-BACKUP-FORMAT.md) | 备份容器、校验、恢复与迁移 |
| [ADR-008 Hilt](../adr/ADR-008-HILT.md) | 依赖注入 |
| [ADR-009 扫码方案](../adr/ADR-009-BARCODE-SCANNING.md) | CameraX + ML Kit，本地优先 |
| [ADR-010 暂不做同步](../adr/ADR-010-NO-SYNC-YET.md) | 离线单用户，保留同步缝 |

## 3. 图表

| 文件 | 内容 |
|---|---|
| [system-architecture.mmd](../diagrams/system-architecture.mmd) | 分层与模块依赖 |
| [er-model.mmd](../diagrams/er-model.mmd) | 核心实体关系 |
| [recipe-plan-flow.mmd](../diagrams/recipe-plan-flow.mmd) | 配方展开到采购的流程 |

图表使用 Mermaid；可在 GitHub、Android Studio Markdown 插件或 VS Code 中直接渲染。

## 4. 阅读路径建议

### 项目负责人签收路径

`R04 启动报告 → DECISION-REGISTER → P02 待确认清单 → R03 前置检查`

### 开发人员上手路径

`P01 产品基线 → A02 整体架构 → A03 模块依赖 → A06 数据字典 → E01 目录规范 → R01 实施顺序`

### 测试人员上手路径

`P01 产品基线 → A04 业务流程 → E06 测试与验收 → E02 并发 → E05 备份恢复`

## 5. 文档维护规则

1. 每份文档顶部必须有：编号、版本、状态、日期、负责人。
2. 修改文档必须更新“变更记录”表；影响架构的修改必须新增 ADR。
3. `Approved` 只能由项目负责人签收；AI 或开发者不得自行把 `Review` 改为 `Approved`。
4. 文档中的“待确认”不得在实现时被静默解释为“已确认”；实现必须引用决策编号。
5. 架构基线版本与 App 版本、数据库 schema 版本、备份格式版本是四个独立版本号，不得混用。
