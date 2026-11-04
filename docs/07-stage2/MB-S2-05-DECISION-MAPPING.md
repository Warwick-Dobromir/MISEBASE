# MB-S2-05 决策映射表（MB-D-013/014/015/016/017）

**文档编号**：MB-S2-05
**版本**：`1.0.0`
**状态**：`Review`
**日期**：2026-11-04
**签收依据**：2026-11-04 负责人电子确认（[S0-05 §7](../05-handover/S0-05-OWNER-SIGNOFF.md)）

---

## 1. 映射总表

| 决策 | 已接受口径 | 实现 | 测试 | 状态 |
|---|---|---|---|---|
| MB-D-013 | 草稿可指向当前版本；发布时解析并写入明确版本 ID；已发布版本不跟随 | `RecipeVersionDraft`、`ComponentTargetDraft`、`CurrentVersionSource`、`RecipeVersionPinRule` | `RecipeVersionPinRuleTest`（5） | 已实现，无 Schema 变更 |
| MB-D-014 | 先预览需求/可用/缺口；确认后写流水；默认不建预留；负库存拒绝 | `InventoryPreviewCalculator`、`InventoryDeductionPlanner`、`ConfirmInventoryDeductionUseCase`、`InventoryRepository` | `InventoryDeductionTest`（8）、`RepositoryContractTest`（库存 2 例） | 已实现领域规则与契约；UI 不在本阶段 |
| MB-D-015 | 支持 `is_optional`；默认不计入缺口，详情单独显示 | `ShortfallCalculator`、`ShortfallReport`、`ShortfallLine` | `ShortfallCalculatorTest`（6） | 已实现 |
| MB-D-016 | 不落 `purchase_order*` 表；按需计算/展示/导出；采购清单属 Inventory | `PurchaseList`、`PurchaseListBuilder`、`CsvPurchaseListExporter`（位于 `rule/inventory`） | `PurchaseListTest`（6） | 已实现，无表引用 |
| MB-D-017 | 计算记录保存 recipe_version_id、输入份数、需求行、引擎版本、payload_hash；确认后不可变 | `CalculationRecord`、`CalculationPayloadHasher`、`CalculationRecordFactory`、`CalculationRecordRepository`、`ConfirmCalculationRecordUseCase` | `CalculationRecordTest`（7） | DTO + 契约 + fake；**持久化需 Schema 变更授权** |

## 2. 逐项说明

### MB-D-013 子配方版本固定

- **草稿**：`ComponentTargetDraft.RecipeCurrent(recipeId)`，不落库为该形态；
- **发布**：`RecipeVersionPinRule.publish` 解析当前版本 → `ComponentTarget.Recipe(recipeId, versionId)`；
- **已发布不跟随**：发布结果只含固定 ID；测试用“子版本 v1→v2 换当前指针”验证旧展开仍为 50 g；
- **缺失处理**：当前版本缺失或引用版本不存在均返回 `CUL-NF-001`，不产生半成品。

### MB-D-014 库存扣减模式

- **预览**：`InventoryPreviewCalculator` 输出每行需求/可用/缺口与 FEFO 分配；只读；
- **确认**：`ConfirmInventoryDeductionUseCase` 在事务内重读库存 → 规划 → 追加流水；
- **默认无预留**：`reservedDelta = 0`，`reservedAfter = 原预留`；测试显式断言；
- **负库存拒绝**：规划阶段超过批次在手量返回 `CUL-INV-003`，不写半条流水；
- **幂等**：键由引用 + 批次 + 行序确定性生成；重复确认返回 `skippedIdempotentKeys`，余额不变；
- **流水只追加**：`InventoryRepository` 不提供更新/删除；DB 触发器仍是最后防线。

> 备注：MB-D-014 原文“除非负责人签收的方案另有规定”；本次签收为“接受建议方案”，故按默认
> **不创建预留**实现。D-010（预留时机）仍为 `OPEN`，不在本阶段裁决。

### MB-D-015 可选配料缺料口径

- 需求行携带 `isOptional`，沿展开路径传播（既有 `RecipeExploder` 已支持）；
- `ShortfallCalculator` 默认把可选行放入 `ShortfallReport.optional`，不计入 `required`；
- 详情视图可读取 `optional` 的缺口值；`includeOptionalInGap = true` 仅用于显式“含可选”口径；
- 采购清单只取非可选缺口行。

### MB-D-016 采购清单形态

- 领域模型 `PurchaseList`/`PurchaseListLine` 不映射任何表；
- `PurchaseListBuilder` 输入缺口行，输出稳定排序的清单；
- 导出契约 `PurchaseListExporter` + `CsvPurchaseListExporter`（UTF-8、固定列）；
- 测试扫描 `core/domain` 源码（排除注释）确认无 `purchase_order` 引用；
- 采购清单归属 `rule/inventory`，未新建模块、未新增表。

### MB-D-017 计算记录快照

- `payload_hash` = SHA-256(规范串)；规范串**不含**时间与记录 ID，同输入跨次运行稳定；
- 记录保存 `recipeVersionId`、`servings`、需求行、`engineVersion`、`payloadHash`；
- `PREVIEW → CONFIRMED` 单向；重复确认 `CUL-INV-003`；
- 仓库契约：只接受 `CONFIRMED`、同 ID 不覆盖、同 payload 去重；
- **Schema v1 无计算记录表**：按任务书只实现 DTO + 契约 + fake，**不建表、不迁移**；
  持久化需负责人签发 Schema 变更授权。

## 3. 与仍 PROPOSED 决策的边界

| 决策 | 状态 | 本阶段处理 |
|---|---|---|
| MB-D-004 扫码 UI 归属 | `PROPOSED` | 未新建 `feature:scan`，未实现扫码 UI |
| MB-D-006 测试口径 | `PROPOSED` | 未改写历史报告；本报告按 264 例口径陈述 |
| MB-D-007 备份一致性 | `PROPOSED` | 未改备份/恢复、写屏障、checkpoint |
| MB-D-008/009 UI/导航 | `PROPOSED` | 未做视觉定稿、未重排导航、未开发页面 |
| MB-D-010/011/012 | `PROPOSED` | 未启用 CI、未做真机验收、未创建签名 |

## 4. 需要授权的 Schema 变更（本阶段未实施）

| # | 变更 | 原因 | 影响 |
|---:|---|---|---|
| 1 | 新增计算记录表（或等价持久化结构） | MB-D-017 要求确认后不可变并可回放 | 需新表/迁移与备份格式核对 |
| 2 | `recipe_version.servings` | 份数缩放的基准份数无法持久化 | 需新增列/迁移；当前按 1 份/整批读取 |

两项均未建表、未改 Schema、未迁移；待负责人明确授权后另立任务实施。
