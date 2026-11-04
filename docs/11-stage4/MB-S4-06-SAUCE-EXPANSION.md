# MB-S4-06 酱汁图展开说明

**文档编号**：MB-S4-06
**版本**：`1.0.0`
**状态**：`Review`
**日期**：2026-11-05
**范围**：`RecipeExploder` / `ExpandRecipeUseCase` 对 `ComponentTarget.Sauce` 的递归展开

---

## 1. 背景

Stage 3 已将 `SAUCE` 组成行映射为 `ComponentTarget.Sauce`，但展开遇到酱汁目标仍返回 `CUL-REP-004`，导致含酱汁菜谱无法计算需求。本阶段消除该限制。

---

## 2. 图模型

- 配方版本节点：`GraphNode.Recipe(RecipeVersionId)`；
- 酱汁版本节点：`GraphNode.Sauce(SauceVersionId)`；
- 两种节点独立命名空间，避免同 ID 误判；
- `RecipeGraphSource.findVersion` 读配方；默认 `findSauceVersion` 读酱汁；
- `ExpandRecipeUseCase` 预取配方 + 酱汁组合图，供展开器同步访问。

---

## 3. 递归规则

对每个组成行：

| 目标 | 处理 |
|---|---|
| 食材 | 记录 `ExplodedRequirement(ingredientId, scaled, optional)` |
| 子菜谱 | 引用量换算到子版本产量单位，factor = 引用量 ÷ 子产量，递归 |
| 子酱汁 | 引用量换算到酱汁产量单位，factor = 引用量 ÷ 酱汁产量，递归 |

酱汁内部同样允许食材、子菜谱、子酱汁三类目标。

---

## 4. 可选标记

- 组件的 `is_optional` 沿引用路径传播；
- 任一祖先为可选，叶节点需求为可选；
- 默认 `includeOptional=false` 时跳过可选行；
- `includeOptional=true` 时保留并标记，供详情展示。

---

## 5. 错误

| 场景 | 错误 |
|---|---|
| 配方版本缺失 | `CUL-NF-001` |
| 酱汁版本缺失 | `CUL-NF-001` |
| 配方/酱汁成环 | `CUL-REP-001`（路径含 `sauce:` 前缀区分） |
| 单位不可换算 | `CUL-UNIT-001` |
| multiplier 非有限正数 | `CUL-VAL-001` |

不再返回 `CUL-REP-004`。

---

## 6. 性能

- 预取图后展开不再访问数据库；
- 已有 200 节点配方基准仍通过；
- 酱汁图与配方图共享同一访问路径，无额外递归框架。

---

## 7. 测试

`RecipeExploderSauceTest` 7 例：

1. 酱汁递归缩放；
2. 嵌套酱汁；
3. 酱汁内引用子菜谱；
4. 酱汁环；
5. 缺失酱汁版本；
6. 可选酱汁默认排除/显式包含且标记传播；
7. 酱汁组件单位不可换算。

另在 `RequirementPlanUseCasesTest` 覆盖“菜谱→酱汁→食材”进入汇总与缺口。

---

## 8. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0.0 | 2026-11-05 | 酱汁图递归展开说明 | 接管工程组 |
