# S0-02 项目结构与架构核验报告

**任务编号**：MB-S0-001
**文档编号**：S0-02
**版本**：`1.0.0`
**状态**：`Review`
**日期**：2026-11-04
**检查对象**：Gradle 结构、模块依赖、数据库、导航、Compose 页面、测试、构建配置

---

## 1. 结论摘要

| 项 | 实际 | 结论 |
|---|---|---|
| 当前模块数 | **15** | 与 G2 D-004 一致 |
| 任务书所称「13 个目标模块」 | **13 是建议目标，不是当前事实** | 需 MB-D-003 签收后才能改造 |
| 依赖方向 | 15 节点 / 43 条声明边 / PASS | 当前无环；但检查脚本只查构建文件 |
| 源码级越界 | 未发现 feature→feature、feature→data/database、domain→Android、database→domain | **PASS（静态扫描）** |
| 数据库 | 31 表 / 31 Entity / 31 DAO / schema v1 | **PASS** |
| 迁移 | `CulinaryMigrations.ALL = emptyArray()` | v1 无迁移；任何变更需 v2 |
| 导航 | 9 个目的地；仅食材真实，7 个占位 | **框架级，不是产品** |
| Compose 页面 | 8 个含 `@Composable` 的文件 | 页面极少 |
| 测试 | 197 例 / 0 失败 / 39 个 JVM 类 | **PASS** |
| 设计系统 | 2 个文件 / 72 行 | 不足以支撑页面开发 |

**核心判断**：G2 的 **core 层与数据库**可继承；**feature 层与 UI**基本是空的；**模块集与产品范围**尚未按新任务书改造。

---

## 2. 当前模块结构

`settings.gradle.kts` 实际声明 15 个模块：

```text
:app
:core:common
:core:domain
:core:database
:core:data
:core:designsystem
:core:platform
:feature:ingredients
:feature:sauces
:feature:recipes
:feature:products
:feature:inventory
:feature:planning
:feature:procurement
:feature:settings
```

### 2.1 模块规模与状态

> LOC 为 `src` 下 `.kt` 行数；不含 `build/`。

| 模块 | 主文件 | 主 LOC | 测试文件 | 测试 LOC | 状态 |
|---|---:|---:|---:|---:|---|
| `:app` | 13 | 635 | 2 | 95 | 单 Activity、导航、启动门禁、Recovery、崩溃日志、调试页 |
| `:core:common` | 13 | 293 | 2 | 110 | Outcome/错误码/Clock/ID/Logger/Dispatcher |
| `:core:domain` | 38 | 1,052 | 8 | 817 | 食材/菜谱/单位模型、端口、算法；无酱汁/商品/库存模型 |
| `:core:database` | 95 | 3,805 | 10 | 2,152 | 31 Entity/DAO、schema、触发器、种子 |
| `:core:data` | 27 | 1,977 | 12 | 1,414 | Repository/Mapper/事务/图片/备份恢复 |
| `:core:designsystem` | 2 | 72 | 0 | 0 | 只有 Color + Theme |
| `:core:platform` | 11 | 515 | 5 | 292 | 权限/SAF/CameraX/ML Kit/WorkManager |
| `:feature:ingredients` | 4 | 378 | 1 | 38 | 唯一可用切片 |
| `:feature:sauces` | 1 | 8 | 0 | 0 | 占位 |
| `:feature:recipes` | 1 | 8 | 0 | 0 | 占位 |
| `:feature:products` | 1 | 8 | 0 | 0 | 占位 |
| `:feature:inventory` | 1 | 8 | 0 | 0 | 占位 |
| `:feature:planning` | 1 | 8 | 0 | 0 | 占位 |
| `:feature:procurement` | 1 | 8 | 0 | 0 | 占位 |
| `:feature:settings` | 1 | 8 | 0 | 0 | 占位（无备份/恢复入口） |

### 2.2 「13 个目标模块」的准确表述

任务书 S0-02 要求检查“现有 13 个目标模块”。实际仓库是 **15 个**，13 是 MB-D-003 的建议目标：

```text
删除 :feature:planning
删除 :feature:procurement
保留 :feature:products，但缩减为商品/包装/条码/食材映射 + 扫码
其余 13 个保留
```

**当前不能按 13 模块验收**，否则会把“建议”写成“已实现”。本报告按 15 个实际模块检查，并单列 13 模块的改造条件。

---

## 3. 模块依赖

### 3.1 声明依赖

`powershell -File scripts/check-module-deps.ps1`：

```text
modules: 15 (expected 15)
declared edges: 43
PASS: module count, module set and dependency directions match A03A.
```

允许方向：

```text
app -> 8 feature + core:data + core:platform + core:designsystem
feature:* -> core:domain + core:designsystem + core:common
core:domain -> core:common
core:database -> core:common
core:data -> core:domain + core:database + core:common
core:designsystem -> core:common
core:platform -> core:domain + core:common
```

### 3.2 源码级静态扫描

本次额外扫描（不只看 `build.gradle.kts`）：

| 检查 | 结果 |
|---|---|
| feature 跨 feature import | **0** |
| feature import `core:data` / `core:database` | **0** |
| `core:domain` import Android/Room/Compose | **0** |
| `core:database` import `core:domain` | **0** |
| `allowMainThreadQueries` 生产代码 | **0**；19 处均在测试（Robolectric 允许） |
| `fallbackToDestructiveMigration` 生产代码 | **0**；唯一命中是注释 |
| `GlobalScope` | **0** |

**结论**：当前 15 模块的边界在源码层面基本成立。

### 3.3 检查脚本的局限

`scripts/check-module-deps.ps1` 仍存在：

- 硬编码 15 模块白名单，删模块后必然 FAIL；
- 只解析 `project(":...")`，不检查 `import`；
- 不检查 UI→DAO、feature→feature、domain→Android、database→domain；
- 43 条是“声明边”，不是“实际编译依赖图”；
- 不检测循环（依赖白名单方向，而非拓扑排序）。

**Stage 1 必须重写**，否则 13 模块改造后无法作为门禁。

---

## 4. 数据库结构

### 4.1 Schema

| 项 | 值 |
|---|---|
| 版本 | `1` |
| Entity | **31** |
| DAO | **31** |
| identityHash | `b75e6e1b0cb2ce5b1eb24bad59e0a987` |
| `exportSchema` | `true` |
| 迁移 | `CulinaryMigrations.ALL = emptyArray()` |
| 破坏性迁移 | 未使用（仅注释提及禁止） |
| WAL | `JournalMode.WRITE_AHEAD_LOGGING` |
| 外键 | `PRAGMA foreign_keys = ON`（onOpen） |
| busy_timeout | 5000（onOpen） |
| synchronous | **未显式设置**（A-G2-001 写 NORMAL，实现未设置） |
| 部分唯一索引 | 9 条 |
| 触发器 | 报告 69 条；源码动态生成，本次未逐条复算 |

### 4.2 表归属

| 归属 | 表 | 数量 |
|---|---|---:|
| 食材 | `unit`、`ingredient`、`ingredient_alias`、`ingredient_unit_conversion` | 4 |
| 酱汁 | `sauce`、`sauce_version`、`sauce_component` | 3 |
| 菜谱 | `recipe`、`recipe_version`、`recipe_component` | 3 |
| 商品/扫码/位置 | `product`、`barcode`、`storage_location` | 3 |
| 库存 | `inventory_batch`、`inventory_ledger`、`inventory_reservation` | 3 |
| 跨切面 | `image_asset`、`change_log`、`backup_record`、`app_metadata` | 4 |
| 计划（休眠） | `production_plan`、`production_plan_item`、`production_plan_snapshot`、`material_requirement`、`material_requirement_source` | 5 |
| 采购（休眠） | `procurement_requirement`、`purchase_order`、`purchase_order_item`、`goods_receipt`、`goods_receipt_item` | 5 |
| 供应商（休眠） | `supplier` | 1 |
| **合计** | | **31** |

**11 张表**在新五项功能中没有入口：供应商、计划、采购。不能直接删除（禁止 destructive migration；需审计与迁移）。

### 4.3 迁移状态

- v1 无迁移；任何新增表/列都要 `Migration(1,2)`；
- 当前没有 `MigrationTest` 的 v1→v2 用例（只有 v1 建库/校验 2 例，历史通过，本次未跑）；
- `DatabaseFactory` 没有 `fallbackToDestructiveMigration`；
- 迁移前自动备份尚未接入（备份编排未接线）。

---

## 5. 导航与页面

### 5.1 导航

`CulinaryDestination` 9 个目的地：

```text
HOME, INGREDIENTS, SAUCES, RECIPES, PRODUCTS,
INVENTORY, PLANNING, PROCUREMENT, SETTINGS
```

`CulinaryNavHost`：

- `HOME` → 首页（8 个入口 + 调试）；
- `INGREDIENTS` → 真实食材 Route；
- 其余 7 个 → `PlaceholderScreen`；
- `debug` → 调试页。

### 5.2 Compose 页面

含 `@Composable` 的文件 8 个：

```text
app: CulinaryApp, DebugScreen, CulinaryNavHost, StartupScreens, HomeScreen, PlaceholderScreen
core:designsystem: Theme
feature:ingredients: IngredientsScreen
```

实际业务页面只有 **食材列表 + 对话框**。酱汁、菜谱、库存、扫码、设置没有页面。

### 5.3 UI 分层

`feature:ingredients` 是单包平铺：

```text
FeatureIngredientsModule.kt
IngredientsScreen.kt   (261 行)
IngredientsUiState.kt
IngredientsViewModel.kt
```

不符合任务书 §14 的 `presentation/list|detail|editor|components|navigation` 结构。其他 feature 只有 8 行占位模块。

---

## 6. 测试与构建配置

### 6.1 测试

- 197 例 / 0 失败 / 39 个 JVM 测试类；
- 1 个仪器化迁移测试类（2 例），本次未运行；
- 无 Compose UI 测试、无自动化 E2E、无扫码/SAF 设备测试；
- 测试集中在 core（database 77、data 43、domain 40、platform 19），feature 只有 3 例。

### 6.2 构建配置

| 项 | 值 |
|---|---|
| AGP | 8.13.0 |
| Kotlin | 2.2.20 |
| KSP | 2.2.20-2.0.4 |
| Gradle | 8.14.3 |
| JDK | 21 |
| JVM target | 17 |
| compileSdk / targetSdk / minSdk | 36 / 36 / 26 |
| Hilt | 2.57.2（禁止 2.59+） |
| Room | 2.8.5 |
| CameraX | 1.4.2 |
| ML Kit | 17.3.0 Bundled |
| Release | R8 + shrinkResources；unsigned |

---

## 7. 架构冲突

| # | 冲突 | 证据 | 影响 |
|---|---|---|---|
| A-01 | P01 仍定义四个执行模块（商品/库存/计划/采购） | `P01 §4.2`、`§6.2` | 与新五项功能冲突；采购单/部分到货仍写 Must |
| A-02 | D-004 锁定 15 模块 | `A03A §4/§11`、`README` | 与 13 模块目标冲突 |
| A-03 | 计划/采购表仍完整 | schema 11 张休眠表 | 范围回流风险 |
| A-04 | `feature:products` 职责未定 | 8 行占位 | 扫码归属未决（MB-D-004） |
| A-05 | `feature:settings` 无备份/恢复入口 | 8 行占位 | 数据安全能力不可达 |
| A-06 | 设计系统只有 2 个文件 | `core:designsystem` | 页面开发会各自造轮子 |
| A-07 | 食材页单包平铺 | 4 个文件 | 不满足页面隔离要求 |

---

## 8. 结构结论

1. **可继承**：15 模块的 core 分层、数据库 schema v1、依赖方向、构建/测试基线。
2. **需改造**：模块集 15→13（待签）、导航 9→5 功能+设置（待签）、products 缩减（待签）、数据库休眠表处置（待签）。
3. **需新建**：酱汁/菜谱/库存/扫码/设置页面与用例；设计系统；UI 测试。
4. **不可假装**：当前不是 13 模块；当前不是五项功能可用；当前没有扫码/备份 UI。

**Stage 1 的前置**：MB-D-002（范围）、MB-D-003（模块集）、MB-D-005（数据库残留）、MB-D-018（文档修正）。在这些签字前，只能做无行为变化的工程准备，不能删模块、改导航、动 schema。

---

## 9. 复现命令

```powershell
Select-String -Path settings.gradle.kts -Pattern '^include\('
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-module-deps.ps1
$j = Get-Content 'core\database\schemas\com.culinary.core.database.CulinaryDatabase\1.json' -Raw | ConvertFrom-Json
"$($j.database.version) / $($j.database.entities.Count) / $($j.database.identityHash)"
Get-Content 'core\database\src\main\java\com\culinary\core\database\CulinaryMigrations.kt'
Get-Content 'app\src\main\java\com\culinary\navigation\CulinaryDestination.kt'
```

---

## 10. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0.0 | 2026-11-04 | 完成 15 模块、依赖、数据库、导航、页面、测试与构建核验 | 接管工程组 |
