# MISEBASE

**个人烹饪档案与库存管理系统 — Android 原生应用**

| 项目 | 内容 |
|---|---|
| 品牌/项目名 | MISEBASE（包名暂留 `com.culinary`，发布前按 D-017/MB-D-001 决定） |
| 架构基线 | `0.4.1`（G1 已签收）；G2 已收档于 `framework-0.1.0` / `2b523de` |
| 当前阶段 | **Stage 3 CLOSED（2026-11-04）**；**MB-D-007 备份一致性已 CLOSED（2026-11-04，审查通过）**；**Stage 4 可启动，等待任务书与负责人启动确认** |
| 工程状态 | **13 模块**；Room schema **v1 / 31 表**（G2 历史；当前已迁移 v2 / 33 表，11 表 `DORMANT`）；三大档案库可用；库存/扫码为占位；**197 例 JVM 测试**（G2 基线；当前 612 次执行 / 376 唯一用例 / 0 失败） |
| 数据库 | **已创建**：schema v2（33 表），导出 `1.json`、`2.json`；`1→2` 迁移已落地；本次备份一致性修复未改 Schema/Entity/DAO |
| 构建基线 | AGP 8.13.0 / Kotlin 2.2.20 / Gradle 8.14.3 / JDK 21 / minSdk 26 / compile & target 36 |
| 依赖注入 | Hilt 2.57.2 + KSP（D-012 `ACCEPTED`，禁止 2.59+） |
| 离线承诺 | 无 `INTERNET` 权限（manifest 移除 + `aapt2` 已验证） |

## 快速开始

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"

.\gradlew.bat test assembleDebug --console=plain --no-daemon
.\gradlew.bat ktlintCheck detekt

powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-module-deps.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-module-deps.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-dormant-tables.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-doc-consistency.ps1

adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.culinary/.MainActivity
```

Android Studio 首次打开需要 `local.properties`（不提交 Git）：

```properties
sdk.dir=C\:\\Users\\<user>\\AppData\\Local\\Android\\Sdk
```

依赖仓库为镜像优先、官方回退（R02 §6.3）；`build-logic` 额外需要 Gradle Plugin Portal
（ktlint-gradle 未同步到镜像）。

## 13 模块（MB-D-003 ACCEPTED，MISEBASE 目标）

```text
app
core/
  common        Outcome、错误模型、Clock、IdGenerator、Dispatcher、日志门面
  domain        领域模型、Repository 端口、Use Case、配方/单位规则（纯 JVM）
  database      Room 实体/DAO/迁移/Schema；11 张休眠表仅保留结构
  data          Repository 实现、Mapper、事务协调、文件与备份
  designsystem  主题、颜色、通用组件
  platform      SAF、权限、CameraX、WorkManager
feature/
  ingredients sauces recipes products inventory settings
```

`:core` 与 `:feature` 是 Gradle 结构容器（无构建脚本），不计入 13 个模块。
`feature:planning`、`feature:procurement` 已按 MB-D-003 删除；采购清单归 `feature:inventory`，
计划/采购不再作为独立模块。依赖检查实测 **13 节点 / 35 条声明边**，拓扑排序无环。

`feature:products` 的范围是**商品 / 包装规格 / 条码 / 食材映射**，为扫码与库存提供商品主数据；
**不包含供应商管理**，也**不包含扫码 UI**（扫码 UI 归属仍为 MB-D-004 `PROPOSED`，Stage 1 未新建
`feature:scan`，未实现扫码页面）。

## 架构边界

- `feature` 之间零直接依赖；跨 feature 通过 `core:domain` 端口与 `app` 导航协作。
- `core:domain` 是纯 JVM 模块，不依赖 Android/Room/Compose/Hilt。
- `core:database` 不依赖 `core:domain`；`core:data` 不直接依赖 Android SAF/`Uri`。
- Compose 页面不直接操作 DAO/SQLite，不实现库存计算/单位换算/配方展开；ViewModel 协调状态，
  Domain 管规则，Data 管持久化（详见 [UI 隔离基础](docs/06-stage1/MB-S1-07-UI-ISOLATION.md)）。
- 不创建 `:core:model`；领域模型统一放 `core:domain` 的 `model` 包。

## 数据安全约定

- 已发布菜谱/酱汁版本不可修改；新编辑产生新版本。
- 库存只能通过追加流水改变；`inventory_ledger` 与 `change_log` 禁止 UPDATE/DELETE。
- 11 张规划/采购/供应商表标记 `DORMANT`：保留 v1 结构，不删表、不迁移、不改造；
  禁止新代码引用（见 [休眠表清单](docs/06-stage1/MB-S1-04-DORMANT-TABLES.md)）。
- 备份/恢复遵守 E05；恢复失败不清理现场。**MB-D-007 已 CLOSED（2026-11-04，审查通过）**：
  写屏障、WAL checkpoint、Worker 真实返回、恢复原子性、Worker 单测与 WAL 恢复测试已落地；
  Stage 4 强制前置已满足。
- Schema v2 已创建（33 表）；`1→2` 迁移已落地，仍禁止 destructive migration、清库或删除现场。

## 工程治理

- 构建逻辑集中在 `build-logic/convention` 的 6 个约定插件。
- 依赖版本只来自 `gradle/libs.versions.toml`；模块脚本不得硬编码版本。
- 质量门禁：`ktlintCheck`、`detekt`、`check-module-deps.ps1`、`check-dormant-tables.ps1`、
  `check-doc-consistency.ps1`。
- Git：每个阶段打 `checkpoint/*` 与 `baseline/*` 标签，见 [E04](docs/03-engineering/E04-GIT-ROLLBACK.md)。

## 决策状态（2026-11-04）

已接受（13/18，负责人授权，见 [S0-05](docs/05-handover/S0-05-OWNER-SIGNOFF.md)）：

- MB-D-001 接管基线与项目身份；
- MB-D-002 范围冻结（五项功能；采购清单归 Inventory）；
- MB-D-003 目标 13 模块；
- MB-D-005 11 张休眠表保留并标 `DORMANT`；
- MB-D-007 备份一致性（**已 CLOSED，2026-11-04 审查通过**；Stage 4 前置已满足）；
- MB-D-008 UI 视觉方向 A（专业厨房档案：暖中性 + Orange 强调）；
- MB-D-009 导航 N1（酱汁/食材/菜谱/库存/扫码五个底部入口 + 顶栏设置）；
- MB-D-013 子配方版本固定（发布时写入明确版本 ID）；
- MB-D-014 库存扣减模式（先预览、确认后写流水）；
- MB-D-015 可选配料缺料口径（默认不计缺口，详情可见）；
- MB-D-016 采购清单形态（不落 `purchase_order*` 表）；
- MB-D-017 计算记录快照（确认后不可变）；
- MB-D-018 纯文档修正授权。

仍为 `PROPOSED`（不得当作已批准，5 项）：MB-D-004 扫码 UI 归属、MB-D-006 测试口径、
MB-D-010 CI、MB-D-011 真机、MB-D-012 签名。

> 历史记录：本次签收之前，MB-D-007 仍为 `PROPOSED`；2026-11-04 已由负责人签收为 `ACCEPTED`，
> 并作为 Stage 4 强制前置；同日完成 MB-BK-001/002 并通过审查，状态更新为 **`CLOSED`**。

> G2 历史决策 `D-005`/`D-007` 与 MISEBASE 的 `MB-D-005`/`MB-D-007` 不是同一体系，禁止互相替代。
> G2 的 `ACCEPTED` 不自动构成对应 `MB-D-*` 的 `ACCEPTED`；MB-D 状态以本仓库签收记录为准。

## 当前状态与未决

- **G2 阶段 1–9** 已完成并归档；`framework-0.1.0` 固定在 `2b523de`，未移动。
- **Stage 0/1/2/3 均已关闭**；Schema 变更任务已关闭（v2 / 33 表）；三大档案库可用，库存/扫码仍为占位。
- **MB-D-007 已 CLOSED（2026-11-04，审查通过）**：六项技术事项、Schema 版本阻断修复、
  `612 / 376 / 0` 测试与 v2 检查点均已落库；详见
  [MB-D-007 关闭记录](docs/10-backup/MB-D-007-CLOSE-RECORD.md)。
- **Stage 4 可启动，当前等待单独任务书与负责人启动确认**；本 README 不构成 Stage 4 启动授权，
  也未开始库存业务开发。
- **仍为 PROPOSED 的阻塞项**：MB-D-004 未签收前不新建 `feature:scan`；MB-D-010 CI、
  MB-D-011 真机、MB-D-012 签名仍未签收。
- 保留项：附件恢复、SAF 目标、生产自动备份触发点未接线；真机 kill/断电未验证；
  目录 fsync 为 best-effort；v1 测试夹具为反向构造。Stage 4 不得把这些保留项当作已完成。
- **G5 发布门禁**：Release keystore 未创建，不影响当前阶段。

## 文档

- [Stage 1 执行报告](docs/06-stage1/MB-S1-01-EXECUTION-REPORT.md)
- [模块调整前后清单与依赖图](docs/06-stage1/MB-S1-02-MODULE-ADJUSTMENT.md)
- [依赖检查与违规注入测试](docs/06-stage1/MB-S1-03-DEPENDENCY-CHECK.md)
- [休眠表 DORMANT 清单](docs/06-stage1/MB-S1-04-DORMANT-TABLES.md)
- [迁移准备物](docs/06-stage1/MB-S1-05-MIGRATION-READINESS.md)
- [备份/恢复缺口与方案](docs/06-stage1/MB-S1-06-BACKUP-GAP-ANALYSIS.md)
- [UI 与业务隔离基础](docs/06-stage1/MB-S1-07-UI-ISOLATION.md)
- [文档修正记录](docs/06-stage1/MB-S1-08-DOC-CORRECTIONS.md)
- [Stage 1 审查回执](docs/06-stage1/MB-S1-09-REVIEW-RECEIPT.md)
- [Stage 1 关闭记录](docs/06-stage1/MB-S1-10-STAGE1-CLOSE-RECORD.md)
- [Stage 2 执行报告（CLOSED）](docs/07-stage2/MB-S2-01-EXECUTION-REPORT.md)
- [Stage 2 领域模型说明](docs/07-stage2/MB-S2-02-DOMAIN-MODEL.md)
- [Stage 2 规则与测试矩阵](docs/07-stage2/MB-S2-03-RULES-TEST-MATRIX.md)
- [Stage 2 Repository 契约](docs/07-stage2/MB-S2-04-REPOSITORY-CONTRACTS.md)
- [Stage 2 决策映射](docs/07-stage2/MB-S2-05-DECISION-MAPPING.md)
- [Stage 2 关闭记录](docs/07-stage2/MB-S2-06-STAGE2-CLOSE-RECORD.md)
- [Schema 变更授权记录](docs/08-schema-change/MB-SC-00-AUTHORIZATION.md)
- [UI 设计书 1.0.0](docs/design/MISEBASE-UI-DESIGN-SPEC.md)
- [G2 收档文件](docs/00-governance/A-G2-001-G2-ARCHIVE.md)
- [决策登记册](docs/00-governance/DECISION-REGISTER.md)
- [MISEBASE 决策登记册](docs/05-handover/MB-S0-07-DECISION-REGISTER.md)
- [文档索引](docs/00-governance/DOCUMENT-INDEX.md)
- [G2 阶段 9 报告](docs/04-readiness/R19-G2-STAGE9-REPORT.md)

## 文档状态规则

| 状态 | 含义 | 是否可用于正式开发 |
|---|---|---|
| `Draft` | 草案，内容可能不完整 | 否 |
| `Review` | 已自检，等待负责人确认 | 仅可作为讨论依据 |
| `Approved` | 已由项目负责人明确签收 | 是 |
| `Deprecated` | 已废弃，保留追溯 | 否 |
| `Superseded` | 被新决策取代，保留原文追溯 | 否 |

任何影响模块边界、数据库结构、备份格式或锁定技术栈的修改，必须先走 ADR 与负责人签收。
