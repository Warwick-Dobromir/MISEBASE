# CULINARY 数据字典

**文档编号**：A06
**版本**：`0.1.0`
**状态**：`Review`
**日期**：2026-11-04
**负责人**：首席架构师 / 技术负责人
**配套文档**：[A05 ER 图](A05-DATA-MODEL-ER.md)、[A07 版本与迁移](A07-VERSIONING-MIGRATION.md)

---

## 1. 通用约定

### 1.1 命名

| 对象 | 规则 | 示例 |
|---|---|---|
| 表 | 单数 `snake_case` | `recipe_version` |
| 列 | `snake_case` | `normalized_name` |
| 主键 | `id` | `id` |
| 外键 | `<被引用实体>_id` | `recipe_id` |
| 布尔 | `is_` / `allow_` 前缀 | `is_optional` |
| 时间点 | `_at`，UTC epoch millis | `created_at` |
| 业务日期 | `_date`，`YYYY-MM-DD` | `planned_date` |
| 金额 | `_minor`，最小货币单位整数 | `unit_price_minor` |
| 索引 | `idx_<table>_<cols>` | `idx_recipe_normalized_name` |
| 唯一索引 | `ux_<table>_<cols>` | `ux_unit_code` |
| 外键约束 | `fk_<table>_<ref>` | `fk_product_ingredient` |

### 1.2 类型

| 逻辑类型 | SQLite 类型 | 说明 |
|---|---|---|
| ID | `TEXT` | 小写 UUID v4，36 字符 |
| 枚举 | `TEXT` | 用 `CHECK` 限定；Room 用 TypeConverter |
| 布尔 | `INTEGER` | 0/1，`CHECK (x IN (0,1))` |
| 时间点 | `INTEGER` | UTC epoch millis |
| 业务日期 | `TEXT` | `YYYY-MM-DD`，本地日期 |
| 数量 | `REAL` | 双精度；内部保留 6 位；显示按单位精度 |
| 金额 | `INTEGER` | 最小货币单位；CNY 为分 |
| JSON | `TEXT` | UTF-8；需版本字段 |
| 哈希 | `TEXT` | 小写 SHA-256 hex |

### 1.3 通用审计列

除纯关联/流水表外，可变业务表包含：

| 列 | 类型 | 空 | 默认 | 说明 |
|---|---|---|---|---|
| `created_at` | INTEGER | N | — | 创建时间，UTC ms |
| `updated_at` | INTEGER | N | — | 最后修改时间，UTC ms |
| `deleted_at` | INTEGER | Y | NULL | 软删除时间；NULL 表示未删除 |
| `revision` | INTEGER | N | 1 | 乐观并发版本；每次更新 +1 |

不可变表（版本、流水、快照、变更日志）只有 `created_at`（必要时加 `published_at`/`posted_at`），没有 `updated_at`。

### 1.4 数值与舍入

- 数据库存 `REAL`，不存格式化字符串。
- 中间计算不四舍五入；显示时按 `unit.display_precision` 格式化。
- 比较使用容差 `1e-9`；库存是否为零使用 `<= 1e-9`。
- 金额不使用 `REAL`；用整数最小单位，避免 `0.1 + 0.2` 问题。
- 汇率/多币种不在本阶段范围；`currency_code` 仅记录。

### 1.5 规范化

- `name` 保留原始显示；`normalized_name` 用于唯一性与搜索。
- 规范化：Unicode NFKC → 去首尾空白 → 连续空白折叠为一个 → `Locale.ROOT` 小写。
- 不擅自去除重音/变音符号，避免把不同食材合并；搜索可在应用层做二次匹配。
- 中文不做分词；v1 用 `LIKE`（O-25）。

### 1.6 外键开关

- 每个连接打开后执行 `PRAGMA foreign_keys = ON`。
- Room 通过 `RoomDatabase.Callback.onOpen` 或 `SupportSQLiteOpenHelper` 配置确保开启。
- 外键关闭视为严重配置错误，启动自检应记录并阻止写入。

## 2. 枚举字典

| 枚举 | 值 |
|---|---|
| `UnitDimension` | `MASS`, `VOLUME`, `COUNT` |
| `EntityStatus` | `ACTIVE`, `ARCHIVED` |
| `VersionStatus` | `DRAFT`, `PUBLISHED`, `SUPERSEDED` |
| `ComponentType` | `INGREDIENT`, `SAUCE`, `RECIPE` |
| `RecipeCategory` | `STOCK`, `PREP`, `COMPONENT`, `DISH`, `OTHER` |
| `Difficulty` | `EASY`, `MEDIUM`, `HARD` |
| `BatchStatus` | `ACTIVE`, `DEPLETED`, `EXPIRED`, `DISCARDED` |
| `LedgerType` | `PURCHASE_RECEIPT`, `PRODUCTION_ISSUE`, `RESERVE`, `RELEASE`, `ADJUSTMENT_IN`, `ADJUSTMENT_OUT`, `WASTE`, `TRANSFER_OUT`, `TRANSFER_IN`, `RETURN`, `VOID_RECEIPT` |
| `ReservationStatus` | `ACTIVE`, `RELEASED`, `CONSUMED` |
| `PlanStatus` | `DRAFT`, `CONFIRMED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED` |
| `PlanItemStatus` | `PENDING`, `DONE`, `CANCELLED` |
| `PurchaseOrderStatus` | `DRAFT`, `ORDERED`, `PARTIALLY_RECEIVED`, `RECEIVED`, `CANCELLED` |
| `PurchaseOrderItemStatus` | `OPEN`, `PARTIAL`, `RECEIVED`, `CANCELLED` |
| `ReceiptStatus` | `DRAFT`, `POSTED`, `VOID` |
| `ProcurementStatus` | `OPEN`, `ORDERED`, `PARTIAL`, `RECEIVED`, `CANCELLED` |
| `ImageOwnerType` | `INGREDIENT`, `SAUCE`, `SAUCE_VERSION`, `RECIPE`, `RECIPE_VERSION`, `PRODUCT`, `INVENTORY_BATCH`, `PLAN`, `RECEIPT` |
| `ImageRole` | `PRIMARY`, `PROCESS`, `REFERENCE` |
| `ConversionStatus` | `OK`, `UNCONVERTIBLE`, `MISSING_PRODUCT`, `UNIT_MISMATCH` |
| `BackupKind` | `AUTO`, `MANUAL`, `PRE_MIGRATION`, `PRE_RESTORE` |
| `BackupStatus` | `SUCCESS`, `FAILED`, `VERIFIED` |
| `ChangeAction` | `CREATE`, `UPDATE`, `DELETE`, `PUBLISH`, `CONFIRM`, `RECEIVE`, `ADJUST`, `IMPORT`, `RESTORE` |

## 3. 单位与食材

### 3.1 `unit`

计量单位。数量和单位必须成对出现。

| 列 | 类型 | 空 | 默认 | 约束/说明 |
|---|---|---|---|---|
| `id` | TEXT | N | — | PK |
| `code` | TEXT | N | — | 唯一；`g`, `kg`, `ml`, `tsp`, `piece` |
| `name` | TEXT | N | — | 显示名 |
| `symbol` | TEXT | N | — | 符号 |
| `dimension` | TEXT | N | — | `MASS` / `VOLUME` / `COUNT` |
| `base_factor` | REAL | N | — | 到同维度基准单位的因子；COUNT 固定 1 |
| `is_base` | INTEGER | N | 0 | 是否维度基准 |
| `is_approximate` | INTEGER | N | 0 | 如 cup/tbsp 的近似换算 |
| `display_precision` | INTEGER | N | 2 | 0~6 |
| `is_active` | INTEGER | N | 1 | 停用单位不参与新录入 |
| `sort_order` | INTEGER | N | 0 | 显示排序 |
| `created_at` | INTEGER | N | — | |

**约束/索引：**

- `UNIQUE(code)`；`INDEX(dimension, is_active)`。
- `CHECK(dimension IN ('MASS','VOLUME','COUNT'))`
- `CHECK(base_factor > 0)`；`CHECK(display_precision BETWEEN 0 AND 6)`。
- COUNT 维度不通过 `base_factor` 互转；不同 COUNT 单位必须走食材级换算。

**种子数据：**

| code | dimension | base_factor | approximate |
|---|---|---:|---|
| `mg` | MASS | 0.001 | 否 |
| `g` | MASS | 1 | 否 |
| `kg` | MASS | 1000 | 否 |
| `ml` | VOLUME | 1 | 否 |
| `l` | VOLUME | 1000 | 否 |
| `tsp` | VOLUME | 5 | 是 |
| `tbsp` | VOLUME | 15 | 是 |
| `cup` | VOLUME | 240 | 是 |
| `fl_oz` | VOLUME | 29.5735 | 是 |
| `piece` | COUNT | 1 | 否 |
| `bunch` | COUNT | 1 | 否 |
| `clove` | COUNT | 1 | 否 |
| `slice` | COUNT | 1 | 否 |
| `pack` | COUNT | 1 | 否 |
| `portion` | COUNT | 1 | 否 |

### 3.2 `ingredient`

抽象食材档案。

| 列 | 类型 | 空 | 默认 | 约束/说明 |
|---|---|---|---|---|
| `id` | TEXT | N | — | PK |
| `name` | TEXT | N | — | 显示名，如“黄油” |
| `normalized_name` | TEXT | N | — | 规范化名称 |
| `scientific_name` | TEXT | Y | NULL | 学名/英文名可选 |
| `category` | TEXT | Y | NULL | 分类，应用层受控词表 |
| `description` | TEXT | Y | NULL | 专业描述 |
| `flavor_profile` | TEXT | Y | NULL | 风味 |
| `seasonality` | TEXT | Y | NULL | 时令 |
| `origin` | TEXT | Y | NULL | 产地/来源 |
| `selection_notes` | TEXT | Y | NULL | 挑选要点 |
| `storage_notes` | TEXT | Y | NULL | 储存要点 |
| `prep_notes` | TEXT | Y | NULL | 处理要点 |
| `allergen_notes` | TEXT | Y | NULL | 过敏原提示（仅记录，不做医学判断） |
| `default_unit_id` | TEXT | Y | NULL | FK `unit.id`，默认计量/显示单位 |
| `status` | TEXT | N | `ACTIVE` | `ACTIVE` / `ARCHIVED` |
| `created_at` | INTEGER | N | — | |
| `updated_at` | INTEGER | N | — | |
| `deleted_at` | INTEGER | Y | NULL | 软删除 |
| `revision` | INTEGER | N | 1 | |

**约束/索引：**

- `FK(default_unit_id) REFERENCES unit(id) ON DELETE RESTRICT`。
- `UNIQUE(normalized_name) WHERE deleted_at IS NULL`（部分唯一索引）。
- `INDEX(status)`、`INDEX(category)`、`INDEX(default_unit_id)`。
- `CHECK(status IN ('ACTIVE','ARCHIVED'))`。

### 3.3 `ingredient_alias`

| 列 | 类型 | 空 | 默认 | 约束/说明 |
|---|---|---|---|---|
| `id` | TEXT | N | — | PK |
| `ingredient_id` | TEXT | N | — | FK `ingredient.id` ON DELETE CASCADE |
| `alias` | TEXT | N | — | 原始别名 |
| `normalized_alias` | TEXT | N | — | 规范化别名 |
| `created_at` | INTEGER | N | — | |

- `UNIQUE(normalized_alias)`：别名全局唯一，避免扫码/搜索歧义。
- `INDEX(ingredient_id)`。

### 3.4 `ingredient_unit_conversion`

食材级换算。只用于“同一食材”的跨维度或特殊单位换算。

| 列 | 类型 | 空 | 默认 | 约束/说明 |
|---|---|---|---|---|
| `id` | TEXT | N | — | PK |
| `ingredient_id` | TEXT | N | — | FK `ingredient.id` ON DELETE CASCADE |
| `from_unit_id` | TEXT | N | — | FK `unit.id` |
| `to_unit_id` | TEXT | N | — | FK `unit.id` |
| `factor` | REAL | N | — | 1 from = factor × to |
| `note` | TEXT | Y | NULL | 依据，如“品牌密度/实测” |
| `source` | TEXT | Y | NULL | 来源 |
| `created_at` | INTEGER | N | — | |

- `UNIQUE(ingredient_id, from_unit_id, to_unit_id)`。
- `CHECK(factor > 0)`；`CHECK(from_unit_id <> to_unit_id)`。
- 反向换算由应用用 `1/factor` 计算，不要求存两行；界面必须显示 `note` 与“近似”标记。
- 没有记录时，跨维度换算必须失败，不能使用通用密度。

## 4. 酱汁与菜谱

### 4.1 `sauce`

| 列 | 类型 | 空 | 默认 | 约束/说明 |
|---|---|---|---|---|
| `id` | TEXT | N | — | PK |
| `name` | TEXT | N | — | 显示名 |
| `normalized_name` | TEXT | N | — | |
| `family` | TEXT | Y | NULL | 酱汁家族/类型 |
| `texture` | TEXT | Y | NULL | 质地 |
| `flavor_profile` | TEXT | Y | NULL | 风味 |
| `usage_notes` | TEXT | Y | NULL | 使用建议 |
| `storage_notes` | TEXT | Y | NULL | 储存 |
| `shelf_life_days` | INTEGER | Y | NULL | 参考保质期，不替代实际判断 |
| `status` | TEXT | N | `ACTIVE` | `ACTIVE` / `ARCHIVED` |
| `created_at` | INTEGER | N | — | |
| `updated_at` | INTEGER | N | — | |
| `deleted_at` | INTEGER | Y | NULL | |
| `revision` | INTEGER | N | 1 | |

- `UNIQUE(normalized_name) WHERE deleted_at IS NULL`。
- `INDEX(status)`、`INDEX(family)`。
- 酱汁的组成在 `sauce_version` / `sauce_component`，不在本表。

### 4.2 `sauce_version`

| 列 | 类型 | 空 | 默认 | 约束/说明 |
|---|---|---|---|---|
| `id` | TEXT | N | — | PK |
| `sauce_id` | TEXT | N | — | FK `sauce.id` ON DELETE CASCADE |
| `version_no` | INTEGER | N | — | 从 1 递增 |
| `status` | TEXT | N | `DRAFT` | `DRAFT` / `PUBLISHED` / `SUPERSEDED` |
| `yield_qty` | REAL | N | — | > 0 |
| `yield_unit_id` | TEXT | N | — | FK `unit.id` |
| `instructions_md` | TEXT | Y | NULL | Markdown 文本 |
| `change_note` | TEXT | Y | NULL | 本版变更说明 |
| `based_on_version_id` | TEXT | Y | NULL | FK 自身；复制来源 |
| `is_current` | INTEGER | N | 0 | 当前版本标记 |
| `created_at` | INTEGER | N | — | |
| `published_at` | INTEGER | Y | NULL | 发布时间 |

- `UNIQUE(sauce_id, version_no)`。
- `UNIQUE(sauce_id) WHERE is_current = 1`（部分唯一索引）。
- `CHECK(yield_qty > 0)`；`CHECK(status IN (...))`；`CHECK(is_current IN (0,1))`。
- 已发布版本不可更新（应用 + 触发器双重保护）。
- `INDEX(sauce_id, status)`、`INDEX(yield_unit_id)`、`INDEX(based_on_version_id)`。

### 4.3 `sauce_component`

| 列 | 类型 | 空 | 默认 | 约束/说明 |
|---|---|---|---|---|
| `id` | TEXT | N | — | PK |
| `sauce_version_id` | TEXT | N | — | 所属版本 FK `sauce_version.id` ON DELETE CASCADE |
| `line_no` | INTEGER | N | — | 版本内行号 |
| `component_type` | TEXT | N | — | `INGREDIENT` / `SAUCE` / `RECIPE` |
| `ingredient_id` | TEXT | Y | NULL | 当类型为 INGREDIENT |
| `referenced_sauce_id` | TEXT | Y | NULL | 当类型为 SAUCE |
| `referenced_sauce_version_id` | TEXT | Y | NULL | 发布后必须固定 |
| `referenced_recipe_id` | TEXT | Y | NULL | 当类型为 RECIPE |
| `referenced_recipe_version_id` | TEXT | Y | NULL | 发布后必须固定 |
| `quantity` | REAL | N | — | > 0 |
| `unit_id` | TEXT | N | — | FK `unit.id` |
| `preparation_note` | TEXT | Y | NULL | 处理说明 |
| `is_optional` | INTEGER | N | 0 | 可选配料 |
| `group_label` | TEXT | Y | NULL | 分组，如“腌料” |
| `sort_order` | INTEGER | N | 0 | |
| `created_at` | INTEGER | N | — | |

**三选一 CHECK：**

```sql
CHECK (
  (component_type = 'INGREDIENT'
    AND ingredient_id IS NOT NULL
    AND referenced_sauce_id IS NULL AND referenced_sauce_version_id IS NULL
    AND referenced_recipe_id IS NULL AND referenced_recipe_version_id IS NULL)
  OR
  (component_type = 'SAUCE'
    AND ingredient_id IS NULL
    AND referenced_sauce_id IS NOT NULL
    AND referenced_recipe_id IS NULL AND referenced_recipe_version_id IS NULL)
  OR
  (component_type = 'RECIPE'
    AND ingredient_id IS NULL
    AND referenced_sauce_id IS NULL AND referenced_sauce_version_id IS NULL
    AND referenced_recipe_id IS NOT NULL)
)
```

- `UNIQUE(sauce_version_id, line_no)`。
- `INDEX(sauce_version_id)`、`INDEX(ingredient_id)`、`INDEX(referenced_sauce_id)`、`INDEX(referenced_sauce_version_id)`、`INDEX(referenced_recipe_id)`、`INDEX(referenced_recipe_version_id)`。
- `CHECK(quantity > 0)`；`CHECK(is_optional IN (0,1))`。
- 已发布版本的所有子引用必须有具体版本（应用校验；未发布草稿可为空）。

### 4.4 `recipe`

| 列 | 类型 | 空 | 默认 | 约束/说明 |
|---|---|---|---|---|
| `id` | TEXT | N | — | PK |
| `name` | TEXT | N | — | |
| `normalized_name` | TEXT | N | — | |
| `category` | TEXT | N | `OTHER` | `STOCK` / `PREP` / `COMPONENT` / `DISH` / `OTHER` |
| `cuisine` | TEXT | Y | NULL | 菜系 |
| `description` | TEXT | Y | NULL | |
| `prep_minutes` | INTEGER | Y | NULL | ≥ 0 |
| `cook_minutes` | INTEGER | Y | NULL | ≥ 0 |
| `difficulty` | TEXT | Y | NULL | `EASY` / `MEDIUM` / `HARD` |
| `status` | TEXT | N | `ACTIVE` | `ACTIVE` / `ARCHIVED` |
| `created_at` | INTEGER | N | — | |
| `updated_at` | INTEGER | N | — | |
| `deleted_at` | INTEGER | Y | NULL | |
| `revision` | INTEGER | N | 1 | |

- `UNIQUE(normalized_name) WHERE deleted_at IS NULL`。
- `INDEX(category, status)`、`INDEX(normalized_name)`。
- 高汤/预制件通过 `category` 表达，不建独立库。

### 4.5 `recipe_version`

| 列 | 类型 | 空 | 默认 | 约束/说明 |
|---|---|---|---|---|
| `id` | TEXT | N | — | PK |
| `recipe_id` | TEXT | N | — | FK `recipe.id` ON DELETE CASCADE |
| `version_no` | INTEGER | N | — | |
| `status` | TEXT | N | `DRAFT` | `DRAFT` / `PUBLISHED` / `SUPERSEDED` |
| `yield_qty` | REAL | N | — | > 0 |
| `yield_unit_id` | TEXT | N | — | FK `unit.id` |
| `instructions_md` | TEXT | Y | NULL | 步骤 |
| `change_note` | TEXT | Y | NULL | |
| `based_on_version_id` | TEXT | Y | NULL | FK 自身 |
| `is_current` | INTEGER | N | 0 | |
| `created_at` | INTEGER | N | — | |
| `published_at` | INTEGER | Y | NULL | |

- `UNIQUE(recipe_id, version_no)`。
- `UNIQUE(recipe_id) WHERE is_current = 1`。
- `CHECK(yield_qty > 0)`；`CHECK(is_current IN (0,1))`。
- `INDEX(recipe_id, status)`、`INDEX(yield_unit_id)`。

### 4.6 `recipe_component`

结构与 `sauce_component` 对称：

| 列 | 类型 | 空 | 默认 | 约束/说明 |
|---|---|---|---|---|
| `id` | TEXT | N | — | PK |
| `recipe_version_id` | TEXT | N | — | 所属版本 FK `recipe_version.id` ON DELETE CASCADE |
| `line_no` | INTEGER | N | — | |
| `component_type` | TEXT | N | — | `INGREDIENT` / `SAUCE` / `RECIPE` |
| `ingredient_id` | TEXT | Y | NULL | |
| `referenced_sauce_id` | TEXT | Y | NULL | |
| `referenced_sauce_version_id` | TEXT | Y | NULL | |
| `referenced_recipe_id` | TEXT | Y | NULL | |
| `referenced_recipe_version_id` | TEXT | Y | NULL | |
| `quantity` | REAL | N | — | > 0 |
| `unit_id` | TEXT | N | — | FK `unit.id` |
| `preparation_note` | TEXT | Y | NULL | |
| `is_optional` | INTEGER | N | 0 | |
| `group_label` | TEXT | Y | NULL | |
| `sort_order` | INTEGER | N | 0 | |
| `created_at` | INTEGER | N | — | |

- 与 `sauce_component` 相同的三选一 CHECK、唯一约束与索引。
- `UNIQUE(recipe_version_id, line_no)`。

## 5. 供应商、商品与条码

### 5.1 `supplier`

| 列 | 类型 | 空 | 默认 | 约束/说明 |
|---|---|---|---|---|
| `id` | TEXT | N | — | PK |
| `name` | TEXT | N | — | |
| `normalized_name` | TEXT | N | — | |
| `contact_name` | TEXT | Y | NULL | |
| `phone` | TEXT | Y | NULL | |
| `email` | TEXT | Y | NULL | |
| `address` | TEXT | Y | NULL | |
| `notes` | TEXT | Y | NULL | |
| `status` | TEXT | N | `ACTIVE` | |
| `created_at` | INTEGER | N | — | |
| `updated_at` | INTEGER | N | — | |
| `deleted_at` | INTEGER | Y | NULL | |
| `revision` | INTEGER | N | 1 | |

- `UNIQUE(normalized_name) WHERE deleted_at IS NULL`。
- `INDEX(status)`。

### 5.2 `product`

实际采购 SKU。**一个商品只属于一个食材；一个食材可有多个商品。**

| 列 | 类型 | 空 | 默认 | 约束/说明 |
|---|---|---|---|---|
| `id` | TEXT | N | — | PK |
| `ingredient_id` | TEXT | N | — | FK `ingredient.id` ON DELETE RESTRICT |
| `supplier_id` | TEXT | Y | NULL | FK `supplier.id` ON DELETE SET NULL |
| `name` | TEXT | N | — | 如“Président 黄油” |
| `normalized_name` | TEXT | N | — | |
| `brand` | TEXT | Y | NULL | |
| `sku` | TEXT | Y | NULL | 供应商货号 |
| `package_label` | TEXT | Y | NULL | 如“200g 装” |
| `content_qty` | REAL | N | — | 包装内容量 > 0 |
| `content_unit_id` | TEXT | N | — | FK `unit.id` |
| `stock_unit_id` | TEXT | N | — | 库存记账单位，通常 `pack` |
| `stock_to_content_factor` | REAL | N | — | 1 库存单位 = factor × 内容单位；> 0 |
| `default_location_id` | TEXT | Y | NULL | FK `storage_location.id` ON DELETE SET NULL |
| `shelf_life_days` | INTEGER | Y | NULL | 参考 |
| `storage_note` | TEXT | Y | NULL | |
| `last_unit_price_minor` | INTEGER | Y | NULL | 最近采购单价，最小货币单位 |
| `currency_code` | TEXT | N | `CNY` | ISO 4217 |
| `status` | TEXT | N | `ACTIVE` | `ACTIVE` / `ARCHIVED` |
| `created_at` | INTEGER | N | — | |
| `updated_at` | INTEGER | N | — | |
| `deleted_at` | INTEGER | Y | NULL | |
| `revision` | INTEGER | N | 1 | |

- `CHECK(content_qty > 0)`、`CHECK(stock_to_content_factor > 0)`。
- `INDEX(ingredient_id, status)`、`INDEX(supplier_id)`、`INDEX(normalized_name)`、`INDEX(default_location_id)`。
- 商品归档后不可用于新计划/采购，但库存与历史保留。
- 包装规格变更：v1 建议新建商品，不原地改历史（O-13）。

### 5.3 `barcode`

| 列 | 类型 | 空 | 默认 | 约束/说明 |
|---|---|---|---|---|
| `id` | TEXT | N | — | PK |
| `product_id` | TEXT | N | — | FK `product.id` ON DELETE CASCADE |
| `code` | TEXT | N | — | 条码原文 |
| `symbology` | TEXT | Y | NULL | `EAN13` / `EAN8` / `UPC_A` / `CODE128` / `QR` / `UNKNOWN` |
| `is_primary` | INTEGER | N | 0 | |
| `created_at` | INTEGER | N | — | |

- `UNIQUE(code)`：一个条码只对应一个商品（O-15）。
- `UNIQUE(product_id) WHERE is_primary = 1`（部分唯一索引）。
- `INDEX(product_id)`。

## 6. 位置与库存

### 6.1 `storage_location`

| 列 | 类型 | 空 | 默认 | 约束/说明 |
|---|---|---|---|---|
| `id` | TEXT | N | — | PK |
| `name` | TEXT | N | — | |
| `normalized_name` | TEXT | N | — | |
| `parent_id` | TEXT | Y | NULL | FK 自身 ON DELETE RESTRICT |
| `kind` | TEXT | N | `OTHER` | `PANTRY` / `FRIDGE` / `FREEZER` / `DRY` / `OTHER` |
| `notes` | TEXT | Y | NULL | |
| `sort_order` | INTEGER | N | 0 | |
| `status` | TEXT | N | `ACTIVE` | |
| `created_at` | INTEGER | N | — | |
| `updated_at` | INTEGER | N | — | |
| `revision` | INTEGER | N | 1 | |

- `INDEX(parent_id)`、`INDEX(status)`。
- 同一父级下名称唯一由应用校验（SQLite 对 NULL 父级的唯一索引行为需注意）。
- 不允许把位置移动到自己的后代（应用递归校验）。

### 6.2 `inventory_batch`

库存最小追溯单元。余额是物化值，流水是事实。

| 列 | 类型 | 空 | 默认 | 约束/说明 |
|---|---|---|---|---|
| `id` | TEXT | N | — | PK |
| `product_id` | TEXT | N | — | FK `product.id` ON DELETE RESTRICT |
| `location_id` | TEXT | N | — | FK `storage_location.id` ON DELETE RESTRICT |
| `batch_code` | TEXT | Y | NULL | 供应商批次/自制批次 |
| `received_at` | INTEGER | N | — | 入库时间 |
| `expiry_date` | TEXT | Y | NULL | `YYYY-MM-DD` |
| `quantity_on_hand` | REAL | N | 0 | ≥ 0 |
| `quantity_reserved` | REAL | N | 0 | ≥ 0；≤ on_hand |
| `stock_unit_id` | TEXT | N | — | FK `unit.id`；与商品库存单位一致 |
| `unit_cost_minor` | INTEGER | Y | NULL | 入库单价 |
| `currency_code` | TEXT | N | `CNY` | |
| `source_type` | TEXT | Y | NULL | `PURCHASE_RECEIPT` / `MANUAL` / `RETURN` / `ADJUSTMENT` |
| `source_id` | TEXT | Y | NULL | 收货明细/调整单 ID |
| `status` | TEXT | N | `ACTIVE` | `ACTIVE` / `DEPLETED` / `EXPIRED` / `DISCARDED` |
| `created_at` | INTEGER | N | — | |
| `updated_at` | INTEGER | N | — | |
| `revision` | INTEGER | N | 1 | |

**约束：**

```sql
CHECK(quantity_on_hand >= 0)
CHECK(quantity_reserved >= 0)
CHECK(quantity_reserved <= quantity_on_hand)
CHECK(status IN ('ACTIVE','DEPLETED','EXPIRED','DISCARDED'))
```

- `INDEX(product_id, status)`、`INDEX(expiry_date)`、`INDEX(location_id)`、`INDEX(stock_unit_id)`。
- 批次不硬删；耗尽置 `DEPLETED`。
- `source_type`/`source_id` 不是外键，避免多来源循环依赖；应用校验。

### 6.3 `inventory_ledger`

**追加式库存流水。禁止 UPDATE/DELETE。**

| 列 | 类型 | 空 | 默认 | 约束/说明 |
|---|---|---|---|---|
| `id` | TEXT | N | — | PK |
| `batch_id` | TEXT | N | — | FK `inventory_batch.id` ON DELETE RESTRICT |
| `product_id` | TEXT | N | — | 冗余快照，便于查询 |
| `location_id` | TEXT | N | — | 发生位置 |
| `type` | TEXT | N | — | 见枚举 |
| `on_hand_delta` | REAL | N | 0 | 在手变化 |
| `reserved_delta` | REAL | N | 0 | 预留变化 |
| `stock_unit_id` | TEXT | N | — | FK `unit.id` |
| `on_hand_after` | REAL | N | — | 操作后在手 |
| `reserved_after` | REAL | N | — | 操作后预留 |
| `from_location_id` | TEXT | Y | NULL | 调拨源 |
| `to_location_id` | TEXT | Y | NULL | 调拨目标 |
| `reference_type` | TEXT | Y | NULL | `PLAN` / `PO` / `RECEIPT` / `ADJUSTMENT` / `MANUAL` |
| `reference_id` | TEXT | Y | NULL | 对应业务 ID |
| `idempotency_key` | TEXT | N | — | 全局唯一，防重复过账 |
| `note` | TEXT | Y | NULL | 原因/备注 |
| `occurred_at` | INTEGER | N | — | 业务发生时间 |
| `created_at` | INTEGER | N | — | 写入时间 |

**约束：**

```sql
CHECK(on_hand_after >= 0)
CHECK(reserved_after >= 0)
CHECK(reserved_after <= on_hand_after)
UNIQUE(idempotency_key)
```

- `INDEX(batch_id, occurred_at)`、`INDEX(product_id, occurred_at)`、`INDEX(reference_type, reference_id)`、`INDEX(location_id)`。
- `idempotency_key` 建议格式：`<type>:<referenceId>:<batchId>:<sequence>`。
- 触发器禁止 UPDATE/DELETE（见第 10 节）。

### 6.4 `inventory_reservation`

| 列 | 类型 | 空 | 默认 | 约束/说明 |
|---|---|---|---|---|
| `id` | TEXT | N | — | PK |
| `plan_id` | TEXT | N | — | FK `production_plan.id` ON DELETE RESTRICT |
| `plan_item_id` | TEXT | Y | NULL | FK `production_plan_item.id` ON DELETE RESTRICT |
| `requirement_id` | TEXT | Y | NULL | FK `material_requirement.id` ON DELETE RESTRICT |
| `batch_id` | TEXT | N | — | FK `inventory_batch.id` ON DELETE RESTRICT |
| `product_id` | TEXT | N | — | 冗余快照 |
| `quantity` | REAL | N | — | > 0，库存单位 |
| `stock_unit_id` | TEXT | N | — | FK `unit.id` |
| `status` | TEXT | N | `ACTIVE` | `ACTIVE` / `RELEASED` / `CONSUMED` |
| `created_at` | INTEGER | N | — | |
| `released_at` | INTEGER | Y | NULL | |
| `consumed_at` | INTEGER | Y | NULL | |

- `CHECK(quantity > 0)`。
- `UNIQUE(requirement_id, batch_id) WHERE status = 'ACTIVE'`（避免同一需求重复预留同一批次）。
- `INDEX(plan_id, status)`、`INDEX(batch_id, status)`、`INDEX(requirement_id)`。

## 7. 制作计划

### 7.1 `production_plan`

| 列 | 类型 | 空 | 默认 | 约束/说明 |
|---|---|---|---|---|
| `id` | TEXT | N | — | PK |
| `title` | TEXT | N | — | |
| `planned_date` | TEXT | Y | NULL | 本地日期；可空 |
| `timezone` | TEXT | N | — | IANA，如 `Asia/Hong_Kong` |
| `status` | TEXT | N | `DRAFT` | 见枚举 |
| `notes` | TEXT | Y | NULL | |
| `current_snapshot_id` | TEXT | Y | NULL | FK `production_plan_snapshot.id`，确认后设置 |
| `confirmed_at` | INTEGER | Y | NULL | |
| `completed_at` | INTEGER | Y | NULL | |
| `created_at` | INTEGER | N | — | |
| `updated_at` | INTEGER | N | — | |
| `revision` | INTEGER | N | 1 | |

- `CHECK(status IN (...))`。
- `INDEX(status, planned_date)`、`INDEX(current_snapshot_id)`。
- 已确认计划的明细不可直接改；刷新生成新快照（O-05）。

### 7.2 `production_plan_item`

| 列 | 类型 | 空 | 默认 | 约束/说明 |
|---|---|---|---|---|
| `id` | TEXT | N | — | PK |
| `plan_id` | TEXT | N | — | FK `production_plan.id` ON DELETE CASCADE |
| `recipe_id` | TEXT | N | — | FK `recipe.id` ON DELETE RESTRICT |
| `recipe_version_id` | TEXT | N | — | FK `recipe_version.id` ON DELETE RESTRICT；固定版本 |
| `quantity` | REAL | N | — | > 0 |
| `unit_id` | TEXT | N | — | FK `unit.id`；目标产量单位 |
| `sort_order` | INTEGER | N | 0 | |
| `note` | TEXT | Y | NULL | |
| `status` | TEXT | N | `PENDING` | `PENDING` / `DONE` / `CANCELLED` |
| `created_at` | INTEGER | N | — | |
| `updated_at` | INTEGER | N | — | |

- `CHECK(quantity > 0)`。
- `INDEX(plan_id, sort_order)`、`INDEX(recipe_version_id)`。
- 应用校验 `recipe_version.recipe_id = recipe_id` 且版本已发布。

### 7.3 `production_plan_snapshot`

| 列 | 类型 | 空 | 默认 | 约束/说明 |
|---|---|---|---|---|
| `id` | TEXT | N | — | PK |
| `plan_id` | TEXT | N | — | FK `production_plan.id` ON DELETE CASCADE |
| `revision_no` | INTEGER | N | — | 从 1 递增 |
| `engine_version` | TEXT | N | — | 展开/换算引擎版本，如 `1.0.0` |
| `payload_json` | TEXT | N | — | 完整展开树、输入参数、策略、单位快照 |
| `payload_hash` | TEXT | N | — | SHA-256，防篡改/对比 |
| `requirement_count` | INTEGER | N | 0 | 汇总行数 |
| `created_at` | INTEGER | N | — | |

- `UNIQUE(plan_id, revision_no)`。
- `INDEX(plan_id, created_at)`。
- `payload_json` 只追加，不更新；用于回放与差异对比。

### 7.4 `material_requirement`

| 列 | 类型 | 空 | 默认 | 约束/说明 |
|---|---|---|---|---|
| `id` | TEXT | N | — | PK |
| `snapshot_id` | TEXT | N | — | FK `production_plan_snapshot.id` ON DELETE CASCADE |
| `ingredient_id` | TEXT | N | — | FK `ingredient.id` ON DELETE RESTRICT |
| `required_qty` | REAL | N | — | ≥ 0 |
| `unit_id` | TEXT | N | — | 汇总单位 FK |
| `dimension` | TEXT | N | — | 冗余，便于查询 |
| `aggregation_key` | TEXT | N | — | `ingredientId|unitId|optional` |
| `available_qty_snapshot` | REAL | N | 0 | 确认时可用量 |
| `shortage_qty` | REAL | N | 0 | max(0, required - available) |
| `is_optional` | INTEGER | N | 0 | |
| `conversion_status` | TEXT | N | `OK` | `OK` / `UNCONVERTIBLE` / `MISSING_PRODUCT` / `UNIT_MISMATCH` |
| `created_at` | INTEGER | N | — | |

- `UNIQUE(snapshot_id, aggregation_key)`。
- `INDEX(snapshot_id)`、`INDEX(ingredient_id)`。
- 可选行默认 `shortage_qty = 0`，但仍显示需求（O-02）。

### 7.5 `material_requirement_source`

| 列 | 类型 | 空 | 默认 | 约束/说明 |
|---|---|---|---|---|
| `id` | TEXT | N | — | PK |
| `requirement_id` | TEXT | N | — | FK `material_requirement.id` ON DELETE CASCADE |
| `plan_item_id` | TEXT | N | — | FK `production_plan_item.id` ON DELETE CASCADE |
| `source_path` | TEXT | N | — | 如 `item:1/line:2/line:1` |
| `component_type` | TEXT | N | — | `INGREDIENT` / `SAUCE` / `RECIPE` |
| `component_id` | TEXT | N | — | 组成行 ID |
| `component_version_id` | TEXT | Y | NULL | 子版本 ID |
| `quantity` | REAL | N | — | 该来源贡献量 |
| `unit_id` | TEXT | N | — | 贡献单位 |
| `depth` | INTEGER | N | 0 | 展开深度 |
| `is_optional` | INTEGER | N | 0 | |
| `note` | TEXT | Y | NULL | |

- `INDEX(requirement_id)`、`INDEX(plan_item_id)`。
- 用于回答“这个需求从哪来”。

## 8. 采购与收货

### 8.1 `procurement_requirement`

| 列 | 类型 | 空 | 默认 | 约束/说明 |
|---|---|---|---|---|
| `id` | TEXT | N | — | PK |
| `plan_id` | TEXT | N | — | FK `production_plan.id` ON DELETE RESTRICT |
| `snapshot_id` | TEXT | N | — | FK `production_plan_snapshot.id` ON DELETE RESTRICT |
| `requirement_id` | TEXT | N | — | FK `material_requirement.id` ON DELETE RESTRICT |
| `ingredient_id` | TEXT | N | — | FK `ingredient.id` |
| `product_id` | TEXT | Y | NULL | FK `product.id` ON DELETE SET NULL |
| `requested_qty` | REAL | N | — | > 0 |
| `unit_id` | TEXT | N | — | FK `unit.id` |
| `status` | TEXT | N | `OPEN` | `OPEN` / `ORDERED` / `PARTIAL` / `RECEIVED` / `CANCELLED` |
| `purchase_order_item_id` | TEXT | Y | NULL | FK `purchase_order_item.id` ON DELETE SET NULL |
| `note` | TEXT | Y | NULL | |
| `created_at` | INTEGER | N | — | |
| `updated_at` | INTEGER | N | — | |

- `INDEX(plan_id, status)`、`INDEX(requirement_id)`、`INDEX(product_id)`。
- 一个缺口可由多个商品/采购行满足；不强制唯一。

### 8.2 `purchase_order`

| 列 | 类型 | 空 | 默认 | 约束/说明 |
|---|---|---|---|---|
| `id` | TEXT | N | — | PK |
| `code` | TEXT | N | — | 人工/系统生成，唯一 |
| `supplier_id` | TEXT | Y | NULL | FK `supplier.id` ON DELETE SET NULL |
| `status` | TEXT | N | `DRAFT` | 见枚举 |
| `ordered_at` | INTEGER | Y | NULL | 下单时间 |
| `expected_date` | TEXT | Y | NULL | 预计到货 |
| `currency_code` | TEXT | N | `CNY` | |
| `allow_over_receipt` | INTEGER | N | 0 | 是否允许超收（O-09） |
| `notes` | TEXT | Y | NULL | |
| `source_plan_id` | TEXT | Y | NULL | FK `production_plan.id` ON DELETE SET NULL |
| `created_at` | INTEGER | N | — | |
| `updated_at` | INTEGER | N | — | |
| `revision` | INTEGER | N | 1 | |

- `UNIQUE(code)`。
- `INDEX(status, ordered_at)`、`INDEX(supplier_id)`、`INDEX(source_plan_id)`。

### 8.3 `purchase_order_item`

| 列 | 类型 | 空 | 默认 | 约束/说明 |
|---|---|---|---|---|
| `id` | TEXT | N | — | PK |
| `purchase_order_id` | TEXT | N | — | FK `purchase_order.id` ON DELETE CASCADE |
| `product_id` | TEXT | N | — | FK `product.id` ON DELETE RESTRICT |
| `ordered_qty` | REAL | N | — | > 0 |
| `unit_id` | TEXT | N | — | 库存单位 FK |
| `unit_price_minor` | INTEGER | Y | NULL | 单价 |
| `received_qty` | REAL | N | 0 | ≥ 0；由收货累加 |
| `cancelled_qty` | REAL | N | 0 | ≥ 0；≤ ordered |
| `status` | TEXT | N | `OPEN` | `OPEN` / `PARTIAL` / `RECEIVED` / `CANCELLED` |
| `note` | TEXT | Y | NULL | |
| `created_at` | INTEGER | N | — | |
| `updated_at` | INTEGER | N | — | |

- `CHECK(ordered_qty > 0)`、`CHECK(received_qty >= 0)`、`CHECK(cancelled_qty >= 0)`、`CHECK(cancelled_qty <= ordered_qty)`。
- `INDEX(purchase_order_id)`、`INDEX(product_id)`。

### 8.4 `goods_receipt`

| 列 | 类型 | 空 | 默认 | 约束/说明 |
|---|---|---|---|---|
| `id` | TEXT | N | — | PK |
| `purchase_order_id` | TEXT | N | — | FK `purchase_order.id` ON DELETE RESTRICT |
| `receipt_no` | TEXT | N | — | 唯一 |
| `received_at` | INTEGER | N | — | 到货时间 |
| `location_id` | TEXT | N | — | FK `storage_location.id` |
| `status` | TEXT | N | `DRAFT` | `DRAFT` / `POSTED` / `VOID` |
| `note` | TEXT | Y | NULL | |
| `created_at` | INTEGER | N | — | |
| `posted_at` | INTEGER | Y | NULL | |
| `voided_at` | INTEGER | Y | NULL | |

- `UNIQUE(receipt_no)`。
- `INDEX(purchase_order_id, status)`、`INDEX(received_at)`。

### 8.5 `goods_receipt_item`

| 列 | 类型 | 空 | 默认 | 约束/说明 |
|---|---|---|---|---|
| `id` | TEXT | N | — | PK |
| `goods_receipt_id` | TEXT | N | — | FK `goods_receipt.id` ON DELETE CASCADE |
| `purchase_order_item_id` | TEXT | N | — | FK `purchase_order_item.id` ON DELETE RESTRICT |
| `product_id` | TEXT | N | — | FK `product.id` ON DELETE RESTRICT |
| `quantity` | REAL | N | — | > 0 |
| `stock_unit_id` | TEXT | N | — | FK `unit.id` |
| `batch_code` | TEXT | Y | NULL | |
| `expiry_date` | TEXT | Y | NULL | |
| `unit_cost_minor` | INTEGER | Y | NULL | 实收单价 |
| `created_batch_id` | TEXT | Y | NULL | FK `inventory_batch.id` ON DELETE SET NULL |
| `note` | TEXT | Y | NULL | 差异说明 |

- `CHECK(quantity > 0)`。
- `INDEX(goods_receipt_id)`、`INDEX(purchase_order_item_id)`、`INDEX(created_batch_id)`。

## 9. 横切表

### 9.1 `image_asset`

| 列 | 类型 | 空 | 默认 | 约束/说明 |
|---|---|---|---|---|
| `id` | TEXT | N | — | PK |
| `owner_type` | TEXT | N | — | 见枚举 |
| `owner_id` | TEXT | N | — | 被引用实体 ID（无外键） |
| `role` | TEXT | N | — | `PRIMARY` / `PROCESS` / `REFERENCE` |
| `step_no` | INTEGER | Y | NULL | 过程图对应步骤 |
| `relative_path` | TEXT | N | — | 相对 `filesDir/images` |
| `mime_type` | TEXT | N | — | `image/webp` 等 |
| `width` | INTEGER | Y | NULL | 像素 |
| `height` | INTEGER | Y | NULL | 像素 |
| `byte_size` | INTEGER | N | — | 字节 |
| `sha256` | TEXT | N | — | 文件校验 |
| `sort_order` | INTEGER | N | 0 | |
| `created_at` | INTEGER | N | — | |
| `deleted_at` | INTEGER | Y | NULL | 软删除 |

- `UNIQUE(relative_path)`。
- 部分唯一：`UNIQUE(owner_type, owner_id) WHERE role='PRIMARY' AND deleted_at IS NULL`。
- `INDEX(owner_type, owner_id, sort_order)`。
- 文件缺失时保留行并标记诊断；不因缺图阻塞档案。

### 9.2 `change_log`

| 列 | 类型 | 空 | 默认 | 约束/说明 |
|---|---|---|---|---|
| `id` | TEXT | N | — | PK |
| `entity_type` | TEXT | N | — | `INGREDIENT` / `RECIPE` / `PRODUCT` / `PLAN` 等 |
| `entity_id` | TEXT | N | — | 实体 ID（无外键） |
| `action` | TEXT | N | — | 见枚举 |
| `before_json` | TEXT | Y | NULL | 修改前快照 |
| `after_json` | TEXT | Y | NULL | 修改后快照 |
| `change_reason` | TEXT | Y | NULL | 用户填写 |
| `app_version` | TEXT | Y | NULL | |
| `schema_version` | INTEGER | Y | NULL | |
| `created_at` | INTEGER | N | — | |

- `INDEX(entity_type, entity_id, created_at)`、`INDEX(created_at)`。
- 只追加；不存敏感信息；大字段截断并标记。

### 9.3 `backup_record`

| 列 | 类型 | 空 | 默认 | 约束/说明 |
|---|---|---|---|---|
| `id` | TEXT | N | — | PK |
| `kind` | TEXT | N | — | `AUTO` / `MANUAL` / `PRE_MIGRATION` / `PRE_RESTORE` |
| `file_name` | TEXT | N | — | |
| `file_uri` | TEXT | Y | NULL | SAF URI 或相对路径 |
| `created_at` | INTEGER | N | — | |
| `size_bytes` | INTEGER | N | — | |
| `sha256` | TEXT | N | — | |
| `schema_version` | INTEGER | N | — | 备份时 DB schema |
| `app_version_name` | TEXT | N | — | |
| `status` | TEXT | N | `SUCCESS` | `SUCCESS` / `FAILED` / `VERIFIED` |
| `note` | TEXT | Y | NULL | |

- `INDEX(created_at)`、`INDEX(kind, created_at)`。

### 9.4 `app_metadata`

| 列 | 类型 | 空 | 默认 | 约束/说明 |
|---|---|---|---|---|
| `key` | TEXT | N | — | PK |
| `value` | TEXT | Y | NULL | |
| `updated_at` | INTEGER | N | — | |

建议键：

| key | 用途 |
|---|---|
| `install_id` | 安装实例 ID，用于备份/诊断 |
| `db_created_at` | 数据库首次创建时间 |
| `last_backup_at` | 最近成功备份 |
| `last_migration_at` | 最近迁移 |
| `last_integrity_check_at` | 最近完整性检查 |
| `schema_engine_version` | 展开引擎版本 |

## 10. 关键 DDL 与触发器

### 10.1 库存流水只追加

```sql
CREATE TRIGGER trg_inventory_ledger_no_update
BEFORE UPDATE ON inventory_ledger
BEGIN
  SELECT RAISE(ABORT, 'inventory_ledger is append-only');
END;

CREATE TRIGGER trg_inventory_ledger_no_delete
BEFORE DELETE ON inventory_ledger
BEGIN
  SELECT RAISE(ABORT, 'inventory_ledger is append-only');
END;
```

### 10.2 已发布版本不可删/不可改

```sql
CREATE TRIGGER trg_recipe_version_no_update_published
BEFORE UPDATE ON recipe_version
WHEN OLD.status IN ('PUBLISHED','SUPERSEDED')
 AND (
   NEW.recipe_id IS NOT OLD.recipe_id OR
   NEW.version_no IS NOT OLD.version_no OR
   NEW.yield_qty IS NOT OLD.yield_qty OR
   NEW.yield_unit_id IS NOT OLD.yield_unit_id OR
   NEW.instructions_md IS NOT OLD.instructions_md OR
   NEW.change_note IS NOT OLD.change_note OR
   NEW.based_on_version_id IS NOT OLD.based_on_version_id OR
   NEW.published_at IS NOT OLD.published_at
 )
BEGIN
  SELECT RAISE(ABORT, 'published recipe version content is immutable');
END;

CREATE TRIGGER trg_recipe_version_no_delete_published
BEFORE DELETE ON recipe_version
WHEN OLD.status IN ('PUBLISHED','SUPERSEDED')
BEGIN
  SELECT RAISE(ABORT, 'published recipe version cannot be deleted');
END;

CREATE TRIGGER trg_sauce_version_no_update_published
BEFORE UPDATE ON sauce_version
WHEN OLD.status IN ('PUBLISHED','SUPERSEDED')
 AND (
   NEW.sauce_id IS NOT OLD.sauce_id OR
   NEW.version_no IS NOT OLD.version_no OR
   NEW.yield_qty IS NOT OLD.yield_qty OR
   NEW.yield_unit_id IS NOT OLD.yield_unit_id OR
   NEW.instructions_md IS NOT OLD.instructions_md OR
   NEW.change_note IS NOT OLD.change_note OR
   NEW.based_on_version_id IS NOT OLD.based_on_version_id OR
   NEW.published_at IS NOT OLD.published_at
 )
BEGIN
  SELECT RAISE(ABORT, 'published sauce version content is immutable');
END;

CREATE TRIGGER trg_sauce_version_no_delete_published
BEFORE DELETE ON sauce_version
WHEN OLD.status IN ('PUBLISHED','SUPERSEDED')
BEGIN
  SELECT RAISE(ABORT, 'published sauce version cannot be deleted');
END;
```

> 说明：`status` 与 `is_current` 不在保护字段内，发布事务可以将旧 `PUBLISHED` 置为 `SUPERSEDED`；内容字段不可改。删除草稿版本允许，删除已发布/被替代版本禁止。应用层仍需做同样的校验，触发器是最后一道防线。

### 10.3 视图

```sql
CREATE VIEW v_batch_available AS
SELECT
  b.id AS batch_id,
  b.product_id,
  b.location_id,
  b.stock_unit_id,
  b.quantity_on_hand,
  b.quantity_reserved,
  (b.quantity_on_hand - b.quantity_reserved) AS quantity_available,
  b.expiry_date,
  b.status
FROM inventory_batch b;

CREATE VIEW v_product_stock AS
SELECT
  product_id,
  stock_unit_id,
  SUM(quantity_on_hand) AS on_hand,
  SUM(quantity_reserved) AS reserved,
  SUM(quantity_on_hand - quantity_reserved) AS available
FROM inventory_batch
WHERE status = 'ACTIVE'
GROUP BY product_id, stock_unit_id;
```

## 11. Room 映射约定

| SQLite | Kotlin/Room |
|---|---|
| `TEXT id` | `String` 或 `@JvmInline value class IngredientId(val value: String)` |
| 枚举 `TEXT` | `enum class` + `@TypeConverter` |
| `INTEGER` 时间 | `Long`；领域层转 `Instant` |
| `TEXT` 日期 | `String`；领域层转 `LocalDate` |
| `REAL` | `Double` |
| `INTEGER` 金额 | `Long` |
| `TEXT` JSON | `String`；`kotlinx.serialization` 在 data 层解析 |
| 布尔 | `Boolean` + converter |

- Entity 放 `core:database/entity`，不暴露到 domain。
- DAO 返回 Entity/投影；Repository 在 `core:data` 映射。
- `@DatabaseView` 对应第 10.3 节视图。
- 所有 FK 建 `@Index`；唯一索引用 `@Index(unique = true)`。
- 不使用 `fallbackToDestructiveMigration`。

## 12. 索引汇总（最低集合）

| 表 | 索引 |
|---|---|
| `unit` | `ux_unit_code`, `idx_unit_dimension_active` |
| `ingredient` | `ux_ingredient_name_active`, `idx_ingredient_status`, `idx_ingredient_category`, `idx_ingredient_default_unit` |
| `ingredient_alias` | `ux_ingredient_alias_norm`, `idx_ingredient_alias_ingredient` |
| `ingredient_unit_conversion` | `ux_iuc_pair`, `idx_iuc_ingredient` |
| `sauce` | `ux_sauce_name_active`, `idx_sauce_status` |
| `sauce_version` | `ux_sauce_version_no`, `ux_sauce_current`, `idx_sauce_version_status` |
| `sauce_component` | `ux_sauce_component_line`, `idx_sc_ingredient`, `idx_sc_ref_sauce`, `idx_sc_ref_recipe` |
| `recipe` | `ux_recipe_name_active`, `idx_recipe_category_status` |
| `recipe_version` | `ux_recipe_version_no`, `ux_recipe_current`, `idx_recipe_version_status` |
| `recipe_component` | `ux_recipe_component_line`, `idx_rc_ingredient`, `idx_rc_ref_sauce`, `idx_rc_ref_recipe` |
| `supplier` | `ux_supplier_name_active`, `idx_supplier_status` |
| `product` | `idx_product_ingredient_status`, `idx_product_supplier`, `idx_product_name`, `idx_product_location` |
| `barcode` | `ux_barcode_code`, `ux_barcode_primary`, `idx_barcode_product` |
| `storage_location` | `idx_location_parent`, `idx_location_status` |
| `inventory_batch` | `idx_batch_product_status`, `idx_batch_expiry`, `idx_batch_location` |
| `inventory_ledger` | `idx_ledger_batch_time`, `idx_ledger_product_time`, `idx_ledger_ref`, `idx_ledger_location` |
| `inventory_reservation` | `idx_reservation_plan_status`, `idx_reservation_batch_status`, `idx_reservation_requirement` |
| `production_plan` | `idx_plan_status_date`, `idx_plan_snapshot` |
| `production_plan_item` | `idx_plan_item_plan_sort`, `idx_plan_item_recipe_version` |
| `production_plan_snapshot` | `ux_snapshot_plan_revision`, `idx_snapshot_plan_time` |
| `material_requirement` | `ux_requirement_snapshot_key`, `idx_requirement_snapshot`, `idx_requirement_ingredient` |
| `material_requirement_source` | `idx_source_requirement`, `idx_source_plan_item` |
| `procurement_requirement` | `idx_proc_req_plan_status`, `idx_proc_req_requirement`, `idx_proc_req_product` |
| `purchase_order` | `ux_po_code`, `idx_po_status_date`, `idx_po_supplier`, `idx_po_source_plan` |
| `purchase_order_item` | `idx_po_item_po`, `idx_po_item_product` |
| `goods_receipt` | `ux_receipt_no`, `idx_receipt_po_status`, `idx_receipt_received_at` |
| `goods_receipt_item` | `idx_receipt_item_receipt`, `idx_receipt_item_po_item`, `idx_receipt_item_batch` |
| `image_asset` | `ux_image_path`, `ux_image_primary`, `idx_image_owner` |
| `change_log` | `idx_change_entity_time`, `idx_change_time` |
| `backup_record` | `idx_backup_time`, `idx_backup_kind_time` |

## 13. 数据校验清单

实现 DAO/Repository 时必须覆盖：

- [ ] `foreign_keys = ON` 实际生效。
- [ ] 库存余额不能为负；预留不能超过在手。
- [ ] 流水写入与余额更新同一事务。
- [ ] 已发布版本内容不可改。
- [ ] 计划项版本属于该菜谱且已发布。
- [ ] 组成行三选一字段严格匹配。
- [ ] 条码全局唯一；主条码每商品最多一个。
- [ ] 主图每个对象最多一张。
- [ ] 快照 payload_hash 与内容一致。
- [ ] 备份 manifest 的 schema/sha 校验通过。

## 14. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 0.1.0 | 2026-11-04 | 初稿；31 张表、枚举、约束、索引、DDL 与 Room 映射 | 架构组 |
