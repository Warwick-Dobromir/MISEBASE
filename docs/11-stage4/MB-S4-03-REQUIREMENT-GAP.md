# MB-S4-03 需求汇总与缺口预览说明

**文档编号**：MB-S4-03
**版本**：`1.0.0`
**状态**：`Review`
**日期**：2026-11-05
**范围**：目标份数展开、需求汇总、可用库存、缺口与可选配料口径

---

## 1. 流程

```text
菜谱当前版本 + 目标份数
  → RecipeExploder（配方 + 酱汁图，按 servings 缩放）
  → RequirementAggregator（按食材汇总可合并单位）
  → 每食材可用库存（批次 available，换算到需求单位）
  → ShortfallCalculator（必需 / 可选分开）
  → PurchaseListBuilder（只取必需缺口）
  → CalculationRecordFactory（确认前快照）
```

全部步骤只读；不写流水，不创建预留，不写计算记录。

---

## 2. 份数缩放

- 根版本基准份数为 `recipe_version.servings`；
- 倍数 = 目标份数 ÷ 基准份数；
- 子配方/子酱汁按“父级引用量 ÷ 子版本产量”递归；
- 目标份数必须为有限正数，否则 `CUL-VAL-001`。

---

## 3. 汇总规则

- 同食材、同单位：直接相加；
- 同食材、同维度不同单位（MASS/VOLUME）：换算到先出现的单位后相加；
- COUNT 或跨维度：不强行合并，保留多行；
- 可选标记：组内全部为可选时，该组为可选。

---

## 4. 可用库存

- 库存按批次、商品记账；商品映射到食材；
- 每个批次取 `available = on_hand − reserved`；
- 用 `UnitConverter` 换算到需求单位；
- 同维度按 `baseFactor`；跨维度只使用食材级显式换算；
- 无换算依据时该批次计 0 可用，缺口为整行需求，不猜密度；
- FEFO 排序与批次分配仍由 `InventoryPreviewCalculator` / `InventoryDeductionPlanner` 负责。

---

## 5. 缺口

- 必需行：`max(0, 需求 − 可用)`；
- 可选行：单独展示，默认**不进入**必需缺口；
- `ShortfallReport.required` 不含可选；
- `ShortfallReport.optional` 保留可选行缺口，供详情查看；
- 页面明确标注“可选缺口（不计入采购）”。

---

## 6. 结果模型

`RequirementPlan` 包含：

| 字段 | 说明 |
|---|---|
| `recipeVersionId` / `recipeName` | 计算所用版本与名称 |
| `baseServings` / `targetServings` | 基准与目标份数 |
| `requiredLines` | 必需行（需求/可用/缺口/商品） |
| `optionalLines` | 可选行（单独展示） |
| `purchaseList` | 按需采购清单 |
| `record` | 确认前计算记录（含 payload hash） |
| `generatedAt` | 生成本次计划的时间 |

`hasRequiredShortfall` 用于确认扣减按钮与提示。

---

## 7. 计算记录联动

- 计算计划只生成 `PREVIEW` 记录；
- 用户“确认扣减”或“生成采购清单”时才 `confirm` 并写入；
- 生成清单不扣库存；确认扣减要求必需行无缺口；
- 可选行进入记录（带 `is_optional`），但不参与默认扣减。

见 [MB-S4-05](MB-S4-05-CALCULATION-RECORD.md)。

---

## 8. 边界与已知限制

- 同一食材存在不可合并单位时，各需求行分别读取库存，可能高估总可用；本阶段按“不强行合并”原则保留，后续可做全局分配；
- 商品映射取该食材第一个启用商品；
- 跨维度换算依赖食材级记录；没有记录时明确显示整行缺口。

---

## 9. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0.0 | 2026-11-05 | 需求汇总、缺口与可选口径说明 | 接管工程组 |
