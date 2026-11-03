# G2 阶段 3（数据库 v1）完成报告

**文档编号**：R13
**版本**：`1.0.0`
**状态**：`Review`（待项目负责人验收）
**日期**：2026-11-04
**实施依据**：[R01 0.2.2](R01-FRAMEWORK-SEQUENCE.md) §6、A06 0.1.0、E02、D-005、D-007、D-009
**回档点**：`baseline/g2-stage2-20261104`

---

## 1. 任务目标

按 A06 建立 Room **schema v1（31 表）**，并交付：

1. Entity / DAO / TypeConverter / 索引与约束；
2. 外键开启、WAL、busy timeout；
3. 单位种子数据（15 条，幂等）；
4. 迁移框架与 `exportSchema` 导出；
5. 约束与触发器测试；
6. `MigrationTestHelper` 可用的前置 schema。

## 2. 交付内容

### 2.1 31 张表（与 A06 完全对应）

| 分组 | 表 |
|---|---|
| 单位与食材（4） | `unit`、`ingredient`、`ingredient_alias`、`ingredient_unit_conversion` |
| 酱汁与菜谱（6） | `sauce`、`sauce_version`、`sauce_component`、`recipe`、`recipe_version`、`recipe_component` |
| 商品与位置（4） | `supplier`、`product`、`barcode`、`storage_location` |
| 库存与预留（3） | `inventory_batch`、`inventory_ledger`、`inventory_reservation` |
| 计划（5） | `production_plan`、`production_plan_item`、`production_plan_snapshot`、`material_requirement`、`material_requirement_source` |
| 采购与收货（5） | `procurement_requirement`、`purchase_order`、`purchase_order_item`、`goods_receipt`、`goods_receipt_item` |
| 横切（4） | `image_asset`、`change_log`、`backup_record`、`app_metadata` |

合计 **31 张**（4 + 6 + 4 + 3 + 5 + 5 + 4）。

### 2.2 打开配置（E02）

- `PRAGMA foreign_keys = ON`（每次连接）；
- `PRAGMA busy_timeout = 5000`；
- WAL（`WRITE_AHEAD_LOGGING`，文件库实测 `journal_mode = wal`）；
- `MigrationRegistry`（`CulinaryMigrations.ALL`），**无** `fallbackToDestructiveMigration`。

### 2.3 Room 无法表达、用原生 DDL 补齐的约束

| 类型 | 内容 |
|---|---|
| 部分唯一索引 | 在用名称唯一（ingredient/sauce/recipe/supplier）、每聚合一个当前版本、每商品一个主条码、每需求每批次一个 ACTIVE 预留、每归属一张 PRIMARY 图 |
| CHECK 触发器 | 各表的枚举取值、数量 > 0 / ≥ 0、`reserved ≤ on_hand`、三选一组成目标、已发布版本内容不可改、`cancelled ≤ ordered` 等 |
| 只追加触发器 | `inventory_ledger` UPDATE/DELETE 拒绝；`change_log` UPDATE/DELETE 拒绝；`production_plan_snapshot` UPDATE 拒绝 |

**已知取舍**：`production_plan_snapshot` 的 **DELETE** 未禁用——计划删除需要级联清理快照，而 SQLite 触发器无法区分级联与直接删除。DAO 不暴露删除方法，只有原始 SQL 才可能直接删；payload 的 UPDATE 仍被拒绝。

### 2.4 种子数据

15 个单位（`mg/g/kg/ml/l/tsp/tbsp/cup/fl_oz/piece/bunch/clove/slice/pack/portion`），固定 UUID v4 形态 ID，`INSERT OR IGNORE`，重复打开不产生重复行。

### 2.5 模块边界

- `core:database` **不依赖** `core:domain`；枚举在数据库层独立声明（`*Db`），由阶段 4 的 Mapper 转换；
- DAO 按实体拆分（`SauceVersionDao` 等），符合 E01 的 `<Entity>Dao` 命名；
- 转换器按域拆为 `CoreConverters` / `InventoryConverters` / `PlanConverters` / `ProcurementConverters` / `CrossCuttingConverters`。

## 3. 测试要求与实际结果

| # | 要求（R01 §6.2） | 结果 |
|---|---|---|
| 1 | 新安装能建库，`user_version=1` | **PASS** |
| 2 | 外键、唯一、CHECK 生效 | **PASS**（每个约束族都有用例） |
| 3 | 流水不可 UPDATE/DELETE | **PASS**（`inventory_ledger`） |
| 4 | 库存余额不能为负；预留 ≤ 在手 | **PASS** |
| 5 | schema JSON 已提交 | **PASS**（31 表，`1.json`） |
| 6 | 迁移框架可用 | **部分**：`CulinaryMigrations` + 导出 schema 已就绪；`MigrationTestHelper` 的插桩运行推迟到阶段 9（见 §4） |

**测试统计：74 例 / 0 失败**

| 测试类 | 数量 | 覆盖 |
|---|---:|---|
| `DatabaseFoundationTest` | 8 | FK、busy timeout、user_version、种子幂等、WAL、unit CHECK |
| `UnitDaoTest` | 4 | 种子顺序、转换器往返、唯一 code、按 ID 查询 |
| `IngredientConstraintsTest` | 11 | 部分唯一（软删除后可复用）、FK RESTRICT、别名/换算 CASCADE、三选一 |
| `KnowledgeConstraintsTest` | 11 | 版本号唯一、当前版本唯一、组成行唯一、三选一、发布后不可改、级联 |
| `ExecutionConstraintsTest` | 10 | 商品数量、FK RESTRICT/SET_NULL、条码唯一、批次约束、流水唯一与只追加、位置树 |
| `PlanSnapshotConstraintsTest` | 9 | 快照版本唯一、payload 不可改、需求键唯一、ACTIVE 预留唯一、级联/限制 |
| `ProcurementConstraintsTest` | 10 | PO/收货编号唯一、数量约束、级联与 RESTRICT |
| `CrossCuttingConstraintsTest` | 8 | 图片路径唯一、主图唯一、日志只追加、元数据 upsert、备份校验 |
| `SchemaExportTest` | 3 | v1、31 表、迁移表为空 |

全工程门禁（本轮实测）：

```text
.\gradlew.bat test ktlintCheck detekt :app:assembleDebug
BUILD SUCCESSFUL
```

## 4. 失败与偏差

| 项 | 说明 | 处理 |
|---|---|---|
| 快照只追加 vs 级联删除 | SQLite 触发器无法区分级联与直接删除 | 仅禁用 UPDATE，删除交给计划级联；DAO 无删除方法 |
| `MigrationTestHelper` | 需要插桩上下文与 `androidTest` assets；本阶段只有 Robolectric/JVM 测试 | 导出 schema 与迁移注册已就绪；插桩迁移测试排入阶段 9 CI，**这是阶段 3 唯一未完全满足的退出条件** |
| detekt `TooManyFunctions` | `@Database` 类随 DAO 数量增长（31） | 阈值调至 35 并注明 Room 结构原因；转换器按域拆分而非继续调阈值 |
| detekt `LongMethod` | 测试夹具过长 | 拆为 `insertKnowledgeWorld` / `insertExecutionWorld` / `insertPlanWorld` |
| 组成类型枚举 | `ComponentTarget`（领域）暂不含酱汁 | 数据库层已完整支持 `SAUCE`（A06 要求），领域模型随酱汁功能补齐 |

## 5. Git

| 项 | 值 |
|---|---|
| 阶段 1 基线 | `baseline/g2-stage1-20261104` |
| 阶段 2 基线 | `baseline/g2-stage2-20261104` |
| 阶段 3 提交 | `feat(db): …`（5 个提交：基础 / 食材 / 知识 / 目录库存 / 计划 / 采购 / 横切） |
| 阶段 3 基线标签 | `baseline/g2-stage3-20261104` |

## 6. 结论

- A06 的 **31 张表全部落地**，schema v1 已导出并纳入版本管理；
- 外键、WAL、busy timeout、种子、CHECK、部分唯一索引、只追加触发器均有实测；
- 74 例数据库测试全绿，全工程质量门禁通过；
- **唯一未完全满足项**：`MigrationTestHelper` 的插桩运行推迟到阶段 9。

**请项目负责人验收阶段 3。** 验收通过后进入阶段 4（数据层：Repository 实现、Mapper、TransactionRunner、Hilt 绑定）。
D-005（主键）与 D-007（快照）已在阶段 3 期间接受并记录；D-013/D-015 等仍为 `PROPOSED`，将在对应阶段前提交。

## 7. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0.0 | 2026-11-04 | 记录 31 表、约束/触发器、74 例测试、偏差与基线 | 工程组 |
