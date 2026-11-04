# MB-SC-07 11 张休眠表未触碰证明

**文档编号**：MB-SC-07
**版本**：`1.0.0`
**状态**：`Review`
**日期**：2026-11-04
**决策依据**：MB-D-005（ACCEPTED）

---

## 1. 休眠表清单（11）

| # | 表 | 归属 |
|---:|---|---|
| 1 | `supplier` | 供应商 |
| 2 | `production_plan` | 计划 |
| 3 | `production_plan_item` | 计划 |
| 4 | `production_plan_snapshot` | 计划 |
| 5 | `material_requirement` | 计划/需求 |
| 6 | `material_requirement_source` | 计划/需求 |
| 7 | `procurement_requirement` | 采购 |
| 8 | `purchase_order` | 采购 |
| 9 | `purchase_order_item` | 采购 |
| 10 | `goods_receipt` | 采购/收货 |
| 11 | `goods_receipt_item` | 采购/收货 |

---

## 2. 结构未触碰证明

测试：`CulinaryMigrationV1ToV2Test.migrates real v1 data and defaults servings to one`

方法：

1. 从 `1.json` 建 v1 库；
2. 查询 `sqlite_master`，记录 11 张表的 `sql` 文本；
3. 执行 `MIGRATION_1_2`，打开 v2 Room；
4. 再次查询同样 11 张表的 `sql`；
5. 断言两个 `Map<String, String>` **完全相等**。

结果：**通过**。11 张表的 `CREATE TABLE` 文本、列顺序、类型、外键、`ON DELETE` 行为均未变化。

数据面：本任务没有向 11 张表插入、更新或删除任何业务数据；迁移 SQL 不包含对这 11 张表的
`INSERT/UPDATE/DELETE/ALTER/CREATE/DROP TABLE`。

---

## 3. 部分索引的临时移除与重建

v1 运行时由 `CulinarySchemaExtras` 创建 9 个不在 Room 导出 schema 中的部分索引。
Room 在迁移后按 `2.json` 逐表比对索引集合，若保留这些“额外索引”会使 v1→v2 校验失败。

因此 `MIGRATION_1_2` 在 DDL 前执行：

```sql
DROP INDEX IF EXISTS ux_ingredient_normalized_name;
DROP INDEX IF EXISTS ux_sauce_normalized_name;
DROP INDEX IF EXISTS ux_recipe_normalized_name;
DROP INDEX IF EXISTS ux_sauce_current;
DROP INDEX IF EXISTS ux_recipe_current;
DROP INDEX IF EXISTS ux_supplier_normalized_name;
DROP INDEX IF EXISTS ux_barcode_primary;
DROP INDEX IF EXISTS ux_image_primary;
DROP INDEX IF EXISTS ux_reservation_active;
```

`CulinaryDatabaseCallback.onOpen` 随即按 v1 的原始定义重建全部 9 个索引。
迁移测试断言 9 个索引全部存在，且 11 张表的 `sqlite_master.sql` 不变。

边界说明：

- 这是 Room 迁移校验要求的**索引级、瞬时**操作；
- 不删除/不重建/不改列/不改数据/不改表 SQL；
- 最终状态下索引名称、列、唯一性、`WHERE` 条件与 v1 完全一致；
- 没有把休眠表转为业务表，也没有新增对它们的 Repository/UI/业务引用。

---

## 4. 新业务引用扫描

### 4.1 本任务新增代码

- `git diff a698c863d5fe81cf8dca1cfdeef4bedff16ffa08..HEAD` 中，`app`、`feature`、
  `core:data` 主代码、`core:domain` 主代码**没有新增**对 11 张表的引用；
- 新增的 `DORMANT` 表名列表只出现在 `core/database/src/test` 与 `androidTest` 的迁移/
  回滚验证中，属于允许的结构验证范围；
- 新增的计算记录实体、DAO、Repository、Mapper 不引用任何休眠表。

### 4.2 专项测试

`PurchaseListTest` 中已有“扫描业务源码、排除注释后不得包含 `purchase_order`”的断言：

```powershell
.\gradlew.bat :core:domain:test `
  --tests "com.culinary.core.domain.rule.inventory.PurchaseListTest" `
  --console=plain --no-daemon
```

结果：`BUILD SUCCESSFUL`。

### 4.3 旧扫描脚本的 3 处文本命中

执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-dormant-tables.ps1
```

结果：`Result: FAIL (3 reference(s) outside allowed roots)`，命中为：

| 文件 | 性质 |
|---|---|
| `core/domain/.../PurchaseList.kt:8` | 既有注释：“不落 `purchase_order*` 表” |
| `core/domain/.../PurchaseListBuilder.kt:15` | 既有注释：“不落 `purchase_order*` 表” |
| `core/domain/.../PurchaseListTest.kt:114` | 既有测试中的字符串断言 |

这三处均为 Stage 2 既有文本，不是本任务新增，也不构成业务引用；扫描脚本按“整行文本”
匹配，未区分注释/字符串。为避免改写 Stage 2 业务文件，本任务不修改这三处，也不把
脚本的 FAIL 伪报为 PASS。专项测试 4.2 排除了注释并验证业务代码无引用。

---

## 5. 结论

- 11 张表未删除、未迁移、未改列、未改数据；
- 表 SQL 迁移前后字节级一致；
- 本任务新增业务代码 0 处引用；
- 9 个部分索引的临时移除是 Room 迁移校验技术操作，最终原样重建；
- 旧扫描脚本的 3 处 FAIL 是 Stage 2 既有注释/测试文本的误报，已如实记录。

---

## 6. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0.0 | 2026-11-04 | 建立休眠表结构、索引、引用扫描与旧脚本误报说明 | 接管工程组 |
