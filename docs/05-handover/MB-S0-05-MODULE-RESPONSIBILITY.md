# MISEBASE 模块职责审查表

**文档编号**：MB-S0-05
**版本**：`0.1.0`
**状态**：`Review`（处置建议；需项目负责人签字）
**日期**：2026-11-04
**审查对象**：G2 的 15 个 Gradle 模块
**目标范围**：五项业务功能（酱汁、食材、菜谱、库存、扫码）+ 设置基础设施

---

## 1. 结论摘要

| 项 | 结论 |
|---|---|
| 现有模块数 | 15（6 core + 8 feature + app） |
| 建议目标模块数 | **13**（6 core + 6 feature + app） |
| 删除 | `feature:planning`、`feature:procurement` |
| 缩减/复用 | `feature:products` → 商品/包装/条码/食材映射 + 扫码 UI |
| 保留并建设 | `feature:ingredients`、`feature:sauces`、`feature:recipes`、`feature:inventory`、`feature:settings` |
| Core 层 | 6 个全部保留；`core:designsystem` 需从 2 个主题文件扩展为真正设计系统 |
| 数据库 | schema v1 暂不修改；11 张表定性为休眠/待迁移，不直接删除 |
| 依赖规则 | 方向不变；模块集与检查脚本必须重写 |

**这不是立即执行的代码修改指令。** 在负责人签字（MB-D-003）前，只作为审查建议。

---

## 2. 现状盘点

> LOC 为 `src` 下 `.kt` 行数，不含构建产物；统计时间 2026-11-04。

| # | 模块 | 主代码文件 | 主代码 LOC | 测试文件 | 测试 LOC | 现状 |
|---|---|---:|---:|---:|---:|---|
| 1 | `:app` | 13 | 635 | 2 | 95 | 单 Activity、导航、启动门禁、Recovery、崩溃日志、调试页 |
| 2 | `:core:common` | 13 | 293 | 2 | 110 | Outcome/错误码/Clock/ID/Logger/Dispatcher |
| 3 | `:core:domain` | 38 | 1,052 | 8 | 817 | 食材/菜谱/单位模型、端口、算法；无酱汁/商品/库存模型 |
| 4 | `:core:database` | 95 | 3,805 | 10 | 2,152 | 31 Entity/DAO、schema、触发器、种子 |
| 5 | `:core:data` | 27 | 1,977 | 12 | 1,414 | Repository/Mapper/事务/图片/备份恢复 |
| 6 | `:core:designsystem` | 2 | 72 | 0 | 0 | 只有 Color + Theme；无字体/间距/组件 |
| 7 | `:core:platform` | 11 | 515 | 5 | 292 | 权限/SAF/CameraX/ML Kit/WorkManager |
| 8 | `:feature:ingredients` | 4 | 378 | 1 | 38 | 唯一可用切片：列表/新建/编辑/归档 |
| 9 | `:feature:sauces` | 1 | 8 | 0 | 0 | 占位 |
| 10 | `:feature:recipes` | 1 | 8 | 0 | 0 | 占位 |
| 11 | `:feature:products` | 1 | 8 | 0 | 0 | 占位 |
| 12 | `:feature:inventory` | 1 | 8 | 0 | 0 | 占位 |
| 13 | `:feature:planning` | 1 | 8 | 0 | 0 | 占位 |
| 14 | `:feature:procurement` | 1 | 8 | 0 | 0 | 占位 |
| 15 | `:feature:settings` | 1 | 8 | 0 | 0 | 占位（无备份/恢复入口） |

**观察**：

- 业务代码高度集中在 core（约 7,700 LOC），feature 层只有 378 LOC；这是“框架基线”的正常形态，但也意味着五项功能几乎全部要从 feature 层开始建。
- 7 个 feature 是 8 行占位文件；删除/重命名成本极低。
- `core:designsystem` 只有 72 行，不能称为设计系统；阶段 3 前必须补齐 token 与基础组件。
- `feature:ingredients` 是单包平铺（Screen/UiState/ViewModel/Module 四个文件），不符合任务书第 14 节的页面隔离要求。

---

## 3. 逐模块处置建议

| 模块 | 处置 | 理由 | 阶段 |
|---|---|---|---|
| `:app` | **保留，改造导航** | 组合根职责不变；导航需从 9 个目的地缩为 5 项业务 + 设置 | 1 |
| `:core:common` | **保留** | 与平台无关的契约稳定，测试齐全 | 0 |
| `:core:domain` | **保留并扩展** | 增加 Sauce/Product/Inventory 模型与端口；移除 planning 相关概念（如有）；保持纯 JVM | 2 |
| `:core:database` | **保留，冻结 v1** | 31 表可继续使用；不删表、不改 schema；休眠表只停代码入口 | 1/2 |
| `:core:data` | **保留并扩展** | 增加 Sauce/Product/Inventory Repository；补备份 checkpoint；保持不依赖 SAF | 2/4 |
| `:core:designsystem` | **保留，重点建设** | 当前仅 72 行；需 token、字体、间距、按钮、输入、卡片、状态组件 | 3 前 |
| `:core:platform` | **保留** | 扫码/SAF/权限/WorkManager 是平台能力，不应下沉到 feature | 0 |
| `:feature:ingredients` | **保留，重构目录** | 已有切片；补详情/别名/换算，按 `presentation/list|detail|editor` 拆分 | 3 |
| `:feature:sauces` | **保留，新建** | 五项功能之一；当前仅占位 | 3 |
| `:feature:recipes` | **保留，新建** | 五项功能之一；当前仅占位 | 3 |
| `:feature:products` | **保留，缩减/复用** | 只保留商品、品牌、包装规格、条码、食材映射、扫码 UI；不做供应商管理 | 1/5 |
| `:feature:inventory` | **保留，新建** | 库存总览/入出库/流水/需求/缺料/采购清单 | 4 |
| `:feature:planning` | **删除** | 新范围不做独立复杂规划；D-007 的快照语义另行落地 | 1 |
| `:feature:procurement` | **删除** | 缺料清单归入 Inventory；不做采购单/到货/供应商 | 1 |
| `:feature:settings` | **保留，接线** | 备份/恢复/诊断/关于；需要 domain 端口，不直接依赖 data | 4/6 |

### 3.1 目标模块集（13）

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
:feature:products      # 商品 + 包装 + 条码 + 扫码
:feature:inventory
:feature:settings
```

删除 `:feature:planning`、`:feature:procurement` 后，模块数 15 − 2 = **13**。

### 3.2 扫码 UI 的三种归属

| 方案 | 内容 | 优点 | 缺点 | 建议 |
|---|---|---|---|---|
| **A** | `feature:products` 拥有扫码 UI + 商品/包装/映射 | 改动最小；商品与扫码天然耦合；不新增模块 | 模块名 `products` 与用户功能名 `Scan` 不完全一致 | **推荐（阶段 1 先这样做）** |
| B | 新建 `feature:scan`，删除/并入 `feature:products` | 命名与五项功能一致 | 新模块 + 商品 CRUD 归属不清；构建/导航改造更大 | 备选 |
| C | 扫码归入 `feature:inventory` | 模块更少 | Inventory 过宽；相机权限/扫码 UI 与库存耦合 | 不推荐 |

**建议 A 的理由**：任务书明确“Products 缩减为扫码商品、包装规格及食材映射所需能力”，这正好是 A 的范围；扫码是“商品匹配 + 入库”的入口，放在 products 内可以保持 `product_id` 一条主线。若后续扫码页面膨胀（>15 页面或 >3000 行），再按 A03A 的拆分触发条件拆出 `feature:scan`。

---

## 4. 目标依赖规则

### 4.1 允许的边

```text
app -> feature:ingredients, feature:sauces, feature:recipes,
       feature:products, feature:inventory, feature:settings
app -> core:data, core:platform, core:designsystem

feature:* -> core:domain, core:designsystem, core:common

core:domain      -> core:common
core:database    -> core:common
core:data        -> core:domain, core:database, core:common
core:designsystem-> core:common
core:platform    -> core:domain, core:common
```

### 4.2 仍然禁止

```text
feature -> feature
feature -> core:data（实现）
feature -> core:database
core:domain -> Android / Room / Compose / Hilt / data / database / platform
core:database -> core:domain / core:data
core:data -> Android SAF / Uri（领域接口）
任意模块 -> app
```

### 4.3 feature 内部结构（强制）

```text
feature/<name>/src/main/java/com/culinary/feature/<name>/
  presentation/
    list/        Screen.kt, ViewModel.kt, UiState.kt
    detail/      ...
    editor/      ...
    components/  仅本 feature 复用
    navigation/  路由/入口
  di/            如需要
```

规则：

- 一个页面一个 `Screen + ViewModel + UiState`；
- 不允许所有页面塞进一个全局文件；
- 共用组件必须有明确共享范围；
- UI 不直接访问 DAO/Database；
- ViewModel 调 UseCase，不绕过 Domain。

`feature:ingredients` 现状是平铺 4 个文件，阶段 3 重构时按此结构拆分；不要为了“看起来整齐”一次性重写其他 feature。

---

## 5. 数据库表归属（31 表）

| 归属 | 表 | 数量 | 处置 |
|---|---|---|---|
| 食材 | `unit`、`ingredient`、`ingredient_alias`、`ingredient_unit_conversion` | 4 | 保留 |
| 酱汁 | `sauce`、`sauce_version`、`sauce_component` | 3 | 保留 |
| 菜谱 | `recipe`、`recipe_version`、`recipe_component` | 3 | 保留 |
| 商品/扫码 | `product`、`barcode`、`storage_location` | 3 | 保留；`supplier` 另行处置 |
| 库存 | `inventory_batch`、`inventory_ledger`、`inventory_reservation` | 3 | 保留 |
| 跨切面 | `image_asset`、`change_log`、`backup_record`、`app_metadata` | 4 | 保留 |
| 计划（休眠） | `production_plan`、`production_plan_item`、`production_plan_snapshot`、`material_requirement`、`material_requirement_source` | 5 | 冻结；不建 UI/Repository；`material_requirement*` 是否转为计算快照待定 |
| 采购（休眠） | `procurement_requirement`、`purchase_order`、`purchase_order_item`、`goods_receipt`、`goods_receipt_item` | 5 | 冻结；不建 UI/Repository |
| 供应商（休眠） | `supplier` | 1 | 冻结；`product.supplier_id` 保留可空，不做供应商管理 UI |

**合计**：4 + 3 + 3 + 3 + 3 + 4 + 5 + 5 + 1 = 31。

### 5.1 休眠表处理原则

1. **不删除**：任务书禁止未审计删除；`fallbackToDestructiveMigration` 禁止。
2. **不建入口**：不添加导航、Repository、UseCase、UI。
3. **不写数据**：除迁移/诊断外，不向休眠表写入。
4. **保留外键**：不破坏现有 schema 与外键完整性。
5. **后续迁移另立任务**：若确认永久不需要，新增 schema v2 迁移，先备份、再删表、补 MigrationTest；不在阶段 1 做。
6. **文档标记**：在 A06 数据字典/ER 图中标注 `DORMANT`。

---

## 6. 依赖检查脚本审查

### 6.1 现状

`scripts/check-module-deps.ps1`：

- 硬编码 15 个模块白名单；
- 只解析各模块 `build.gradle.kts` 的 `project(":...")`；
- 检查模块数量、模块集合、禁止 `core:model`、禁止依赖 `app`；
- 不检查源码 `import`；
- 不检查 UI→DAO、feature→feature、domain→Android、database→domain；
- 不检查循环依赖（只靠白名单方向）。

### 6.2 问题

- 删掉 planning/procurement 后脚本必然 FAIL；
- 即使 build.gradle 合规，源码仍可 `import` 其他 feature 的内部类；
- 无法阻止 Composable 直接 `import com.culinary.core.database.dao.*`；
- 43 条边是“声明边”，不是“实际依赖边”。

### 6.3 重写要求

| 要求 | 实现 |
|---|---|
| 模块集来自配置，不硬编码 | 读取 `settings.gradle.kts` + 允许规则表 |
| 检查声明依赖 | `build.gradle.kts` 的 `project(...)` |
| 检查源码 import | 扫描 `src/main/**/*.kt` 的 `import com.culinary.feature.*` / `core.database` 等 |
| 检查分层禁忌 | `core:domain` 不得出现 `android.*`/`androidx.room`/`androidx.compose`；`core:database` 不得 import `core.domain`；`feature` 不得 import `core.data`/`core.database` |
| 检查循环 | 构建图后拓扑排序 |
| 输出可读 | 模块数、边数、违规文件:行号 |
| CI 可跑 | 退出码非 0 即失败 |

**建议**：保留 PowerShell 入口（Windows 本地），内部规则配置化；同时提供 Gradle 任务 `checkModuleDeps`，CI 用 Gradle 任务，避免脚本与构建脱节。

---

## 7. 删除 Planning / Procurement 的影响

| 影响面 | 内容 | 处理 |
|---|---|---|
| `settings.gradle.kts` | 去掉 2 个 include | 阶段 1 |
| 模块目录 | 删除 2 个 8 行占位文件 | 阶段 1（删除前确认无引用） |
| `app` 导航 | 去掉 2 个目的地 | 阶段 1 |
| 依赖检查 | 白名单改 13 | 阶段 1 |
| Domain/Data | 当前无 planning/procurement Repository/UseCase | 无需删除业务代码 |
| Database | 表/DAO 保留，标记 DORMANT | 阶段 1/2 |
| 测试 | 这 2 个模块无测试 | 无测试损失 |
| 文档 | D-004、README、A03A、P01 需更新/标注 | 阶段 0/1 |
| 历史 | G2 R11 的“15 模块”仍是历史事实，不改写 | 保留 |

---

## 8. 模块建设顺序建议（不构成排期承诺）

1. **阶段 1**：删 planning/procurement；products 缩减；导航改 5 项；依赖检查重写；README/P01 修正。
2. **阶段 2**：domain 补 Sauce/Product/Inventory 模型与端口；菜谱版本/缩放/冻结规则；库存计算规则。
3. **阶段 3**：design system 基础组件；ingredients 目录重构；sauces/recipes 页面。
4. **阶段 4**：inventory 页面与闭环；settings 备份/恢复入口。
5. **阶段 5**：products/扫码 UI + 真机验证。
6. **阶段 6**：整体联调、E2E、发布。

---

## 9. 待签字事项

| 决策 | 内容 |
|---|---|
| MB-D-003 | 目标 13 模块（删除 planning/procurement，products 缩减） |
| MB-D-004 | 扫码 UI 归属（建议 A：feature:products） |
| MB-D-005 | 11 张休眠表：冻结 v1、不删表、后续迁移另立任务 |
| MB-D-013 | 子配方版本固定时点 |
| MB-D-017 | 计划快照的替代方案 |

---

## 10. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 0.1.0 | 2026-11-04 | 完成 15 模块盘点、13 模块建议、数据库归属与依赖检查审查 | 接管工程组 |
