# MB-S2-01 Stage 2 执行报告

**文档编号**：MB-S2-01
**版本**：`1.1.1`
**状态**：`CLOSED`
**日期**：2026-11-04
**阶段**：Stage 2（核心数据模型与业务规则）
**依据**：MB-S2-001；决策映射见 [MB-S2-05](MB-S2-05-DECISION-MAPPING.md)
**执行前 HEAD**：`99223000d34b15abe3fa8020d5cb7f06ecaab8cf`
**检查点**：`checkpoint/misebase-stage2-20261104`（对象哈希与解析见 §8.2）
**审查结论**：有条件通过（主体合规，无重大范围偏离）；关闭记录见 [MB-S2-06](MB-S2-06-STAGE2-CLOSE-RECORD.md)

---

## 1. 执行摘要

Stage 2 在**不修改 Schema v1、不修改 Entity 字段、不新增表、不改备份/恢复**的前提下，建立了
核心领域模型、单位换算与配方展开规则、子配方版本固定、库存预览/确认扣减、可选配料缺料、
采购清单、计算记录快照，以及 Repository 契约、fake 与两项基础实现。

| 项 | 结果 |
|---|---|
| 代码提交 | 7 个（领域模型 / 单位与展开 / 版本固定 / 库存 / 缺料与采购 / 计算记录 / Repository） |
| 新增领域模型与值对象 | 19 个文件（含酱汁、库存、需求/缺口、计算记录） |
| 新增业务规则 | 6 组（单位展示精度、份数缩放、版本固定、库存扣减、缺料口径、采购清单） |
| Repository | 4 个端口 + 2 个基础实现 + 4 个 fake + 契约测试 |
| JVM 测试 | **264 例 / 0 失败**（G2 基线 197 + Stage 2 新增 67） |
| Debug APK | 构建成功（`app/build/outputs/apk/debug/app-debug.apk`，36,856,488 字节） |
| Schema / Entity / 迁移 | **0 变更**；Schema 仍 v1 / 31 表；11 张休眠表 0 新引用 |
| 备份/恢复 | **0 变更** |
| Compose / 导航 / 扫码 UI | **0 变更**；未新建模块 |
| 决策状态 | 执行时 10/18 `ACCEPTED`、8 项 `PROPOSED`；后续签收后为 13/18、5 项 |
| 报告状态 | **`CLOSED`**（2026-11-04 关闭；见 [MB-S2-06](MB-S2-06-STAGE2-CLOSE-RECORD.md)） |

**需要负责人裁决的两项 Schema 变更请求**（本阶段均未实施，只做到 DTO/契约）：

1. **计算记录持久化**（MB-D-017）：Schema v1 无计算记录表，按任务书 §4.8 只实现领域 DTO 与
   `CalculationRecordRepository` 契约，**不建表、不迁移**。
2. **配方份数持久化**：`recipe_version` 无 `servings` 列；领域 `RecipeVersion.servings` 在读取时
   按“整批 = 1 份”处理。自定义份数需要 Schema 变更授权。

## 2. 启动前置检查与决策状态

### 2.1 前置检查

| # | 检查项 | 结果 | 证据 |
|---:|---|---|---|
| 1 | 工作树 clean | PASS | `git status --porcelain` 空输出 |
| 2 | HEAD 等于或严格后继于 stage2-start | PASS | HEAD `9922300` 严格后继 `b6a6d80`，领先 1 个签收提交 |
| 3 | 执行时 10 项决策为 ACCEPTED | PASS | S0-05 §7、MB-S0-07 §5、README、MB-S1-10 §4 |
| 4 | 执行时 8 项 PROPOSED 边界生效 | PASS | MB-D-007/008/009/004 对应实现均未触碰；MB-D-006/010/011/012 未写成已批准 |
| 5 | 模块集仍为 13 | PASS | `settings.gradle.kts` 未修改 |
| 6 | Schema 仍 v1，无 v2/迁移 | PASS | `schemas/` 无变更；`1.json` 未修改 |

### 2.2 决策状态（2026-11-04）

| 状态 | 决策 |
|---|---|
| `ACCEPTED`（10） | MB-D-001、002、003、005、013、014、015、016、017、018 |
| `PROPOSED`（8） | MB-D-004、006、007、008、009、010、011、012 |

> 本表为 Stage 2 **执行时快照**。2026-11-04 后续签收 MB-D-008（A）、MB-D-009（N1）及
> MB-D-007（备份一致性，Stage 4 强制前置）后，当前计数为 **13/18 ACCEPTED、5 项 PROPOSED**
> （见 [MB-S2-06](MB-S2-06-STAGE2-CLOSE-RECORD.md) §3）。

本阶段实现**只依赖 10 项已接受决策**；`PROPOSED` 项未被写成已批准，也未据其实现视觉、导航、
扫码 UI 或备份一致性修复。

## 3. 执行前 Git 状态与标签解析

| 项 | 值 |
|---|---|
| 工作区 | `D:\MISEBASE` |
| 分支 | `misebase/main` |
| 执行前 HEAD | `99223000d34b15abe3fa8020d5cb7f06ecaab8cf` |
| 工作树 | clean |
| `checkpoint/misebase-stage2-start-20261104` | tag 对象 `c6fa133d460de4edfe8fb4d05e57d71d989ac869` → commit `b6a6d8038e01e888f84b193a4a0f5e34893db3a6` |
| `framework-0.1.0` | `2b523de4b87e37a2eb08b4c7c851549350ad811a`（未移动） |
| 模块集 | 13 |

## 4. 领域模型

完整清单、字段与不变量见 [MB-S2-02](MB-S2-02-DOMAIN-MODEL.md)。要点：

| 分组 | 新增类型 |
|---|---|
| 标识与值对象 | `ProductId`、`BatchId`、`LocationId`、`LedgerEntryId`、`CalculationRecordId`、`SauceId`、`SauceVersionId`、`Servings`、`DisplayQuantity` |
| 酱汁 | `Sauce`、`SauceVersion`、`SauceComponent` |
| 库存 | `InventoryItem`（批次级，可用量 = 在手 − 预留）、`InventoryLedgerEntry`、`LedgerEntryType` |
| 需求与缺口 | `RequirementLine`、`ShortfallLine`、`ProductRequirement` |
| 采购 | `PurchaseList`、`PurchaseListLine` |
| 计算记录 | `CalculationRecord`、`CalculationRecordStatus` |
| 草稿与固定 | `RecipeVersionDraft`、`RecipeComponentDraft`、`ComponentTargetDraft` |

既有模型仅一处扩展：`RecipeVersion` 增加 `servings: Int = 1`（默认“整批 = 1 份”），不改变既有
调用方语义。未新增数据库表，未修改任何 Entity 字段。

## 5. 业务规则实现

| 规则 | 实现 | 关键行为 |
|---|---|---|
| 单位换算（D-008） | `UnitConverter` | 同维度按 `baseFactor`；COUNT 不互转；跨维度仅食材级显式换算；失败 `CUL-UNIT-001/002` |
| 精度与舍入 | `UnitConverter.display/convertForDisplay` | 计算保精度；展示按目标单位 `displayPrecision` HALF_UP；近似单位打标 |
| 配方展开 | `RecipeExploder` | 份数缩放、递归子配方、循环拒绝 `CUL-REP-001`、缺失引用 `CUL-NF-001`、可选行默认排除 |
| 份数缩放 | `RecipeExploder.explodeForServings` | 倍数 = 目标份数 / 根版本份数；根缺失返回 `CUL-NF-001` |
| 版本固定（MB-D-013） | `RecipeVersionPinRule` | 草稿可指向当前版本；发布时解析为明确版本 ID；已发布版本不跟随；旧展开稳定 |
| 库存预览（MB-D-014） | `InventoryPreviewCalculator` | 需求/可用/缺口；FEFO；跨单位换算；不可换算批次计 0，不猜密度 |
| 确认扣减（MB-D-014） | `InventoryDeductionPlanner` + `ConfirmInventoryDeductionUseCase` | 确认才写流水；默认不建预留；负库存拒绝 `CUL-INV-003`；幂等键确定性生成 |
| 重复确认 | `InventoryRepository.appendIfAbsent` | 同幂等键返回 `false`，不重复写、不重复扣 |
| 可选缺料（MB-D-015） | `ShortfallCalculator` | 可选行默认不入缺口，单独返回；可显式并入 |
| 采购清单（MB-D-016） | `PurchaseListBuilder` + `CsvPurchaseListExporter` | 只取非可选且有缺口行；不落 `purchase_order*`；CSV 稳定列序 |
| 计算记录（MB-D-017） | `CalculationPayloadHasher`、`CalculationRecordFactory`、`ConfirmCalculationRecordUseCase` | 保存版本/份数/需求行/引擎版本/`payload_hash`；确认后不可变；重复确认 `CUL-INV-003` |

规则全部在 `core:domain`，不依赖 Compose/Android/Room；失败统一用 `Outcome` + `CulinaryError`。

## 6. Repository 契约与实现

详见 [MB-S2-04](MB-S2-04-REPOSITORY-CONTRACTS.md)。

| 端口 | 实现 | 说明 |
|---|---|---|
| `RecipeRepository` | `RecipeRepositoryImpl` | 读版本（含组件/单位）、保存草稿、保存已固定发布版本 |
| `InventoryRepository` | `InventoryRepositoryImpl` | 批次余额、幂等追加流水、账本读取 |
| `CalculationRecordRepository` | **无实现（契约 + fake）** | Schema v1 无表；需 Schema 变更授权 |
| `SauceRepository` | 契约 + fake | 持久化实现留待 Stage 3 酱汁档案库 |

DAO 仅新增查询/受约束更新方法（`RecipeDao.listActive`、`InventoryBatchDao.listActive/applyDeltas`、
`InventoryLedgerDao.findByIdempotencyKey/listAll`）；未改 Entity、表结构、Schema 版本或迁移。

契约测试：Room 内存库验证 `RecipeRepositoryImpl` 与 `InventoryRepositoryImpl`；fake 验证同一组语义
（草稿/当前版本唯一、幂等追加、负库存拒绝、计算记录 insert-once）。

## 7. 测试与构建结果

命令：

```powershell
.\gradlew.bat test assembleDebug --console=plain --no-daemon
```

| 模块 | 用例数 |
|---|---:|
| `core:common` | 9 |
| `core:domain` | 100 |
| `core:database` | 77 |
| `core:data` | 50 |
| `core:platform` | 19 |
| `app` | 6 |
| `feature:ingredients` | 3 |
| **合计** | **264** |

- 失败 **0**，错误 **0**；
- 相比 G2 基线 **197** 增加 **67**（domain +60、data +7），无测试删除或迁移；
- `assembleDebug` 成功，产物 `app/build/outputs/apk/debug/app-debug.apk`（36,856,488 字节）；
- 仪器化迁移测试仍为 Stage 1 记录的“需设备、未运行”，本阶段未改变该状态。

规则到用例的覆盖矩阵见 [MB-S2-03](MB-S2-03-RULES-TEST-MATRIX.md)。

## 8. Git 提交、标签与变更范围

### 8.1 提交

| 提交 | 内容 |
|---|---|
| `1f2afce37b036ddb2fbc234bb4f93c3a8ee798d2` | 领域模型与值对象 |
| `daa27b0c31f8003f39b3babc3ad04d338a65b3b8` | 单位展示精度与份数缩放 |
| `3b73729c21e3393d933e350b42b4aceb508d23ce` | 子配方版本固定 |
| `6901da1c34c97823715c7732ea5b3b21b0d27a47` | 库存预览/确认扣减 |
| `2a5f474ba1e20bb1cdd4b0a377d22cc1cd04f415` | 可选缺料与采购清单 |
| `9523d8e5f80f5c4972b70db2c98f46694bd94cc9` | 计算记录快照与 payload_hash |
| `73f80732ea352958adea8a63d5cc59d24a553084` | Repository 契约、fake、基础实现 |
| `04ca5fc2146642b0f47536ea3d957e4d719fcba5` | 代码格式（`ktlintFormat`，无行为变更） |

本报告提交为 `83d1051184fd04a02c59e4cbe87740ebb4eee4a3`；检查点标签指向该提交，
对象哈希见 §8.2。§8.2 由后续簿记提交回填，标签不随簿记提交移动。

### 8.2 检查点与关闭标签

```text
checkpoint/misebase-stage2-20261104
  tag object: c39f87488c00b0e3b570f49ba9252a3b85aadf51
  commit:     83d1051184fd04a02c59e4cbe87740ebb4eee4a3

checkpoint/misebase-stage2-closed-20261104
  tag object: 34cb267da29d3a7b30b90e647586f91699e7c5dd
  commit:     a39b3ca7cca3bede69b841d8650bbc13eb4a7334

checkpoint/misebase-stage3-start-20261104
  tag object: 42b06b0d4e36828f8578732cc5ae70238fdfdfa5
  commit:     a39b3ca7cca3bede69b841d8650bbc13eb4a7334
```

`stage2-20261104` 指向报告提交；两个关闭标签指向 `docs(stage2): close stage 2 after review`
提交，由关闭簿记提交回填。§9 的风险与 §10 的审查条件不因簿记提交改变。

### 8.3 变更范围（`stage2-start..HEAD`）

```text
M  README.md
M  docs/00-governance/DOCUMENT-INDEX.md
M  docs/05-handover/MB-S0-07-DECISION-REGISTER.md
M  docs/05-handover/S0-05-OWNER-SIGNOFF.md
M  docs/06-stage1/MB-S1-01-EXECUTION-REPORT.md
M  docs/06-stage1/MB-S1-10-STAGE1-CLOSE-RECORD.md
M  scripts/check-doc-consistency.ps1
A  docs/07-stage2/*（本阶段 5 份文档）
A/M core/domain/**（模型、规则、契约、测试）
A/M core/data/**（Mapper、实现、fake、契约测试）
M  core/database/dao/*.kt（仅新增查询/受约束更新）
```

明确未变更：`core/database/schemas/**`、`core/database/entity/**`、迁移、数据库文件、
`core/data/.../backup/**`、`AutoBackupWorker`、Compose/导航/主题、`settings.gradle.kts`、
`gradle/libs.versions.toml`、G2 历史报告正文、G3-1。

## 9. 未解决问题与风险

| # | 事项 | 级别 | 说明 |
|---:|---|---|---|
| 1 | 计算记录持久化 Schema 变更 | 中 | 已授权（[MB-SC-00](../08-schema-change/MB-SC-00-AUTHORIZATION.md)），待实施；现仅 DTO + 契约 + fake |
| 2 | `recipe_version.servings` 不存在 | 中 | 已授权（MB-SC-00）与计算记录合并为一次 Schema 变更，待实施；当前读取按 1 份/整批 |
| 3 | `SAUCE` 组成行无法映射为领域目标 | 低 | `RecipeMapper` 返回 `CUL-REP-004`；酱汁档案与嵌套在 Stage 3 落地 |
| 4 | Repository 写操作的原子性依赖 Use Case 事务 | 中 | `appendIfAbsent`/`savePublished` 单次调用不在内部开事务；必须在 `TransactionRunner.write` 内调用（E02 §4.3） |
| 5 | `getVersion` 映射失败时返回 `null` | 低 | 与既有端口签名一致，但会丢失错误码；Stage 3 可改为 `Outcome` |
| 6 | 无 Compose/导航/扫码实现 | 按范围 | Stage 2 明确不开发页面 |
| 7 | 仪器化迁移测试未运行 | 继承 | 需设备；Stage 1 已记录 |

## 10. 是否具备 Stage 2 审查条件

**具备审查条件**：前置检查全通过、规则与契约有可复现测试、264/0 通过、Debug APK 构建成功、
变更严格在允许范围、无 Schema/Entity/备份/UI 变更。

审查时建议重点确认：

1. 第 9 节第 1、2 项是否按“需要 Schema 变更授权”处理（当前未越界建表）；
2. Repository 事务边界约定是否被后续 Stage 3/4 用例遵守；
3. `SAUCE` 组成行映射边界的处理方式是否可接受到 Stage 3。

**Stage 2 已关闭（CLOSED）**：审查结论为“有条件通过；主体合规，无重大范围偏离”，
关闭提交与两个关闭标签见 §8.2 与 [MB-S2-06](MB-S2-06-STAGE2-CLOSE-RECORD.md)。
条件项（两项 Schema 变更请求）状态为“待负责人决策”，未实施。

## 11. 停止声明

> **未进入 Stage 3。** 本阶段未开发 Compose 页面、未接 UI、未新建模块、未改 Schema/Entity/迁移、
> 未改备份/恢复、未移动 `framework-0.1.0` 或任何既有 checkpoint 标签。

## 12. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0.0 | 2026-11-04 | Stage 2 执行报告，状态 Review | 接管工程组 |
| 1.1.0 | 2026-11-04 | 按 MB-S2-CLOSE-001 改为 CLOSED；补关闭提交与标签 | 接管工程组 |
| 1.1.1 | 2026-11-04 | MB-DECISION-SIGN-002：修正 §1 报告状态为 CLOSED；两项 Schema 风险改为“已授权（MB-SC-00），待实施” | 接管工程组 |
| 1.1.2 | 2026-11-04 | MB-D-007-SIGN-001：MB-D-007 签收为 ACCEPTED（Stage 4 强制前置）；当前计数同步为 13/18、5 项 PROPOSED | 接管工程组 |
