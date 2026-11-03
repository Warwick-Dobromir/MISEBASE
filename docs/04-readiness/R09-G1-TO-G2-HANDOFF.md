# CULINARY G1 至 G2 最终交接说明

**文档编号**：R09
**版本**：`1.0.0`
**状态**：`Review`（交接材料；G1 已签收）
**日期**：2026-11-04
**交接方向**：旧 G1 工作区 → 新 G2 开发工作区
**G1 状态**：`ACCEPTED`（项目负责人正式签收）
**G2 状态**：`WAITING AUTHORIZATION`（等待负责人授权新工作区启动）

---

## 0. 如何使用本说明

新工作区应先读本文件，再按第 9 节索引打开正式文档。**不要仅凭本说明开始编码**；涉及数据、备份、模块依赖时，必须回到对应正式文档。

本说明不替代正式交付物，也不重新审查 G1。G1 已签收，任何架构变更都必须走新的 ADR/变更流程，不能在 G2 中静默修改。

## 1. 项目定位

CULINARY 是**专业个人烹饪档案与研发管理系统**，面向单一用户、Android 原生、离线优先、长期可维护。

它要解决的不是“找菜谱”，而是：

- 把食材、酱汁、菜谱作为可版本化的知识资产管理；
- 把菜谱展开为可执行的制作计划、需求、采购和库存动作；
- 在离线、单机、无云账号的前提下保证数据不丢、可迁移、可恢复；
- 让配方/酱汁的历史版本可追溯，计划确认后不被后续编辑篡改。

核心约束：

- 单用户、单设备为主；不做账号、云同步、多人协作、AI 生成核心。
- 离线可用；核心功能不依赖网络。
- 图片可选，不生成空占位图。
- 食材与商品分离；知识库只有食材、酱汁、菜谱三类。
- 所有业务数据本地持久化；备份/恢复是正式能力，不是附属功能。

## 2. G1 正式签收记录

| 项 | 记录 |
|---|---|
| G1 结论 | **ACCEPTED**（项目负责人正式签收） |
| 签收日期 | 2026-11-04 |
| 8 项关键决策 | D-001/002/003/004/006/008/009/011 全部 `ACCEPTED` |
| E05 | `0.4.1` / `Approved` |
| D-004 | 15 模块架构，`ACCEPTED` |
| D-011 | 备份恢复方案，`ACCEPTED` |
| 正式工程 | 未创建（G2 范围） |
| Release keystore | 未创建（G5 发布门禁） |
| G2 | 等待负责人授权 |

**G1 最终归档/交接包**：`CULINARY-G1-TO-G2-HANDOFF-20261104.zip`（本说明随包提供；SHA-256 见包外 `.sha256`）。

> 历史上生成过 `CULINARY-G1-FINAL-ARCHIVE-20261104.zip` 和更早的候选归档；它们反映 G1 签收前的状态，已由本交接包取代。不得用旧包证明 G1 当前状态。

## 3. 技术栈

| 层 | 选择 | 版本/约束 |
|---|---|---|
| 语言 | Kotlin | 2.2.20 |
| UI | Jetpack Compose + Material 3 | Compose BOM 2025.10.01 |
| 架构 | 单 Activity + Navigation Compose | Navigation 2.9.8 |
| 数据库 | Room + Android 平台 SQLite | Room 2.8.5；WAL；单进程单写者 |
| DI | Hilt + KSP | Hilt 2.57.2；KSP 2.2.20-2.0.4；**2.59+ 要求 AGP 9，禁止升级** |
| 构建 | Gradle + AGP | Gradle 8.14.3；AGP 8.13.0 |
| JDK | JDK 21 | Android Studio JBR 21.0.8 |
| SDK | min 26 / compile & target 36 | build-tools 36.1 |
| 扫码 | CameraX + ML Kit Bundled | CameraX 1.4.2；ML Kit barcode-scanning 17.3.0 |
| 图片 | Coil 3 | 3.3.0 |
| 后台 | WorkManager | 2.10.5 |
| 序列化 | kotlinx.serialization | 1.9.0 |
| 日志 | Timber | 5.0.1 |
| 测试 | JUnit4 / Truth / MockK / Turbine / Robolectric / Compose UI Test / MigrationTestHelper | 版本见 R02 |

**已验证事实：**

- V01 已用真实构建验证依赖解析、`assembleDebug`、模拟器 `adb install` 与 Activity 启动。
- 设备端无 `INTERNET` 权限（通过 manifest 移除规则 + `aapt2`/`dumpsys` 验证）。
- Hilt 2.59.2 实测与 AGP 8.13 不兼容；当前锁定 2.57.2。

## 4. 15 模块架构（D-004，不可推翻）

```text
app
core:common
core:domain          # model / ports / usecases / rules
core:database
core:data            # repository 实现、备份引擎
core:designsystem
core:platform        # SAF、权限、CameraX、WorkManager
feature:ingredients
feature:sauces
feature:recipes
feature:products
feature:inventory
feature:planning
feature:procurement
feature:settings
```

| 模块 | 职责 |
|---|---|
| `app` | Application、MainActivity、导航图、DI 聚合、启动协调 |
| `core:common` | `Outcome`、错误模型/错误码、`Clock`、`IdGenerator`、`DispatcherProvider`、日志接口 |
| `core:domain` | 领域模型、Repository Port、Use Case、领域规则；**含 `model` 包** |
| `core:database` | Room Entity/DAO/Database、Migration、Schema、事务基础设施 |
| `core:data` | Repository 实现、Mapper、事务协调、备份格式/校验/打包/解包/恢复编排 |
| `core:designsystem` | 主题、颜色、排版、通用组件；不依赖 feature |
| `core:platform` | SAF、系统文件选择器、权限、CameraX、ML Kit、WorkManager、系统分享 |
| `feature:ingredients` | 食材库、别名、单位换算入口、食材图片 |
| `feature:sauces` | 酱汁库、版本、组成、发布 |
| `feature:recipes` | 菜谱库、版本、组成、循环校验、展开、图片 |
| `feature:products` | 商品、条码、供应商、商品-食材关联 |
| `feature:inventory` | 批次、位置、流水、预留、盘点 |
| `feature:planning` | 制作计划、确认、快照、需求、缺口 |
| `feature:procurement` | 采购单、采购明细、收货 |
| `feature:settings` | 设置、备份/恢复入口、关于、诊断 |

**依赖方向（必须保持）：**

```text
app -> feature -> core:domain <- core:data -> core:database
app -> core:platform / core:designsystem / core:common
```

- `feature` 之间**零依赖**；需要交互时通过 `core:domain` 的 Port/Use Case 或 `app` 组合。
- `core:domain` 不依赖 Android/Room/Compose。
- `core:database` 不依赖 `core:domain`。
- `core:data` 不直接依赖 Android SAF，不使用 `Uri` 作为领域接口；通过 `BackupStoragePort`/`StorageHandle` 桥接 `core:platform`。
- 15 节点、43 条依赖边，已做 DAG/拓扑检查；证据：`docs/02-architecture/module-dependency-check.txt`。

**绝对不要创建 `core:model` 独立模块**；领域模型统一在 `:core:domain` 的 `model` 包。

## 5. 核心业务体系

### 5.1 知识体系

- **食材库**：食材、别名、单位/换算、分类、图片；是配方与商品的基础。
- **酱汁库**：酱汁、版本、组成、发布；版本不可变，已发布版本被引用后不能改。
- **菜谱库**：菜谱、版本、组成（食材/酱汁/子菜谱）、步骤、图片、循环检测、展开。
- 酱汁与菜谱**保持独立 feature**，通过 domain 端口和版本引用交互。

### 5.2 执行体系

- **商品**：商品、条码、供应商、商品-食材关联；与食材分离。
- **库存**：批次、储存位置、追加式流水、预留；禁止直接改库存数字。
- **制作计划**：计划、计划项、固定 `recipe_version_id`、确认快照、需求汇总、缺口。
- **采购**：采购单、明细、收货；收货生成批次与流水。

### 5.3 连接关系

```text
食材 1--N 商品
商品 1--N 批次 1--N 流水/预留
菜谱/酱汁 1--N 版本 1--N 组成
计划项 N--1 菜谱版本
计划确认 -> 不可变快照 -> 需求 -> 缺口 -> 采购 -> 收货 -> 库存
```

## 6. 已接受决策（G1）

| 编号 | 决策 | 状态 |
|---|---|---|
| D-001 | 原生 Android；Kotlin + Compose；不跨平台 | ACCEPTED |
| D-002 | min 26 / compile & target 36 | ACCEPTED |
| D-003 | Room + 平台 SQLite；WAL；单进程单写者；不 SQLCipher | ACCEPTED |
| D-004 | 15 模块；model 并入 domain；sauces/recipes、products/inventory 独立；feature 零依赖 | ACCEPTED |
| D-006 | 菜谱/酱汁不可变已发布版本 + 当前版本指针 | ACCEPTED |
| D-008 | 严格单位维度；跨维度只允许食材级显式换算；无依据拒绝 | ACCEPTED |
| D-009 | 批次 + 位置 + 追加式流水 + 预留；禁止直接改数 | ACCEPTED |
| D-011 | ZIP + manifest + SHA-256 + 可选 AES-GCM；E05 0.4.1 | ACCEPTED |
| D-014 | 本阶段不做云账号、同步、协作、AI 核心 | CONFIRMED |

完整记录：`docs/00-governance/DECISION-REGISTER.md`（0.7.0）。

## 7. 尚待负责人确认的决策

> 这些**不是 G1 阻塞项**，G1 已签收；但必须在对应 G2 阶段前由负责人确认。**不得擅自改为 ACCEPTED。**

| 编号 | 内容 | 当前状态 | 最迟确认阶段 |
|---|---|---|---|
| **D-012** | Hilt 2.57.2 + KSP | PROPOSED | **G2 阶段 1（工程骨架）前** |
| **D-005** | UUID v4 TEXT 主键 | PROPOSED | **DB v1（阶段 3）前** |
| **D-007** | 计划确认生成不可变快照 | PROPOSED | **制作计划功能（阶段 6）前** |
| D-013 | CameraX + ML Kit 扫码 | PROPOSED | 平台能力（阶段 5）前 |
| D-015 | 应用私有文件 + 元数据表 | PROPOSED | 图片/文件功能前 |
| D-020 | 有库存商品只能归档 | PROPOSED | 商品/库存功能前 |
| D-021 | 已确认计划刷新生成新快照 | PROPOSED | 制作计划功能前 |
| D-025 | 配方图深度 20 / 节点 500 | PROPOSED | 配方算法正式实现前 |

**D-012 是关键路径：** 未接受前，不得创建 Hilt 骨架。A01/V01 只证明“能构建”，不等于负责人已接受。

其他 `OPEN` 产品规则（预留时机、数据库加密、包名、可选配料、超量收货、搜索、真机、语言）已有默认值或可延后；见 DECISION-REGISTER。

## 8. G2 工作范围

G2 是**框架搭建**，不是完整产品功能开发。按 `R01` 阶段 0-9 执行：

| 阶段 | 内容 | 退出条件 |
|---|---|---|
| 0 | 环境门禁（Debug） | 依赖解析、空工程 `assembleDebug`、模拟器安装/启动 |
| 1 | 工程骨架 | 15 模块、版本目录、约定插件、`projects` 正确、依赖规则 |
| 2 | 核心契约 | `core:common`、`core:domain` 模型/端口/Use Case、纯 JVM 测试 |
| 3 | 数据库 v1 | Room v1、核心表、迁移框架、DAO 测试、单写者/事务 |
| 4 | 首个纵向切片 | 食材列表/详情/编辑、图片可选、导航/主题/错误 |
| 5 | 平台能力 | 扫码、SAF、权限、WorkManager 最小实现 |
| 6 | 库存骨架 | 批次/流水/预留/条件更新 |
| 7 | 计划/采购骨架 | 快照、需求、缺口、采购收货 |
| 8 | 备份/恢复骨架 | E05 0.4.1 的 manifest/校验/journal/恢复 |
| 9 | 质量门禁 | CI、迁移、备份冒烟、离线、Release/R8 构建验证 |

**G2 第一项动作**（按 R01 §4）：创建 15 模块骨架 + `build-logic` + version catalog；不先做完整 UI。

**G2 不做：** 所有 feature 的完整 UI、最终视觉、性能极致、应用商店发布、正式 Release 签名/keystore。

## 9. 当前项目文件清单与索引

### 9.1 17 项正式交付物（18 个文件）

| # | 文件 | 版本 | 状态 |
|---:|---|---|---|
| 1 | [P01 产品需求基线](../01-product/P01-PRODUCT-BASELINE.md) | 0.1.0 | Review |
| 2 | [P02 需求与待确认清单](../01-product/P02-REQUIREMENTS-OPEN-ITEMS.md) | 0.1.0 | Review |
| 3 | [A01 技术选型](../02-architecture/A01-TECH-STACK-DECISION.md) | 0.2.0 | Review |
| 4 | [A02 整体架构](../02-architecture/A02-SYSTEM-ARCHITECTURE.md) | 0.2.0 | Review |
| 5 | [A03 模块与依赖](../02-architecture/A03-MODULE-DEPENDENCIES.md) | 0.3.0 | Review |
| 6 | [A04 业务流程](../02-architecture/A04-BUSINESS-FLOWS.md) | 0.1.0 | Review |
| 7 | [A05 ER 图](../02-architecture/A05-DATA-MODEL-ER.md) | 0.1.0 | Review |
| 8 | [A06 数据字典](../02-architecture/A06-DATA-DICTIONARY.md) | 0.1.0 | Review |
| 9 | [A07 版本与迁移](../02-architecture/A07-VERSIONING-MIGRATION.md) | 0.1.0 | Review |
| 10 | [E01 代码与目录规范](../03-engineering/E01-CODE-DIRECTORY-STANDARDS.md) | 0.2.0 | Review |
| 11 | [E02 并发与事务](../03-engineering/E02-DATABASE-CONCURRENCY.md) | 0.2.0 | Review |
| 12 | [E03 异常与日志](../03-engineering/E03-ERROR-LOGGING.md) | 0.2.0 | Review |
| 13 | [E04 Git 与回档](../03-engineering/E04-GIT-ROLLBACK.md) | 0.2.0 | Review |
| 14 | [E05 备份与恢复](../03-engineering/E05-BACKUP-RESTORE.md) | 0.4.1 | **Approved** |
| 15 | [E06 测试与验收](../03-engineering/E06-TESTING-ACCEPTANCE.md) | 0.1.0 | Review |
| 16 | [R01 框架实施顺序](R01-FRAMEWORK-SEQUENCE.md) | 0.2.2 | Review |
| 17 | [R02 环境与依赖](R02-ENVIRONMENT-DEPENDENCIES.md) | 0.2.0 | Review |
| 18 | [R03 前置检查](R03-PREFLIGHT-CHECKLIST.md) | 0.5.0 | Review |
| — | [R04 框架制作启动报告](R04-FRAMEWORK-START-REPORT.md) | 0.7.0 | Review |

### 9.2 治理/复核材料

| 文件 | 版本 | 用途 |
|---|---|---|
| [DECISION-REGISTER](../00-governance/DECISION-REGISTER.md) | 0.7.0 | 全部决策与签收记录 |
| [R05 G1 复核包](G1-REVIEW-PACKET.md) | 0.5.0 | D-004/D-011/G5 复核记录 |
| [R06 归档核验](G1-ARCHIVE-AUDIT.md) | 0.3.0 | 8 项核验与 G2 状态 |
| [R07 材料报告（历史）](R07-FINAL-MATERIALS-REPORT.md) | 0.2.0 | Deprecated，历史 |
| [R08 定点修订报告（历史）](R08-FINAL-REVISION-REPORT.md) | 0.2.0 | Deprecated，历史 |
| [R09 本交接说明](R09-G1-TO-G2-HANDOFF.md) | 1.0.0 | 当前交接材料 |
| [README](../../README.md) | — | 仓库入口 |
| [DELIVERY-MANIFEST](../00-governance/DELIVERY-MANIFEST.md) | 0.7.0 | 交付物与 SHA-256 |
| [DOCUMENT-INDEX](../00-governance/DOCUMENT-INDEX.md) | 0.6.0 | 文档导航 |
| [A03A 模块拆分审查](../02-architecture/A03A-MODULE-DECOMPOSITION-REVIEW.md) | 0.3.0 | D-004 证据 |
| [E05 0.4.1 自检报告](../03-engineering/E05-0.4.1-REVISION-REPORT.md) | 0.1.0 | E05 修订证据 |
| [E05A 修订复核](../03-engineering/E05-REVISION-REVIEW.md) | 0.1.0 | E05 五项修订背景 |
| [V01 依赖验证](V01-DEPENDENCY-VALIDATION.md) | 0.1.0 | 构建/安装/权限证据 |
| [模块 DAG 检查](../02-architecture/module-dependency-check.txt) | — | 15 节点/43 边 |

## 10. 不得擅自修改的架构规则

1. **D-004 15 模块**：不新增/删除/合并模块；不创建 `core:model`。
2. **feature 零依赖**：feature 之间不能直接依赖；通过 domain 端口或 app 组合。
3. **domain 纯度**：`core:domain` 不依赖 Android/Room/Compose。
4. **database 不依赖 domain**：Room 实体属于 database；domain 不引用 Room。
5. **data/platform 备份边界**：`core:data` 不直接用 SAF/`Uri`；`core:platform` 实现 `BackupStoragePort`。
6. **单写者**：所有写入经 `TransactionRunner`/`WriteGate`；禁止绕过 `writeMutex`。
7. **库存不可直接改数**：只能追加流水/预留/收货。
8. **已发布版本不可变**：菜谱/酱汁版本发布后不可编辑；新编辑产生新版本。
9. **计划快照不可变**：确认后刷新生成新快照，不原地改。
10. **E05 备份格式**：不重设 manifest/哈希/journal；0.4.1 已签收。
11. **无云/无 AI 核心**：本阶段不引入账号、同步、多人协作、AI 生成核心。
12. **无网络权限**：保持离线；新增依赖若带入 `INTERNET`，必须用 manifest 移除并验证。

任何违反以上规则的改动，先提 ADR，不得在 G2 中静默实施。

## 11. G2 启动前置条件

| # | 条件 | 当前 |
|---:|---|---|
| 1 | G1 正式签收 | **ACCEPTED** |
| 2 | 8 项 G1 决策接受 | **完成** |
| 3 | E05 0.4.1 Approved | **完成** |
| 4 | 17 项交付物齐全 | **完成** |
| 5 | 环境/JDK/SDK/依赖/模拟器 | **完成**（V01） |
| 6 | **D-012 Hilt 接受** | **PENDING** |
| 7 | D-005 主键策略接受 | PENDING（DB v1 前） |
| 8 | D-007 计划快照接受 | PENDING（计划模块前） |
| 9 | G2 开工授权 | **WAITING AUTHORIZATION** |
| 10 | Release keystore | 未创建（G5 发布门禁） |

**G2 可以准备，但不能启动**，直到：

1. 项目负责人接受 D-012；
2. 项目负责人明确授权 G2；
3. 新工作区确认本交接包与正式文档一致。

D-005 可在阶段 1-2 之后、DB v1 前接受；D-007 在计划模块前接受。

## 12. 当前未创建的工程及原因

**正式 Android 工程未创建。**

原因：

- G1 阶段只做架构/工程准备，禁止提前写产品代码；
- G2 尚未获得负责人授权；
- 本交接任务也明确禁止创建正式工程；
- 提前创建会绕过 D-012/G2 授权门禁，并可能把未接受的技术选择固化。

**Release keystore 未创建。**

原因：G5 是发布门禁，不是 G1/G2 阻塞项；只允许在发布准备阶段创建、备份、验证指纹。当前不得创建。

**未执行测试/真机验证。**

E06/E05 是测试计划；尚未执行。V01 只验证了依赖构建/安装/启动，不是真机验证，也不是功能测试。

## 13. 交接后的第一步（给新工作区）

1. 解压 `CULINARY-G1-TO-G2-HANDOFF-20261104.zip`，核对 `ARCHIVE-MANIFEST.txt` 的 SHA-256。
2. 读本说明、`DECISION-REGISTER`、`R01`、`R06`、`R08`（历史）。
3. 向项目负责人确认：
   - D-012 是否接受；
   - 是否授权 G2；
   - D-005 是否在 DB v1 前接受。
4. 获得授权后，按 `R01` 阶段 1 创建 15 模块骨架。
5. 未获授权前，只做环境/资料准备，不创建正式工程。

## 14. 当前状态一句话

**G1 已正式签收；技术基线、15 模块、E05 0.4.1、17 项交付物已冻结；D-012/D-005/D-007 仍为 PROPOSED；G2 等待负责人授权，正式工程尚未创建。**

## 15. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0.0 | 2026-11-04 | 建立 G1→G2 自包含交接说明；记录 G1 ACCEPTED、待办决策、G2 范围与前置条件 | 架构组 |
