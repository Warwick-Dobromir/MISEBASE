# MISEBASE

**个人烹饪档案与库存管理系统 — Android 原生应用**

| 项目 | 内容 |
|---|---|
| 品牌/项目名 | MISEBASE（包名锁定 `com.culinary`；v1.0.0 已签名发布） |
| 架构基线 | `0.4.1`（G1 已签收）；G2 已收档于 `framework-0.1.0` / `2b523de` |
| 当前阶段 | **Stage 6 Review（2026-11-05）：v1.0.0 Release APK 已签名产出、真机升级与冒烟验证通过**；Stage 0–5 与首页 N3 导航已关闭；MB-HOME-002 导航重构已完成 |
| 工程状态 | **14 模块（Stage 5 起，MB-D-004）**；Room schema **v2 / 33 表**（11 表 `DORMANT`）；三大档案库 + Inventory + 扫码可用；N3 底部五项（首页/知识库/扫码凸起/库存/设置）与知识库聚合页已接入；**857 次执行 / 540 唯一 JVM 用例 / 0 失败**（Stage 6 发布门禁复跑；Stage 5 基线 817 / 514 唯一 JVM 用例） |
| 数据库 | **已创建**：schema v2（33 表），导出 `1.json`、`2.json`；`1→2` 迁移已落地；本次备份一致性修复未改 Schema/Entity/DAO |
| 构建基线 | AGP 8.13.0 / Kotlin 2.2.20 / Gradle 8.14.3 / JDK 21 / minSdk 26 / compile & target 36 |
| 依赖注入 | Hilt 2.57.2 + KSP（D-012 `ACCEPTED`，禁止 2.59+） |
| 离线承诺 | 无 `INTERNET` 权限（manifest 移除 + `aapt2` 已验证） |

> 历史基线：G2 收档时为 **197 例 JVM 测试**、schema **v1 / 31 表**；这些数字仅用于追溯 G2 历史，当前口径以本表为准。

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

## 14 模块（MB-D-004 ACCEPTED，Stage 5 起）

> MB-D-003 的 13 模块是 Stage 1–4 目标；MB-D-004 于 2026-11-05 签收，Stage 5 起新增第 14 个模块
> `feature:scan`（扫码 UI / CameraX / ML Kit 识别）。13 不是历史错误，14 只从 Stage 5 起成立。

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
  ingredients sauces recipes products inventory scan settings
```

`:core` 与 `:feature` 是 Gradle 结构容器（无构建脚本），不计入 14 个模块。
`feature:planning`、`feature:procurement` 已按 MB-D-003 删除；采购清单归 `feature:inventory`，
计划/采购不再作为独立模块。依赖检查实测 **14 节点 / 39 条声明边**，拓扑排序无环。

`feature:products` 的范围是**商品 / 包装规格 / 条码 / 食材映射**，为扫码与库存提供商品主数据；
**不包含供应商管理**。Stage 5 起扫码 UI / 相机 / 识别归 `feature:scan`（只依赖 `core:domain`、
`core:common`、`core:designsystem`），识别结果通过明确接口交给业务模块：
食材创建/映射归 `feature:products`，库存入库归 `feature:inventory`。

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

## 决策状态（2026-11-05）

已接受（15/18，负责人授权，见 [S0-05](docs/05-handover/S0-05-OWNER-SIGNOFF.md)）：

- MB-D-001 接管基线与项目身份；
- MB-D-002 范围冻结（五项功能；采购清单归 Inventory）；
- MB-D-003 Stage 1–4 目标 13 模块；
- MB-D-004 扫码 UI 归属：新建独立 `feature:scan`；Stage 5 起模块集为 14；
- MB-D-005 11 张休眠表保留并标 `DORMANT`；
- MB-D-007 备份一致性（**已 CLOSED，2026-11-04 审查通过**；Stage 4 前置已满足）；
- MB-D-008 UI 视觉方向 A（**Rev.1：Champagne Gold 正式替换 Orange，2026-11-05**）；
- MB-D-009 导航 **N3**（首页/知识库/扫码凸起/库存/设置；设置入底栏；知识库聚合；2026-11-05 Rev.2）；
- MB-D-011 真机验证设备：至少一台真机，API 26+（推荐 33+）；模拟器不可替代；保留测试记录；
- MB-D-013 子配方版本固定（发布时写入明确版本 ID）；
- MB-D-014 库存扣减模式（先预览、确认后写流水）；
- MB-D-015 可选配料缺料口径（默认不计缺口，详情可见）；
- MB-D-016 采购清单形态（不落 `purchase_order*` 表）；
- MB-D-017 计算记录快照（确认后不可变）；
- MB-D-018 纯文档修正授权。

已全部 `ACCEPTED`（2026-11-05，Stage 6 签收）：MB-D-006 测试口径（JVM + 真机、结果清单）、
MB-D-010 CI（不采用远端 CI，本地构建与验收）、MB-D-012 发布签名（独立 keystore + 仓库外双备份）。

> 2026-11-05 补记：MB-D-004（新建 `feature:scan`，Stage 5 起 14 模块）与 MB-D-011（真机验证要求）
> 已由负责人签收为 `ACCEPTED`。本次签收**不创建 `feature:scan`、不改模块集、不构成 Stage 5
> 开发或真机测试授权**；Stage 5 仍需单独任务书。

> 历史记录：本次签收之前，MB-D-007 仍为 `PROPOSED`；2026-11-04 已由负责人签收为 `ACCEPTED`，
> 并作为 Stage 4 强制前置；同日完成 MB-BK-001/002 并通过审查，状态更新为 **`CLOSED`**。

> G2 历史决策 `D-005`/`D-007` 与 MISEBASE 的 `MB-D-005`/`MB-D-007` 不是同一体系，禁止互相替代。
> G2 的 `ACCEPTED` 不自动构成对应 `MB-D-*` 的 `ACCEPTED`；MB-D 状态以本仓库签收记录为准。

## 当前状态与未决

- **Stage 6 发布已完成（2026-11-05，Review）**：`v1.0.0`（`versionCode=1`）Release APK 已用独立
  `release.keystore` 签名并通过 `apksigner` 校验（v2，证书 `5C:A4:19:59:…:AF:61`）；
  新增自适应桌面图标（`mipmap-anydpi-v26` + 5 密度 PNG 兜底）并真机验证；
  真机 `adb install -r` 覆盖升级后 2 食材 / 2 商品 / 3 批次 / 3 流水完整保留（批次 UUID 逐条一致）；
  全量门禁 `test assembleRelease ktlintCheck detekt` 通过（857 执行 / 540 唯一 / 0 失败）。
  详见 [执行报告](docs/14-stage6/MB-S6-01-EXECUTION-REPORT.md)、
  [图标说明](docs/14-stage6/MB-S6-02-APP-ICON.md)、
  [签名与 Keystore](docs/14-stage6/MB-S6-03-RELEASE-SIGNING.md)、
  [升级数据安全](docs/14-stage6/MB-S6-04-UPGRADE-DATA-SAFETY.md) 与
  [发布构建指南](docs/14-stage6/RELEASE-BUILD-GUIDE.md)。
  **未新增业务功能、未改 Schema/Entity/DAO。**
- **MB-HOME-002 导航重构已完成（2026-11-05，Review）**：N3 底部五项为首页/知识库/扫码（中央凸起）/库存/设置；设置从顶栏移到底栏；知识库聚合酱汁/食材/菜谱三个 Tab，底层三个 feature 模块与详情/编辑路由均未合并；香槟金已正式批准（MB-D-008 Rev.1）。冷启动仍进入首页，真实数据与隐藏模块策略不变。详见
  [MB-HOME-002 执行报告](docs/13-home/MB-HOME-002-EXECUTION-REPORT.md)、[决策记录](docs/13-home/MB-HOME-002-DECISIONS.md) 与 [真机截图](docs/13-home/MB-HOME-002-device-home.png)。
  **未新增模块、未改 Schema；测试通过。**
- **MB-HOME-001 独立首页已完成（2026-11-05，已由 MB-HOME-002 验收）**：数据源、隐藏模块与香槟金 token 见
  [MB-HOME-001 报告](docs/13-home/MB-HOME-001-EXECUTION-REPORT.md)；其 N1 导航截图仅作历史证据。
- **G2 阶段 1–9** 已完成并归档；`framework-0.1.0` 固定在 `2b523de`，未移动。
- **Stage 0/1/2/3/4 均已关闭**；Schema 变更任务已关闭（v2 / 33 表）；三大档案库与 Inventory 可用。
- **Stage 4 状态为 CLOSED（2026-11-05，有条件通过）**：库存总览/详情/入库/出库/调整/流水、需求汇总与缺口、采购清单 CSV、计算记录回放、酱汁图展开、编辑器组成行只读保护已完成；详见
  [Stage 4 关闭记录](docs/11-stage4/MB-S4-CLOSE-RECORD.md)。
- **Stage 5 状态为 CLOSED（2026-11-05）**：新建第 14 个模块 `feature:scan`，
  扫码页面（CameraX 预览、扫描框、手电筒、返回、权限说明与手工输入兜底）、ML Kit Bundled 条码/二维码识别、
  相同码去抖与一次性消费、识别结果接口（`feature:products` / `feature:inventory`）、导入确认与幂等已落地；
  扫码入口已从占位切换为真实页面。**MB-S5-002 已修正"同一商品只能导入一次"**：幂等键改为事件级
  `scan-import:<条码>:<operation_id>`，同一次确认重试复用同一 id、不同次确认生成新 id。
  真机验证记录见 [MB-S5-02](docs/12-stage5/MB-S5-02-DEVICE-VERIFICATION.md)，
  修正记录见 [MB-S5-08](docs/12-stage5/MB-S5-08-IDEMPOTENCY-FIX.md)，
  关闭记录见 [MB-S5-CLOSE](docs/12-stage5/MB-S5-CLOSE-RECORD.md)。
- **MB-D-007 已 CLOSED（2026-11-04，审查通过）**：六项技术事项、Schema 版本阻断修复、
  `612 / 376 / 0` 基线测试与 v2 检查点均已落库；详见
  [MB-D-007 关闭记录](docs/10-backup/MB-D-007-CLOSE-RECORD.md)。
- **模块集为 14**：`feature:scan` 已创建并接入 N1 第五入口；依赖检查实测 14 节点 / 39 条声明边，无环。
- **发布决策已全部签收（2026-11-05）**：MB-D-006 测试口径、MB-D-010 本地构建、MB-D-012 发布签名；MISEBASE 决策 18/18 `ACCEPTED`。
- 保留项：附件恢复、SAF 目标、生产自动备份触发点未接线；真机 kill/断电未验证；
  目录 fsync 为 best-effort；v1 测试夹具为反向构造；二维码"相机取景"未人工复测（解码能力已在真机验证）。
- **G5 发布门禁**：已满足——Release keystore 位于仓库外 `D:\MISEBASE_RELEASE\`，Release APK 已签名（见 [MB-S6-03](docs/14-stage6/MB-S6-03-RELEASE-SIGNING.md)）。

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
- [Stage 4 执行报告（CLOSED）](docs/11-stage4/MB-S4-01-EXECUTION-REPORT.md)
- [Stage 4 关闭记录](docs/11-stage4/MB-S4-CLOSE-RECORD.md)
- [MB-D-004 / MB-D-011 签收授权记录](docs/12-stage5/MB-D-004-011-AUTHORIZATION.md)
- [Stage 5 执行报告（Review）](docs/12-stage5/MB-S5-01-EXECUTION-REPORT.md)
- [Stage 5 扫码页面说明](docs/12-stage5/MB-S5-03-SCAN-PAGES.md)
- [Stage 5 识别接口与导入确认](docs/12-stage5/MB-S5-04-IMPORT-CONTRACT.md)
- [Stage 5 测试矩阵](docs/12-stage5/MB-S5-05-TEST-MATRIX.md)
- [Stage 5 决策映射（MB-D-004 / MB-D-011）](docs/12-stage5/MB-S5-06-DECISION-MAPPING.md)
- [Stage 5 真机验证记录](docs/12-stage5/MB-S5-02-DEVICE-VERIFICATION.md)
- [Stage 5 重复入库修正（MB-S5-002）](docs/12-stage5/MB-S5-08-IDEMPOTENCY-FIX.md)
- [Stage 4 库存页面说明](docs/11-stage4/MB-S4-02-INVENTORY-PAGES.md)
- [Stage 4 需求汇总与缺口](docs/11-stage4/MB-S4-03-REQUIREMENT-GAP.md)
- [Stage 4 采购清单](docs/11-stage4/MB-S4-04-PURCHASE-LIST.md)
- [Stage 4 计算记录](docs/11-stage4/MB-S4-05-CALCULATION-RECORD.md)
- [Stage 4 测试矩阵](docs/11-stage4/MB-S4-08-TEST-MATRIX.md)
- [UI 设计书 1.4.0](docs/design/MISEBASE-UI-DESIGN-SPEC.md)
- [MB-HOME-001 首页执行与验收报告](docs/13-home/MB-HOME-001-EXECUTION-REPORT.md)
- [MB-HOME-002 导航重构执行报告](docs/13-home/MB-HOME-002-EXECUTION-REPORT.md)
- [MB-HOME-002 决策记录（香槟金 / N3）](docs/13-home/MB-HOME-002-DECISIONS.md)
- [MB-HOME-002 首页真机截图](docs/13-home/MB-HOME-002-device-home.png)
- [Stage 6 发布与交付执行报告](docs/14-stage6/MB-S6-01-EXECUTION-REPORT.md)
- [Stage 6 桌面图标设计](docs/14-stage6/MB-S6-02-APP-ICON.md)
- [Stage 6 发布签名与 Keystore](docs/14-stage6/MB-S6-03-RELEASE-SIGNING.md)
- [Stage 6 升级与数据安全验证](docs/14-stage6/MB-S6-04-UPGRADE-DATA-SAFETY.md)
- [Stage 6 全量测试结果清单](docs/14-stage6/MB-S6-06-TEST-RESULTS.md)
- [发布构建指南（换机可复现）](docs/14-stage6/RELEASE-BUILD-GUIDE.md)
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

