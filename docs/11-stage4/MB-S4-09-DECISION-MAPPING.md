# MB-S4-09 决策映射表

**文档编号**：MB-S4-09
**版本**：`1.0.0`
**状态**：`Review`
**日期**：2026-11-05
**范围**：MB-D-014 / 015 / 016 / 017 与相关已接受决策的落地证据

---

## 1. MB-D-014 库存扣减模式

| 决策 | 落地 |
|---|---|
| 先预览需求/可用/缺口 | `BuildRequirementPlanUseCase` → `ShortfallCalculator`；页面“计算”只读 |
| 用户确认后写流水 | `ConfirmRequirementDeductionUseCase` 在 `TransactionRunner.write` 内追加 `PRODUCTION_ISSUE` 流水 |
| 默认不创建预留 | `reservedDelta = 0`；`InventoryDeductionPlanner` 默认无预留；测试断言 `reservedAfter = 0` |
| 不足拒绝 | 出库与确认扣减均返回 `CUL-INV-003`，不产生半条流水 |

证据：`StockMovementUseCasesTest`、`InventoryDeductionTest`、`RequirementPlanUseCasesTest`、`InventoryRepositoryImpl`。

---

## 2. MB-D-015 可选配料缺料口径

| 决策 | 落地 |
|---|---|
| 支持 `is_optional` | `RequirementLine.isOptional`、`RecipeComponent.isOptional`、计算记录行 `is_optional` |
| 默认不计入缺口 | `ShortfallCalculator.required` 排除可选；`PurchaseListBuilder` 只取 `!isOptional` |
| 详情单独显示 | `RequirementPlan.optionalLines`；页面“可选缺口（不计入采购）” |
| 路径传播 | `RecipeExploder` 将祖先可选标记传给叶节点 |

证据：`RecipeExploderSauceTest`、`ShortfallCalculatorTest`、`RequirementPlanUseCasesTest`、`InventoryUiTest`。

---

## 3. MB-D-016 采购清单形态

| 决策 | 落地 |
|---|---|
| 不落 `purchase_order*` | 无任何采购单 Entity/DAO 业务引用；休眠表扫描 PASS |
| 按需求 − 可用计算 | `PurchaseListBuilder` |
| 展示 | 需求计划页采购清单区 |
| 导出 | `CsvPurchaseListExporter` + 系统文档选择器 |
| 属于 Inventory | 路由与页面均在 `feature:inventory` |

证据：`PurchaseListTest`、`RequirementPlanUseCasesTest`、`InventoryUiTest`、休眠表扫描。

---

## 4. MB-D-017 计算记录快照

| 决策 | 落地 |
|---|---|
| 保存 recipe_version_id | `CalculationRecord.recipeVersionId` |
| 输入份数 | `CalculationRecord.servings` |
| 需求行 | `calculation_record_line`（可选标记） |
| 引擎版本 | `CalculationEngine.VERSION = stage4-1` |
| payload_hash | `CalculationPayloadHasher`；唯一索引 |
| 确认后不可变 | 领域 `confirm`、Repository insert-once、数据库触发器 |
| 回放 | `ListCalculationRecordsUseCase` / `GetCalculationRecordUseCase` + 页面回放对话框 |

证据：`CalculationRecordTest`、`CalculationRecordRepositoryTest`、`RequirementPlanUseCasesTest`。

---

## 5. 其他已接受决策

| 决策 | 本阶段关系 |
|---|---|
| MB-D-008 UI 方向 A | 页面沿用既有 token/组件，不改视觉方向 |
| MB-D-009 N1 | 五入口与顺序不变；库存入口切换真实页面 |
| MB-D-013 子配方版本固定 | 展开只读明确版本，不跟随当前版本 |
| MB-D-007 备份一致性 | 未改备份/恢复实现 |

---

## 6. PROPOSED 边界

| 决策 | 本阶段处置 |
|---|---|
| MB-D-004 扫码 UI 归属 | 不新建 `feature:scan`、不实现扫码页面；底部入口仍占位 |
| MB-D-006 验收/测试口径 | 不改写历史报告；按实际唯一用例 416 报告 |
| MB-D-010 远端 CI | 未启用 |
| MB-D-011 真机验证 | 未做真机验收 |
| MB-D-012 发布签名 | 未创建/变更签名 |

---

## 7. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0.0 | 2026-11-05 | Stage 4 决策映射 | 接管工程组 |
