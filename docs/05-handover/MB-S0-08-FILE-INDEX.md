# MISEBASE 项目文件索引

**文档编号**：MB-S0-08
**版本**：`0.1.0`
**状态**：`Review`
**日期**：2026-11-04
**用途**：让后续工作区、审查者和开发者能在一个页面内找到「权威文件在哪里」。

---

## 1. 权威顺序

发生冲突时，按以下顺序取权威：

1. **MISEBASE 总任务书**（`MB-PROJECT-001`；当前尚未文件化，见 §7 待办）；
2. **已签字的 MISEBASE 新决策**（MB-D-xxx，见 [MB-S0-07](MB-S0-07-DECISION-REGISTER.md)）；
3. **本阶段 0 资料包**（经负责人审查后）；
4. **G2 已接受决策与 A-G2-001**（仅限被继承的部分）；
5. **G2 阶段报告 R11–R19**（历史事实，不改写）；
6. **P01/P02/A01–A07**（历史设计；与总任务书冲突处由 MB-S0-02 标记 Superseded）；
7. **G1 文档**（历史设计，最低优先级）。

> 未签字的本资料包只是审查建议，不是已确认需求。

---

## 2. 交接资料索引（仓库外）

### 2.1 G1

| 文件/目录 | 绝对路径 | 说明 |
|---|---|---|
| G1 归档 | `D:\A-1\CULINARY项目\07-交接包\CULINARY-G1-ARCHIVE-20261104.zip` | 281,487 字节；SHA-256 `AB17AF9C…ABBE8` |
| G1 校验 | 同目录 `.sha256` | 与压缩包一致 |
| G1 文档快照 | `…\G1\CULINARY-G1-ARCHIVE-20261104\snapshot\docs\` | 41 份 Markdown + ADR + 图 |
| G1 依赖验证工程 | `…\snapshot\tools\dependency-spike\` | 非产品代码 |

### 2.2 G2

| 文件/目录 | 绝对路径 | 说明 |
|---|---|---|
| G2 交接资料总目录 | `D:\A-1\CULINARY项目\07-交接包\G2-交接资料` | 296 个文件 |
| 完整性 MANIFEST | `…\G2-SHA256-MANIFEST.txt` | 295 条，全部匹配 |
| Git bundle | `…\02-Git基线与历史\G2-完整仓库.bundle` | 21 refs；HEAD `850ea63` |
| 源码 ZIP | `…\04-G2正式基线源码\G2-正式基线源码.zip` | 与 `2b523de` 一致 |
| Schema | `…\05-数据库与Schema\schemas\...\1.json` | 31 表 |
| Debug APK | `…\06-测试与构建证据\Debug APK\app-debug.apk` | 36,603,939 字节 |
| Release APK | `…\06-测试与构建证据\Release APK\app-release-unsigned.apk` | 23,533,968 字节，unsigned |
| mapping | `…\06-测试与构建证据\mapping\mapping.txt` | 35,519,990 字节 |

> 以上为只读参考；MISEBASE 工作副本在 `D:\MISEBASE`。

---

## 3. 仓库文档索引（`D:\MISEBASE\docs`）

### 3.1 治理

| 文档 | 用途 | 状态 |
|---|---|---|
| [A-G2-001 G2 收档](../00-governance/A-G2-001-G2-ARCHIVE.md) | G2 最终归档记录 | Archived |
| [DECISION-REGISTER](../00-governance/DECISION-REGISTER.md) | G2 决策登记册 0.20.0 | Review |
| [DOCUMENT-INDEX](../00-governance/DOCUMENT-INDEX.md) | G2 文档索引 | Review |
| [DELIVERY-MANIFEST](../00-governance/DELIVERY-MANIFEST.md) | G2 交付清单 | — |

### 3.2 产品

| 文档 | 用途 | 备注 |
|---|---|---|
| [P01 产品基线](../01-product/P01-PRODUCT-BASELINE.md) | 历史产品定义 | 与总任务书冲突处待标 Superseded |
| [P02 需求待确认](../01-product/P02-REQUIREMENTS-OPEN-ITEMS.md) | 历史待确认清单 | 部分已被新决策取代 |

### 3.3 架构

| 文档 | 用途 |
|---|---|
| [A01 技术选型](../02-architecture/A01-TECH-STACK-DECISION.md) | 技术栈与理由 |
| [A02 整体架构](../02-architecture/A02-SYSTEM-ARCHITECTURE.md) | 分层与模块 |
| [A03 模块依赖](../02-architecture/A03-MODULE-DEPENDENCIES.md) | 16 模块边界字典（历史） |
| [A03A 模块拆分审查](../02-architecture/A03A-MODULE-DECOMPOSITION-REVIEW.md) | 15 模块决策与 DAG |
| [A04 业务流程](../02-architecture/A04-BUSINESS-FLOWS.md) | 配方→计划→采购→库存 |
| [A05 ER 图](../02-architecture/A05-DATA-MODEL-ER.md) | 数据模型 |
| [A06 数据字典](../02-architecture/A06-DATA-DICTIONARY.md) | 字段级说明 |
| [A07 版本与迁移](../02-architecture/A07-VERSIONING-MIGRATION.md) | 六类版本、迁移与回滚 |
| [module-dependency-check.txt](../02-architecture/module-dependency-check.txt) | 设计期依赖检查文本 |

### 3.4 工程

| 文档 | 用途 |
|---|---|
| [E01 代码与目录规范](../03-engineering/E01-CODE-DIRECTORY-STANDARDS.md) | 命名、分层、提交 |
| [E02 并发与事务](../03-engineering/E02-DATABASE-CONCURRENCY.md) | WAL、单写者、锁、重试 |
| [E03 异常与日志](../03-engineering/E03-ERROR-LOGGING.md) | 错误码、日志、崩溃 |
| [E04 Git 与回档](../03-engineering/E04-GIT-ROLLBACK.md) | 分支、标签、回滚 |
| [E05 备份与恢复](../03-engineering/E05-BACKUP-RESTORE.md) | E05 0.4.1 规范 |
| [E05 修订复核](../03-engineering/E05-REVISION-REVIEW.md) | 0.4.1 复核证据 |
| [E06 测试与验收](../03-engineering/E06-TESTING-ACCEPTANCE.md) | 测试矩阵 |
| [E05 0.4.1 修订报告](../03-engineering/E05-0.4.1-REVISION-REPORT.md) | 修订记录 |

### 3.5 阶段与就绪

| 文档 | 用途 |
|---|---|
| [R01 框架实施顺序](../04-readiness/R01-FRAMEWORK-SEQUENCE.md) | G1/G2 阶段计划 |
| [R02 环境与依赖](../04-readiness/R02-ENVIRONMENT-DEPENDENCIES.md) | JDK/SDK/依赖 |
| [R03 前置检查](../04-readiness/R03-PREFLIGHT-CHECKLIST.md) | 开工检查 |
| [R04 启动报告](../04-readiness/R04-FRAMEWORK-START-REPORT.md) | G1 启动 |
| [R09 G1→G2 交接](../04-readiness/R09-G1-TO-G2-HANDOFF.md) | 历史交接 |
| [R10 G2 授权](../04-readiness/R10-G2-START-AUTHORIZATION.md) | G2 启动 |
| [R11]…[R19] | G2 阶段 1–9 报告 | 历史证据 |
| [V01 依赖验证](../04-readiness/V01-DEPENDENCY-VALIDATION.md) | 版本兼容性 |
| [D012 Hilt 补充](../04-readiness/D012-HILT-SUPPLEMENT.md) | 多模块 Hilt |

### 3.6 ADR 与图

| 文档 | 主题 |
|---|---|
| [ADR-001](../adr/ADR-001-NATIVE-ANDROID.md) | 原生 Android |
| [ADR-002](../adr/ADR-002-ROOM-SQLITE.md) | Room/SQLite |
| [ADR-003](../adr/ADR-003-LAYERED-MODULES.md) | 分层模块 |
| [ADR-004](../adr/ADR-004-VERSIONING-SNAPSHOT.md) | 版本与快照 |
| [ADR-005](../adr/ADR-005-UNIT-CONVERSION.md) | 单位换算 |
| [ADR-006](../adr/ADR-006-INVENTORY-LEDGER.md) | 库存台账 |
| [ADR-007](../adr/ADR-007-BACKUP-FORMAT.md) | 备份格式 |
| [ADR-008](../adr/ADR-008-HILT.md) | Hilt |
| [ADR-009](../adr/ADR-009-BARCODE-SCANNING.md) | 扫码 |
| [ADR-010](../adr/ADR-010-NO-SYNC-YET.md) | 不做同步 |
| [system-architecture.mmd](../diagrams/system-architecture.mmd) | 架构图 |
| [er-model.mmd](../diagrams/er-model.mmd) | ER 图 |
| [recipe-plan-flow.mmd](../diagrams/recipe-plan-flow.mmd) | 配方→计划→采购流程 |

### 3.7 阶段 0 接管资料（本包）

| 文档 | 内容 |
|---|---|
| [README](README.md) | 总览与结论 |
| [MB-S0-01 接管审查](MB-S0-01-INTAKE-AUDIT.md) | 完整性/事实/差异/风险 |
| [MB-S0-02 范围冻结](MB-S0-02-SCOPE-FREEZE.md) | 五项功能与边界 |
| [MB-S0-03 资料与决策需求](MB-S0-03-MATERIAL-REQUESTS.md) | 已提供/缺失/待决 |
| [MB-S0-04 接管检查清单](MB-S0-04-TAKEOVER-CHECKLIST.md) | PASS/PARTIAL/BLOCKED |
| [MB-S0-05 模块职责](MB-S0-05-MODULE-RESPONSIBILITY.md) | 15→13 模块与表归属 |
| [MB-S0-06 安全与回档](MB-S0-06-SAFETY-ROLLBACK.md) | 基线/迁移/锁/回滚 |
| [MB-S0-07 决策登记册](MB-S0-07-DECISION-REGISTER.md) | 继承 + 18 项建议 |
| [MB-S0-08 文件索引](MB-S0-08-FILE-INDEX.md) | 本文件 |

### 3.8 UI

| 文档 | 内容 |
|---|---|
| [MISEBASE-UI-DESIGN-SPEC](../design/MISEBASE-UI-DESIGN-SPEC.md) | 22 节设计书骨架；视觉方向待确认 |

---

## 4. 代码与配置索引

| 路径 | 内容 |
|---|---|
| `settings.gradle.kts` | 15 模块声明（待缩减为 13） |
| `gradle/libs.versions.toml` | 全部依赖版本 |
| `build-logic/convention/` | 6 个约定插件 |
| `app/src/main/java/com/culinary/navigation/CulinaryDestination.kt` | 9 个导航目的地（待改） |
| `app/src/main/java/com/culinary/ui/HomeScreen.kt` | 首页入口列表 |
| `core/database/schemas/com.culinary.core.database.CulinaryDatabase/1.json` | 31 表 schema |
| `core/database/.../schema/CulinarySchemaExtras.kt` | 部分唯一索引 + 触发器 |
| `core/database/.../migration/` | 迁移（当前为空） |
| `core/domain/src/main/kotlin/com/culinary/core/domain/` | 领域模型/端口/规则/用例 |
| `core/data/src/main/java/com/culinary/core/data/backup/` | E05 备份/恢复 |
| `core/platform/src/main/java/com/culinary/core/platform/scanner/` | 扫码解码/相机 |
| `feature/ingredients/src/main/java/com/culinary/feature/ingredients/` | 唯一可用切片 |
| `scripts/check-module-deps.ps1` | 依赖检查（待重写） |
| `.github/workflows/ci.yml` | CI（无远端） |
| `config/` | detekt/lint 配置 |

---

## 5. 证据索引

| 证据 | 路径 |
|---|---|
| 本次构建 APK | `app/build/outputs/apk/debug/app-debug.apk`（生成物，gitignore） |
| 本次测试 XML | `<module>/build/test-results/**/*.xml` |
| G2 测试报告 | `D:\A-1\…\06-测试与构建证据\测试报告\` |
| G2 迁移测试 | `…\测试报告\迁移测试\` + HTML |
| G2 构建日志 | `…\构建日志\当前构建-测试日志.txt` |
| G2 依赖检查结果 | `…\构建日志\依赖边界检查-结果.txt` |
| G2 安全验证 | `…\07-安全与平台验证\` |

---

## 6. 阅读路径

### 项目负责人

`docs/05-handover/README.md` → `MB-S0-01` → `MB-S0-02` → `MB-S0-07`（签字）

### 架构/开发

`README.md`（仓库根）→ `A03A` → `MB-S0-05` → `MB-S0-06` → `A07` → `E02` → 对应 feature

### UI/设计

`MB-S0-02` → `MISEBASE-UI-DESIGN-SPEC` → `A03A` §4 → 对应 feature 页面

### 审查者

`MB-S0-01` → `MB-S0-04` → `MB-S0-08` → 原始交接包

---

## 7. 待补文件

| 文件 | 原因 | 责任 |
|---|---|---|
| `docs/00-governance/MB-PROJECT-001.md` | 总任务书尚未文件化 | 项目负责人 |
| `docs/00-governance/MB-DECISIONS.md` | 阶段 0 决策签字后转正 | 接管方 |
| `docs/design/MISEBASE-UI-DESIGN-SPEC.md` | 已建骨架，待视觉确认 | 设计/负责人 |
| `docs/incidents/` | 事故记录目录（按需） | 工程组 |

---

## 8. 维护规则

1. 新增文档必须在本文登记；
2. 文档顶部必须有编号、版本、状态、日期；
3. 修改文档更新变更记录；
4. 影响架构的修改新增 ADR；
5. `Approved` 只能由项目负责人签收；
6. 不删除历史报告正文；用 `Superseded` / `Deprecated` 标注；
7. 不把“待确认”静默解释为“已确认”。

---

## 9. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 0.1.0 | 2026-11-04 | 建立权威顺序、交接/仓库/代码/证据索引 | 接管工程组 |
