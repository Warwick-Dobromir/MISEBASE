# MB-S1-01 Stage 1 执行报告

**文档编号**：MB-S1-01
**版本**：`0.1.1`
**状态**：`Review`（提交审查）
**日期**：2026-11-04
**任务书**：MB-S1-001 Stage 1 工程范围清理
**工作区**：`D:\MISEBASE`；分支 `misebase/main`
**执行前 HEAD**：`f8de1bc3a9b4326b5817edea68c9352611456c77`（工作树 clean）
**接管基线**：`705e0ca`；代码基线 `2b523de`（`framework-0.1.0`）
**结论**：Stage 1 允许范围内的工作已完成；无 Schema/备份实现/功能页面变更；**具备审查条件**。

---

## 1. 执行摘要

| 项 | 结果 |
|---|---|
| 范围冻结 | 五项功能；采购清单归 Inventory；计划/采购不独立；P01 标 Superseded |
| 模块集 | **15 → 13**（删除 planning/procurement；products 缩减） |
| 依赖检查 | 配置化 + 源码 import 扫描 + 拓扑排序；13 节点 / 35 声明边 / 155 import 边 / 0 违规 |
| 违规注入 | 7 类注入全部捕获（feature→feature、feature→impl、db→domain、domain→Android、UI→DAO、模块数、循环） |
| 休眠表 | 11 张 DORMANT；0 处允许范围外引用；未删/未迁/未改结构 |
| 迁移准备 | 规范 + 测试模板 + 备份/回滚清单 + 回档点设计；**未创建 v2、未实施迁移** |
| 备份/恢复 | S0-R-02/R-10/R-11 缺口与修复/验证方案；**未改实现** |
| UI 隔离 | 分层规则 + 检查规则 + 评审清单；**未开发页面** |
| 测试/构建 | **197 例 JVM 测试 / 0 失败**；Debug APK 构建成功；ktlint/detekt PASS |
| Git | 8 个提交 + Stage 1 检查点；`framework-0.1.0` 未移动 |

## 2. 执行前 Git 状态与标签

### 2.1 实测

| 项 | 值 |
|---|---|
| 分支 | `misebase/main` |
| HEAD | `f8de1bc3a9b4326b5817edea68c9352611456c77`（`docs(stage0): record stage 0 closure commit and tags`） |
| 工作树 | clean（`git status --porcelain` 空） |
| 接管基线 | `705e0ca6c203bc3941d21c974aadb38f6e8e3ae1`（G2 收档） |
| 代码基线 | `2b523de`（`framework-0.1.0`） |
| Stage 0 关闭标签 | `checkpoint/misebase-stage0-closed-20261104` → `1ede793` |
| Stage 1 起点标签 | `checkpoint/misebase-stage1-start-20261104` → `1ede793` |
| 远程 | 无（本地仓库） |

> 说明：`705e0ca` 是接管基线；Stage 0 在其上追加 5 个纯文档提交。HEAD `f8de1bc` 比
> `stage1-start` 标签多一个“记录关闭提交与标签”的文档提交，工作树 clean，不影响基线。

### 2.2 基线关系

```text
2b523de (framework-0.1.0)
  └─ 705e0ca (G2 archive; 接管基线)
       └─ ... Stage 0 docs ...
            └─ 1ede793 (stage0-closed / stage1-start)
                 └─ f8de1bc (实际 HEAD)
```

### 2.3 为什么没有从 705e0ca 重新开始

`705e0ca` 只比代码基线多 G2 收档文档，业务代码/schema/测试与 `2b523de` 相同；Stage 0 已在其上
完成接管审查并关闭。重置会丢失 Stage 0 记录，且不改变代码事实，因此以实际 HEAD 为 Stage 1 起点。

## 3. 已确认决策与未签收边界

### 3.1 已确认（5/18，负责人授权）

| 决策 | 内容 | 本阶段使用 |
|---|---|---|
| MB-D-001 | 基线 `705e0ca`；分支；项目名 MISEBASE；包名暂留 | 报告/README |
| MB-D-002 | 五项功能；采购清单归 Inventory；计划/采购不独立；P01 Superseded | 文档/模块 |
| MB-D-003 | 13 模块；删 planning/procurement；products 缩减 | settings/app/依赖检查 |
| MB-D-005 | 11 表保留并标 DORMANT；不删/不迁/不建 v2 | 休眠表/迁移 |
| MB-D-018 | 纯文档修正；A-G2-001 Errata；不改历史正文 | 文档 |

### 3.2 仍为 PROPOSED（未当作已批准）

| 决策 | 本阶段处理 |
|---|---|
| MB-D-004 扫码 UI 归属 | **不新建 `feature:scan`、不实现扫码 UI**；products 仅保留商品主数据范围 |
| MB-D-007 备份一致性 | **不改任何备份/恢复实现**；只做分析/方案（MB-S1-06） |
| MB-D-008/009 视觉/导航 | **不做视觉定稿、不重排导航**；导航仅删除已删模块的编译引用 |
| 其余 MB-D | 未签收；未写成 ACCEPTED |

> **导航变更边界（审查补记）**：本次导航变更**仅删除已删除模块的编译引用**——
> `CulinaryDestination` 的 `PLANNING`/`PROCUREMENT` 两个枚举、`strings.xml` 两个字符串、
> 导航测试断言 8→6。它**不是 MB-D-009 的导航信息架构决策**：未新增入口、未调整其余入口顺序、
> 未改变首页结构。`MB-D-009` 仍为 `PROPOSED`。

## 4. 文档修正结果

| 文档 | 结果 |
|---|---|
| README | MISEBASE 品牌；13 模块；schema v1/31 表；197 例；11 张 DORMANT；5 项 ACCEPTED；Stage 1 链接 |
| P01 | 0.1.1；顶部修订说明；§4.2/§6.2/§8 标 Superseded/重新解释；原文保留 |
| A03A | 0.3.1；顶部 13 模块修订；§4 标历史；新增 §13 目标模块集 |
| A-G2-001 | 正文不动；新增 §14 Errata：188→197；R11/R12/S1 说明 |
| MB-DECISIONS | 0.2.0；5 项 ACCEPTED；13 项 PROPOSED；G2-S1；签收表 |
| DOCUMENT-INDEX | 0.17.0；新增 8 份 Stage 1 文档、A-G2-001、MB-DECISIONS |
| A05/A06 | 0.1.1；11 张表 DORMANT 标注；字段/结构未改 |
| 历史报告 | R11–R19 未修改 |

详见 [MB-S1-08](MB-S1-08-DOC-CORRECTIONS.md)。

## 5. 模块调整结果

### 5.1 前后对比

| 项 | 调整前 | 调整后 |
|---|---:|---:|
| 模块数 | 15 | **13** |
| app | 1 | 1 |
| core | 6 | 6 |
| feature | 8 | **6** |
| 声明依赖边 | 43 | **35** |
| 源码 import 边 | 未统计 | **155** |
| 导航目的地 | 9（HOME + 8） | 7（HOME + 6 入口） |

### 5.2 变更

- `settings.gradle.kts`：13 个 `include`；
- 删除 `feature/planning`、`feature/procurement`（8 行占位，无测试/业务代码）；
- `app/build.gradle.kts`：删除 2 条装配依赖；
- `CulinaryDestination`/`strings.xml`/导航测试：删除 2 个占位入口，断言 8→6；
- `feature:products`：KDoc 缩减为商品/包装/条码/食材映射；无供应商管理、无扫码 UI；
- 未新建 `feature:scan`；未开发功能页面。

详见 [MB-S1-02](MB-S1-02-MODULE-ADJUSTMENT.md)。

## 6. 依赖检查结果

| 项 | 结果 |
|---|---|
| 脚本 | `scripts/check-module-deps.ps1`（配置：`config/module-dependencies.json`） |
| 模块集 | 13 / 13 |
| 声明边 | 35 |
| import 边 | 155 |
| 拓扑排序 | 13 节点，无环 |
| 违规 | **0** |
| 违规注入 | 7 类全部捕获；干净树 0 违规 |

拓扑序：`common → database → designsystem → domain → data → platform → ingredients → inventory →
products → recipes → sauces → settings → app`。

详见 [MB-S1-03](MB-S1-03-DEPENDENCY-CHECK.md)。

## 7. 休眠表处理结果

| 项 | 结果 |
|---|---|
| 表数 | 11 |
| 标注 | `config/dormant-tables.json` + A05/A06 文档 + MB-S1-04 |
| 引用扫描 | 248 个 Kotlin 文件；**0 处允许范围外引用** |
| 允许引用 | 仅 `core/database` 结构定义与约束测试 |
| 结构变更 | **无** |
| 删除/迁移 | **无** |
| Schema v2 | **未创建** |

11 表：`supplier`、`production_plan`、`production_plan_item`、`production_plan_snapshot`、
`material_requirement`、`material_requirement_source`、`procurement_requirement`、`purchase_order`、
`purchase_order_item`、`goods_receipt`、`goods_receipt_item`。

详见 [MB-S1-04](MB-S1-04-DORMANT-TABLES.md)。

## 8. 迁移准备物

| 交付物 | 位置 | 状态 |
|---|---|---|
| 迁移规范 | `templates/MIGRATION-SPEC-TEMPLATE.md` | 模板 |
| 迁移测试模板 | `templates/MigrationTestTemplate.kt.txt` | 模板（不编译） |
| 备份/回滚验证清单 | `templates/MIGRATION-BACKUP-ROLLBACK-CHECKLIST.md` | 模板 |
| 回档点设计 | MB-S1-05 §5 | 设计 |
| 迁移实施 | 无 | **未实施** |
| Schema v2 | 无 | **未创建** |

详见 [MB-S1-05](MB-S1-05-MIGRATION-READINESS.md)。

## 9. 备份/恢复分析

| 风险 | 状态 | 本阶段输出 |
|---|---|---|
| S0-R-02 WAL/备份一致性 | OPEN（高） | 7 项缺口 + 修复方案 + 6 项验证 |
| S0-R-10 恢复中断 | PARTIAL | 8 项缺口 + 修复方案 + 7 项验证 |
| S0-R-11 自动备份假成功 | OPEN（高） | 5 项缺口 + 修复方案 + 6 项验证 |

**未修改** `AutoBackupWorker`、`BackupArchiveWriter`、`RestoreExecutor`、写屏障、checkpoint。

> **备份结论（审查补记）**：备份/恢复**未修复，仅做了分析与方案**；S0-R-02、S0-R-11 仍为
> **OPEN（高）**，S0-R-10 仍为 PARTIAL。本阶段没有把任何备份能力标记为“一致备份”或“已修复”。
> 详见 [MB-S1-06](MB-S1-06-BACKUP-GAP-ANALYSIS.md)。

## 10. UI 隔离基础

- 规则：Compose 不碰 DAO/SQLite/Data；ViewModel 协调状态；Domain 管规则；Data 管持久化；
- 检查：`V-UI-DAO`（feature 中同时 import Compose 与 Room/SQLite/Data 即失败）；
- 现状：`feature:ingredients` 合规；其余 feature 仅占位；`core:domain` 纯 JVM；
- 未开发页面、未做视觉定稿、未重排导航。

详见 [MB-S1-07](MB-S1-07-UI-ISOLATION.md)。

## 11. 测试与构建结果

### 11.1 命令

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"

.\gradlew.bat test assembleDebug ktlintCheck detekt --console=plain --no-daemon --rerun-tasks
```

### 11.2 结果

| 项 | 结果 |
|---|---|
| JVM 测试 | **197 例 / 0 失败** |
| 测试类 | 39 个 |
| 模块分布 | common 9 / domain 40 / database 77 / data 43 / platform 19 / app 6 / feature 3 |
| Debug APK | 构建成功：`app/build/outputs/apk/debug/app-debug.apk`，36,603,123 字节 |
| 构建耗时 | `BUILD SUCCESSFUL in 3m 21s`（809 tasks executed，`--rerun-tasks`） |
| ktlintCheck | PASS |
| detekt | PASS |
| 仪器化迁移测试 | 2 例，**未运行**（需设备/模拟器） |

> **证据边界（审查补记）**：`CulinaryDatabaseMigrationTest` 的 2 例仪器化迁移测试**未运行**，
> 因为本轮没有设备/模拟器。**Stage 1 的 PASS 不包含仪器化迁移测试证据**，不能据此宣称
> 迁移测试通过；该证据缺口保留到具备设备时补跑。

测试数未下降：删除的两个模块没有测试；导航测试仍 3 例，仅断言从 8 个入口改为 6 个。

## 12. Git 提交 / 标签 / 变更范围

### 12.1 提交

| # | 提交哈希 | 主题 |
|---:|---|---|
| 1 | `a4b88dd4f8cfd3261140a974a73b1ae2c790fb8b` | `docs(stage1): correct README, P01, A03A and G2 errata` |
| 2 | `88fdfa2610e9c6d0c44460b1d274f013b4746c30` | `refactor(build): reduce module set to 13 per MB-D-003` |
| 3 | `d0fb009eae82b7e0730fe8ca80390029cf08bcc1` | `test(build): add configurable module dependency checker` |
| 4 | `7beb3c98e41a892b3e3bee7d4ca5d7cbbfa74fee` | `docs(db): mark 11 dormant tables and add reference scan` |
| 5 | `07ace2e09c211573fdf39bfaeb7e906310531120` | `docs(db): add migration readiness templates and rollback plan` |
| 6 | `20ba3881f1202869ad35c4a746fb11d18e777fbe` | `docs(backup): analyse R-02/R-10/R-11 gaps without implementation changes` |
| 7 | `4a0944f858874e9e6ee7873092faa0a4432d3b86` | `docs(ui): establish UI and business isolation rules` |
| 8 | `93d0d92cc5df57093e0ab5b8c8014a491e10c746` | `docs(stage1): add execution report and stage checks` |

### 12.2 标签

| 标签 | 解析 |
|---|---|
| `checkpoint/misebase-stage1-20261104` | `93d0d92`（提交；标签对象 `4010536`） |
| `checkpoint/misebase-stage1-reviewed-20261104` | `{{REVIEWED_TAG}}` |
| `framework-0.1.0` | `2b523de`（未移动） |

### 12.3 变更范围

`git diff --name-status 2b523de..HEAD` 摘要：

```text
M	README.md
M	app/build.gradle.kts
M	app/src/main/java/com/culinary/navigation/CulinaryDestination.kt
M	app/src/main/java/com/culinary/ui/HomeScreen.kt
M	app/src/main/res/values/strings.xml
M	app/src/test/java/com/culinary/navigation/CulinaryDestinationTest.kt
A	config/dormant-tables.json
A	config/module-dependencies.json
A	docs/00-governance/A-G2-001-G2-ARCHIVE.md
M	docs/00-governance/DOCUMENT-INDEX.md
M	docs/01-product/P01-PRODUCT-BASELINE.md
M	docs/02-architecture/A03A-MODULE-DECOMPOSITION-REVIEW.md
M	docs/02-architecture/A05-DATA-MODEL-ER.md
M	docs/02-architecture/A06-DATA-DICTIONARY.md
A	docs/02-architecture/module-dependency-check-13.txt
A	docs/05-handover/MB-S0-01-INTAKE-AUDIT.md
A	docs/05-handover/MB-S0-02-SCOPE-FREEZE.md
A	docs/05-handover/MB-S0-03-MATERIAL-REQUESTS.md
A	docs/05-handover/MB-S0-04-TAKEOVER-CHECKLIST.md
A	docs/05-handover/MB-S0-05-MODULE-RESPONSIBILITY.md
A	docs/05-handover/MB-S0-06-SAFETY-ROLLBACK.md
A	docs/05-handover/MB-S0-07-DECISION-REGISTER.md
A	docs/05-handover/MB-S0-08-FILE-INDEX.md
A	docs/05-handover/README.md
A	docs/05-handover/S0-01-BASELINE-REPORT.md
A	docs/05-handover/S0-02-ARCHITECTURE-REPORT.md
A	docs/05-handover/S0-03-DECISION-REVIEW.md
A	docs/05-handover/S0-04-RISK-REGISTER.md
A	docs/05-handover/S0-04A-DOCUMENT-CONSISTENCY.md
A	docs/05-handover/S0-05-OWNER-SIGNOFF.md
A	docs/05-handover/S0-06-STAGE-REPORT.md
A	docs/05-handover/S0-07-REVISION-REPORT.md
A	docs/06-stage1/MB-S1-01-EXECUTION-REPORT.md
A	docs/06-stage1/MB-S1-02-MODULE-ADJUSTMENT.md
A	docs/06-stage1/MB-S1-03-DEPENDENCY-CHECK.md
A	docs/06-stage1/MB-S1-04-DORMANT-TABLES.md
A	docs/06-stage1/MB-S1-05-MIGRATION-READINESS.md
A	docs/06-stage1/MB-S1-06-BACKUP-GAP-ANALYSIS.md
A	docs/06-stage1/MB-S1-07-UI-ISOLATION.md
A	docs/06-stage1/MB-S1-08-DOC-CORRECTIONS.md
A	docs/06-stage1/templates/MIGRATION-BACKUP-ROLLBACK-CHECKLIST.md
A	docs/06-stage1/templates/MIGRATION-SPEC-TEMPLATE.md
A	docs/06-stage1/templates/MigrationTestTemplate.kt.txt
A	docs/design/MISEBASE-UI-DESIGN-SPEC.md
D	feature/planning/build.gradle.kts
D	feature/planning/src/main/java/com/culinary/feature/planning/FeaturePlanningModule.kt
D	feature/procurement/build.gradle.kts
D	feature/procurement/src/main/java/com/culinary/feature/procurement/FeatureProcurementModule.kt
M	feature/products/src/main/java/com/culinary/feature/products/FeatureProductsModule.kt
A	scripts/check-doc-consistency.ps1
A	scripts/check-dormant-tables.ps1
M	scripts/check-module-deps.ps1
A	scripts/check-stage1-scope.ps1
A	scripts/test-module-deps.ps1
M	settings.gradle.kts
```

> 该范围从代码基线 `2b523de` 起算，包含 Stage 0 新增的 `docs/05-handover/*` 与设计文档；
> Stage 1 自身的变更起点是 `f8de1bc`。`scripts/check-stage1-scope.ps1` 以 `f8de1bc` 为默认起点。
> 不涉及 `core/database`、`core/data/src/main`、`core/platform/src/main` 的修改。

范围检查脚本：`scripts/check-stage1-scope.ps1`（PASS）。
文档一致性：`scripts/check-doc-consistency.ps1`（PASS：13 模块、197 例、P01/A03A/A-G2-001 修订标记、5/18 决策状态、链接可解析）。

> 审查补记：导航边界、`f8de1bc` 实际起点、`git diff/status` 证据、仪器化测试缺口与备份状态，
> 见 [MB-S1-09 审查回执](MB-S1-09-REVIEW-RECEIPT.md)。

## 13. 未解决问题与风险

| # | 事项 | 状态/影响 |
|---:|---|---|
| 1 | MB-D-007 未签收 | 备份仍是一致性缺口；不得实现 WAL checkpoint/写屏障 |
| 2 | S0-R-02 WAL 备份 | OPEN（高）；真实 WAL 场景未验证 |
| 3 | S0-R-10 恢复原子性 | PARTIAL；最终装库非原子 rename、无真实 kill 测试 |
| 4 | S0-R-11 Worker 假成功 | OPEN（高）；实现未改 |
| 5 | 迁移未实施 | 仅模板/方案；v2 需单独授权 |
| 6 | 仪器化测试未跑 | 2 例迁移测试需设备 |
| 7 | 休眠表长期保留 | 存储成本低；未来删除需授权 |
| 8 | MB-D-004/008/009 未签收 | 扫码 UI/视觉/导航仍待决策 |
| 9 | `app -> core:common` 显式允许 | 因 `api` 暴露；已在配置与文档说明 |
| 10 | 依赖检查为词法扫描 | 不覆盖反射/同包；Gradle 依赖兜底 |

## 14. Stage 1 是否具备审查条件

**具备。** 理由：

- 模块集实际为 13，构建/测试通过；
- 依赖检查可复现、能捕获注入违规；
- 无 Schema/Entity/DAO/迁移/DB 文件变更；
- 无备份/恢复实现变更；
- 无新增功能页面/扫码 UI/视觉定稿/导航重排；
- 11 张休眠表未删/未迁/未改结构，DORMANT 与扫描完成；
- 文档修正完成，历史正文未改；
- Git 变更在允许范围，`framework-0.1.0` 未移动。

**审查注意**：MB-D-007 仍是备份实现的硬阻塞；Stage 1 的备份结论仅为分析与方案，不是修复完成。

## 15. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 0.1.0 | 2026-11-04 | Stage 1 执行报告：范围、模块、依赖、休眠表、迁移、备份、UI、测试、Git | 接管工程组 |
| 0.1.1 | 2026-11-04 | 审查补记：导航非 MB-D-009、仪器化测试未运行、备份未修复、审查回执链接 | 接管工程组 |
