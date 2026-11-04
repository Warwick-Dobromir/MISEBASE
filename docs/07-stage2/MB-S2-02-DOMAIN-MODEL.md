# MB-S2-02 领域模型说明

**文档编号**：MB-S2-02
**版本**：`1.0.0`
**状态**：`Review`
**日期**：2026-11-04
**范围**：`core:domain` 的模型与值对象（Stage 2）

---

## 1. 设计原则

1. **纯 JVM**：`core:domain` 不依赖 Android/Room/Compose/Hilt；时间用 `java.time`，结果用 `Outcome`。
2. **先值对象后实体**：ID 只承诺非空不透明字符串，不解析 UUID；数量必须带单位。
3. **不变量在构造期失败**：非法状态用 `require` 拒绝，可预期业务失败用 `Outcome`。
4. **不越 Schema**：本阶段不新增表；无表的领域对象只做 DTO + 契约（见 §5）。
5. **命名与 A06 对齐**：字段名与数据字典一致，便于 Stage 3 映射。

## 2. 标识与值对象

| 类型 | 语义 | 不变量 |
|---|---|---|
| `IngredientId` / `UnitId` / `RecipeId` / `RecipeVersionId` | 既有标识 | 非空 |
| `SauceId` / `SauceVersionId` | 酱汁与版本 | 非空 |
| `ProductId` / `BatchId` / `LocationId` / `LedgerEntryId` | 商品、批次、位置、流水 | 非空 |
| `CalculationRecordId` | 计算记录 | 非空 |
| `Quantity`（既有） | 数量 + 单位 | 有限、非负 |
| `Servings` | 份数 | 有限、> 0；`scaleFactor(relativeTo)` |
| `DisplayQuantity` | 展示数量 | 有限、非负；`isApproximate` |
| `IngredientUnitConversion`（既有） | 食材级换算 | 单位不同、因子 > 0 |

## 3. 档案与版本

| 类型 | 关键字段 | 不变量 | 对应表（v1） |
|---|---|---|---|
| `Ingredient`（既有） | id、name、normalizedName、defaultUnitId、status | 名称非空 | `ingredient` |
| `Sauce` | id、name、normalizedName、status | 名称非空 | `sauce` |
| `SauceVersion` | id、sauceId、versionNo、status、yield、components | 产量 > 0；`lineNo` 唯一 | `sauce_version` / `sauce_component` |
| `Recipe`（既有） | id、name、normalizedName、category、status | 名称非空 | `recipe` |
| `RecipeVersion` | 既有字段 + `servings` | 产量 > 0；`servings > 0`；`lineNo` 唯一 | `recipe_version` / `recipe_component` |
| `RecipeComponent`（既有） | lineNo、target、quantity、isOptional | 行号 > 0；数量 > 0 | `recipe_component` |
| `ComponentTarget`（既有） | `Ingredient` / `Recipe(recipeId, versionId)` | 子菜谱必须带明确版本 | `component_type` |

> `RecipeVersion.servings` 在 Stage 2 为**领域属性**；Schema v1 无对应列，读取按 1 份处理（§5）。

## 4. 草稿与发布（MB-D-013）

| 类型 | 用途 |
|---|---|
| `ComponentTargetDraft` | 草稿目标：`Ingredient` / `RecipeCurrent(recipeId)` / `RecipePinned(recipeId, versionId)` |
| `RecipeComponentDraft` | 草稿组成行，数量必须 > 0 |
| `RecipeVersionDraft` | 草稿版本：版本号 > 0、产量 > 0、`lineNo` 唯一 |
| `CurrentVersionSource` | 端口：按 `recipeId` 取当前版本 ID |

发布时由 `RecipeVersionPinRule` 把 `RecipeCurrent` 解析为明确 `RecipeVersionId`，生成
`RecipeVersion(status = PUBLISHED)`；`RecipePinned` 原样保留。落库形态永远不含 `RecipeCurrent`。

## 5. 库存

| 类型 | 关键字段 | 不变量 | 对应表 |
|---|---|---|---|
| `InventoryItem` | batchId、productId、locationId、stockUnit、onHand、reserved、expiresOn | `onHand ≥ 0`、`0 ≤ reserved ≤ onHand`；`available = onHand − reserved` | `inventory_batch` |
| `InventoryLedgerEntry` | id、batchId、productId、locationId、type、stockUnit、两个 delta、两个 after、idempotencyKey、occurredAt | 至少一个 delta ≠ 0；after 非负；`reservedAfter ≤ onHandAfter`；幂等键非空 | `inventory_ledger` |
| `LedgerEntryType` | 11 种类型 | 与 `LedgerTypeDb` 取值一一对应 | `inventory_ledger.type` |

`InventoryItem` 不提供"直接改余额"的方法；余额只能由追加式流水改变（D-009）。

## 6. 需求、缺口与采购

| 类型 | 语义 | 不变量 |
|---|---|---|
| `RequirementLine` | 计算记录/清单的稳定需求行 | 数量 > 0；可选标记沿路径传播 |
| `ShortfallLine` | 需求 − 可用 | 三个数量同单位；缺口 ≥ 0 |
| `ProductRequirement` | 按商品的扣减需求 | 数量 > 0 |
| `PurchaseList` / `PurchaseListLine` | 按需生成的采购清单 | 采购量 > 0；不映射任何表 |

## 7. 计算记录（MB-D-017）

| 类型 | 关键字段 | 不变量 |
|---|---|---|
| `CalculationRecord` | id、recipeVersionId、servings、lines、engineVersion、payloadHash、status、createdAt、confirmedAt | hash 为 64 位小写十六进制；行按"食材 + 单位"唯一；`CONFIRMED` 必须带 `confirmedAt` |
| `CalculationRecordStatus` | `PREVIEW` / `CONFIRMED` | 只允许 `PREVIEW → CONFIRMED` |
| `CalculationEngine.VERSION` | 引擎版本常量 | 算法变更必须递增 |

`confirm(at)` 对已确认记录返回 `CUL-INV-003`，从领域层阻止二次确认；持久化契约要求 insert-once。

## 8. 与 Schema v1 的差距（需授权项）

| 领域对象 | 差距 | 本阶段处理 |
|---|---|---|
| `CalculationRecord` | 无对应表 | 仅 DTO + `CalculationRecordRepository` 契约 + fake；**需 Schema 变更授权** |
| `RecipeVersion.servings` | `recipe_version` 无 `servings` 列 | 读取按“整批 = 1 份”；写回不持久化；**需授权** |
| `Sauce` / `SauceVersion` 持久化 | 有表但无实现 | 契约 + fake 先行，Stage 3 落地 |
| 配方引用酱汁（`SAUCE` 组成行） | 领域目标未建模 | Mapper 返回 `CUL-REP-004`，不静默丢弃 |

## 9. 与既有类型的关系

- 既有 `ExplodedRequirement` / `RequirementGroup` / `RequirementGap` 保留：分别服务展开、汇总、旧缺口算法；
- 新增 `RequirementLine` / `ShortfallLine` 是**计算记录与采购清单的稳定契约**，避免把展开中间态写进历史；
- `ShortfallCalculator.fromGroups` 提供两者之间的显式转换。
