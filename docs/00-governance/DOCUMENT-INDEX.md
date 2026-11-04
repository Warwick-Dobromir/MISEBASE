# CULINARY 文档索引

**文档编号**：GOV-INDEX
**版本**：`0.18.0`
**状态**：`Review`
**日期**：2026-11-04
**维护人**：首席架构师 / 技术负责人

---

## 1. 交付物总表

任务书要求 17 项交付物。本索引把它们映射到可维护的文档文件；其中“ER 图 + 数据字典”拆为两份文件，因此实际文件数为 18，但交付物仍为 17 项。

| # | 交付物 | 文档 | 版本 | 状态 | 主要读者 |
|---|---|---|---|---|---|
| 1 | 产品需求基线 | [P01 产品需求基线](../01-product/P01-PRODUCT-BASELINE.md) | 0.1.1 | Superseded（§4.2/§6.2 部分） | 产品、架构、测试 |
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
| [交付清单与校验记录](DELIVERY-MANIFEST.md) | 17 项交付物、SHA-256、完整性检查与开工门禁快照 |
| [决策登记册](DECISION-REGISTER.md) | 所有需要签收的架构/产品决策，含默认值与影响 |
| [A03A 模块拆分审查提案](../02-architecture/A03A-MODULE-DECOMPOSITION-REVIEW.md) | G2 的 15 模块历史；MISEBASE 13 模块目标见 §13 与 MB-D-003 |
| [G1 最终复核包](../04-readiness/G1-REVIEW-PACKET.md) | D-004/D-011/G5 证据汇总与签收选项 |
| [E05 修订复核包](../03-engineering/E05-REVISION-REVIEW.md) | E05 0.4.0 五项修订、哈希结构、journal 与测试 |
| [G1 归档核验与 G2 准入报告](../04-readiness/G1-ARCHIVE-AUDIT.md) | 17 份文档、ER/字典、技术栈、并发、备份、测试、环境的 8 项核验 |
| [R08 G1 最终定点修订报告](../04-readiness/R08-FINAL-REVISION-REPORT.md) | R01 0.2.2、七项 PROPOSED 门禁、状态统一（历史） |
| [R09 G1→G2 最终交接说明](../04-readiness/R09-G1-TO-G2-HANDOFF.md) | 项目定位、G1 签收、15 模块、技术栈、待办决策、G2 范围与前置条件 |
| [D012 Hilt 补充材料](../04-readiness/D012-HILT-SUPPLEMENT.md) | D-012 多模块聚合、运行期注入、依赖路由、不兼容边界、坐标迁移证据 |
| [R10 G2 阶段 1 授权记录](../04-readiness/R10-G2-START-AUTHORIZATION.md) | D-012 接受、G2 授权范围、Git 基线与退出条件 |
| [R11 G2 阶段 1 完成报告](../04-readiness/R11-G2-STAGE1-REPORT.md) | 15 模块骨架、构建/质量/安装实测、偏差与遗留 |
| [R12 G2 阶段 2 完成报告](../04-readiness/R12-G2-STAGE2-REPORT.md) | 核心契约、领域规则、24 个 JVM 测试、D-005 边界 |
| [R13 G2 阶段 3 完成报告](../04-readiness/R13-G2-STAGE3-REPORT.md) | Room v1 31 表、约束与触发器、77 例测试、快照/计划删除策略、迁移阻塞项 |
| [R14 G2 阶段 4 完成报告](../04-readiness/R14-G2-STAGE4-REPORT.md) | 数据层、事务协调、图片存储、E05 备份/恢复骨架、34 例测试 |
| [R15 G2 阶段 5 完成报告](../04-readiness/R15-G2-STAGE5-REPORT.md) | 平台调度/权限/SAF/扫码解码、19 例测试、设备验证延后 |
| [R16 G2 阶段 6 完成报告](../04-readiness/R16-G2-STAGE6-REPORT.md) | 导航、启动门禁/恢复、崩溃日志、调试入口、设备证据 |
| [R17 G2 阶段 7 完成报告](../04-readiness/R17-G2-STAGE7-REPORT.md) | 食材 CRUD 全链路、设备 E2E、167 例测试 |
| [R18 G2 阶段 8 完成报告](../04-readiness/R18-G2-STAGE8-REPORT.md) | 算法验证：展开/汇总/缺口/快照、200 节点基准 |
| [R19 G2 阶段 9 完成报告](../04-readiness/R19-G2-STAGE9-REPORT.md) | 质量门禁：CI、迁移、R8、恢复演练、离线、基线 |
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
| [A-G2-001 G2 收档文件](A-G2-001-G2-ARCHIVE.md) | G2 基线、验收证据与遗留；§14 Errata 更正测试数 188→197 |
| [MB-DECISIONS 决策登记册](../05-handover/MB-S0-07-DECISION-REGISTER.md) | MISEBASE 18 项决策；10 项 ACCEPTED、8 项 PROPOSED；G2-S1 测试口径 |

### 2.1 MISEBASE Stage 1 文档（2026-11-04）

| 文档 | 用途 |
|---|---|
| [MB-S1-01 执行报告](../06-stage1/MB-S1-01-EXECUTION-REPORT.md) | Stage 1 目标、变更、测试、构建、Git、风险、未决 |
| [MB-S1-02 模块调整](../06-stage1/MB-S1-02-MODULE-ADJUSTMENT.md) | 15→13 前后清单、依赖图、构建与回档 |
| [MB-S1-03 依赖检查](../06-stage1/MB-S1-03-DEPENDENCY-CHECK.md) | 配置化检查、源码 import 扫描、拓扑排序、违规注入 |
| [MB-S1-04 休眠表](../06-stage1/MB-S1-04-DORMANT-TABLES.md) | 11 表 DORMANT 清单、引用扫描、禁止规则 |
| [MB-S1-05 迁移准备物](../06-stage1/MB-S1-05-MIGRATION-READINESS.md) | 迁移规范、测试模板、备份/回滚验证、回档点；未实施 |
| [MB-S1-06 备份缺口分析](../06-stage1/MB-S1-06-BACKUP-GAP-ANALYSIS.md) | S0-R-02/R-10/R-11 测试缺口、修复方案、验证方案 |
| [MB-S1-07 UI 隔离基础](../06-stage1/MB-S1-07-UI-ISOLATION.md) | Compose/ViewModel/Domain/Data 分层约束；未开发页面 |
| [MB-S1-08 文档修正记录](../06-stage1/MB-S1-08-DOC-CORRECTIONS.md) | MB-D-018 修正项与历史正文不变证明 |
| [MB-S1-09 审查回执](../06-stage1/MB-S1-09-REVIEW-RECEIPT.md) | 导航边界、实际起点、diff/status、仪器化测试与备份状态补记 |
| [MB-S1-10 Stage 1 关闭记录](../06-stage1/MB-S1-10-STAGE1-CLOSE-RECORD.md) | 审查通过、关闭前 HEAD、决策状态、关闭标签与未决事项 |

### 2.2 MISEBASE Stage 2 文档（2026-11-04，状态 Review）

| 文档 | 用途 |
|---|---|
| [MB-S2-01 执行报告](../07-stage2/MB-S2-01-EXECUTION-REPORT.md) | 前置检查、领域模型、规则、Repository、测试、Git、风险；状态 `Review` |
| [MB-S2-02 领域模型说明](../07-stage2/MB-S2-02-DOMAIN-MODEL.md) | 值对象、档案/版本、草稿、库存、需求/缺口、计算记录与 Schema 差距 |
| [MB-S2-03 规则与测试矩阵](../07-stage2/MB-S2-03-RULES-TEST-MATRIX.md) | 每条规则到测试类/用例的映射；264 例 / 0 失败 |
| [MB-S2-04 Repository 契约](../07-stage2/MB-S2-04-REPOSITORY-CONTRACTS.md) | 端口、实现、DAO 增量、fake、契约测试、事务边界 |
| [MB-S2-05 决策映射](../07-stage2/MB-S2-05-DECISION-MAPPING.md) | MB-D-013/014/015/016/017 的落地与 Schema 变更请求 |

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

## 6. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 0.6.0 | 2026-11-04 | 建立文档索引与交付物映射 | 架构组 |
| 0.7.0 | 2026-11-04 | 补 D012-SUP-01、R10、R11（D-012 接受与 G2 阶段 1） | 工程组 |
| 0.8.0 | 2026-11-04 | 补 R12（G2 阶段 2 核心契约） | 工程组 |
| 0.9.0 | 2026-11-04 | 补 R13（G2 阶段 3 数据库 v1） | 工程组 |
| 0.10.0 | 2026-11-04 | 补 R14（G2 阶段 4 数据层） | 工程组 |
| 0.11.0 | 2026-11-04 | 补 R15（G2 阶段 5 平台能力） | 工程组 |
| 0.12.0 | 2026-11-04 | 补 R16（G2 阶段 6 应用外壳） | 工程组 |
| 0.13.0 | 2026-11-04 | 补 R17（G2 阶段 7 食材纵向切片） | 工程组 |
| 0.14.0 | 2026-11-04 | 补 R18（G2 阶段 8 算法验证） | 工程组 |
| 0.15.0 | 2026-11-04 | 补 R19（G2 阶段 9 质量门禁） | 工程组 |
| 0.16.0 | 2026-11-04 | 新增 A-G2-001 G2 收档文件 | 工程组 |
| 0.17.0 | 2026-11-04 | Stage 1：新增 8 份 Stage 1 文档；记录 P01 Superseded、A03A 13 模块目标、A-G2-001 Errata | 接管工程组 |
| 0.17.1 | 2026-11-04 | Stage 1 审查回执：新增 MB-S1-09；补证据边界 | 接管工程组 |
| 0.17.2 | 2026-11-04 | Stage 1 关闭：新增 MB-S1-10；记录关闭标签 | 接管工程组 |
| 0.18.0 | 2026-11-04 | Stage 2：新增 MB-S2-01～05；执行报告状态 Review；记录 264 例测试与 Schema 变更请求 | 接管工程组 |
