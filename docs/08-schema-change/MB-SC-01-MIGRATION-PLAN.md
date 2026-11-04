# MB-SC-01 Schema v1 → v2 迁移方案

**文档编号**：MB-SC-01
**版本**：`1.0.0`
**状态**：`Review`
**日期**：2026-11-04
**授权**：[MB-SC-00](MB-SC-00-AUTHORIZATION.md)（独立 Schema 变更任务）
**执行边界**：只做 Schema 变更、迁移、测试、备份恢复验证与回滚验证；不进入 Stage 3，不开发页面。

---

## 1. 变更声明

| 项 | 内容 |
|---|---|
| 迁移编号 | `MIGRATION_1_2`（`CulinaryMigrations.MIGRATION_1_2`） |
| 起点版本 | Schema v1，31 表，其中 11 张 `DORMANT` |
| 目标版本 | Schema v2，33 表 |
| 关联决策 | MB-D-017（计算记录，ACCEPTED）；MB-D-013/014/015/016（ACCEPTED，仅作为语义约束） |
| 变更类型 | 加列、加表、加索引、加触发器、历史数据默认值回填 |
| 是否涉及休眠表 | **否**。11 张表不删除、不迁移、不改结构、不写入 |
| 回档点 | 起点提交 `a698c863d5fe81cf8dca1cfdeef4bedff16ffa08`；迁移前 v1 备份；检查点标签 `checkpoint/misebase-schema-change-20261104` |
| 合并变更 | ① 计算记录持久化；② `recipe_version.servings`。两项在同一个 v2 迁移中实施 |

**不包含**：计算记录业务页面、Use Case 页面接入、导航、视觉定稿、扫码 UI、新模块、备份/恢复实现修改。

---

## 2. 起点事实

| 项 | 值 |
|---|---|
| 分支 / HEAD | `misebase/main` / `a698c863d5fe81cf8dca1cfdeef4bedff16ffa08` |
| Stage 3 起点标签 | `42b06b0d4e36828f8578732cc5ae70238fdfdfa5`（HEAD 的祖先） |
| 模块数 | 13 |
| `1.json` | 31 表，SHA-256 `A305AFD334C8069C8342ED0664944D8817F24BC1C06F251E1DF4DA4BFD070B7B` |
| 迁移注册表 | 变更前为空 |
| 决策状态 | 12/18 ACCEPTED；6 项 PROPOSED（不得当作已批准） |

---

## 3. 目标 Schema

### 3.1 `recipe_version` 加列

```sql
ALTER TABLE `recipe_version`
ADD COLUMN `servings` INTEGER NOT NULL DEFAULT 1;
```

| 项 | 值 |
|---|---|
| 列名 | `servings` |
| 类型 | `INTEGER` |
| 空值 | `NOT NULL` |
| 默认值 | `1` |
| 业务约束 | `servings > 0`；由 `trg_recipe_version_check_*` 触发器实现 |
| 历史数据 | 不回填、不改写；SQLite 对既有行返回默认值 `1`，语义为“整批 = 1 份” |
| 已发布版本 | `trg_recipe_version_published_immutable` 的内容变更集合加入 `servings`，发布后不可修改 |

### 3.2 `calculation_record`

| 列 | 类型 | 空 | 说明 |
|---|---|---|---|
| `id` | TEXT | N | 主键，UUID v4 文本 |
| `recipe_version_id` | TEXT | N | 外键 → `recipe_version(id)`，`ON DELETE RESTRICT` |
| `servings` | REAL | N | 计算时使用的份数；允许半份，必须 > 0 |
| `engine_version` | TEXT | N | 计算引擎版本 |
| `payload_hash` | TEXT | N | 小写 SHA-256，64 位；唯一 |
| `status` | TEXT | N | `PREVIEW` / `CONFIRMED` |
| `created_at` | INTEGER | N | 创建时间，UTC epoch millis |
| `confirmed_at` | INTEGER | Y | 确认时间；`CONFIRMED` 时必须非空，`PREVIEW` 时必须为空 |

索引：

- `index_calculation_record_recipe_version_id`
- `index_calculation_record_payload_hash`（UNIQUE）
- `index_calculation_record_recipe_version_id_created_at`
- `index_calculation_record_status`

### 3.3 `calculation_record_line`

| 列 | 类型 | 空 | 说明 |
|---|---|---|---|
| `id` | TEXT | N | 主键，UUID v4 文本 |
| `calculation_record_id` | TEXT | N | 外键 → `calculation_record(id)`，`ON DELETE CASCADE` |
| `line_no` | INTEGER | N | 行号，从 1 开始 |
| `ingredient_id` | TEXT | N | 外键 → `ingredient(id)`，`ON DELETE RESTRICT` |
| `quantity` | REAL | N | 数量，必须 > 0 |
| `unit_id` | TEXT | N | 外键 → `unit(id)`，`ON DELETE RESTRICT` |
| `is_optional` | INTEGER | N | 0/1 |

索引：

- `index_calculation_record_line_calculation_record_id`
- `index_calculation_record_line_ingredient_id`
- `index_calculation_record_line_unit_id`
- `index_calculation_record_line_calculation_record_id_line_no`（UNIQUE）
- `index_calculation_record_line_calculation_record_id_ingredient_id_unit_id`（UNIQUE）

### 3.4 触发器与约束

| 触发器 | 作用 |
|---|---|
| `trg_calculation_record_check_insert/update` | 状态、份数、哈希、时间字段、确认时间一致性 |
| `trg_calculation_record_confirmed_immutable` | `OLD.status = CONFIRMED` 时拒绝任何 UPDATE |
| `trg_calculation_record_confirmed_no_delete` | 拒绝删除已确认记录 |
| `trg_calculation_record_line_check_insert/update` | 行号、数量、可选标记 |
| `trg_calculation_record_line_no_insert/update/delete` | 已确认记录的行不可新增、修改、删除 |
| `trg_recipe_version_check_insert/update` | 在 v1 版本约束上追加 `servings >= 1` |
| `trg_recipe_version_published_immutable` | 已发布版本内容不可变，内容集合包含 `servings` |

`PREVIEW → CONFIRMED` 的首次确认是唯一允许的确认写入；确认后任何字段变化都被拒绝。

---

## 4. 对既有 31 表的影响

| 类别 | 数量 | 处理 |
|---|---:|---|
| 完全不变的表 | 30 | 不重建、不改列、不改索引、不改数据 |
| 仅加列的表 | 1（`recipe_version`） | `ALTER TABLE ADD COLUMN`，默认 1，不重写历史行 |
| 删除/重命名的表 | 0 | 禁止 |
| 结构性改造的休眠表 | 0 | 禁止 |

11 张 `DORMANT` 表：`supplier`、`production_plan`、`production_plan_item`、
`production_plan_snapshot`、`material_requirement`、`material_requirement_source`、
`procurement_requirement`、`purchase_order`、`purchase_order_item`、`goods_receipt`、
`goods_receipt_item`。

证明方法：迁移测试在 v1 建库后记录 11 张表的 `sqlite_master.sql`，迁移到 v2 后逐表比较，
要求字节级完全一致；同时用引用扫描证明新业务代码 0 处引用。

---

## 5. 迁移算法

```text
BEGIN TRANSACTION
1. DROP 9 个 v1 运行时部分索引（定义不变，迁移校验后由 onOpen 重建）
2. ALTER TABLE recipe_version ADD COLUMN servings INTEGER NOT NULL DEFAULT 1
3. CREATE TABLE calculation_record
4. CREATE TABLE calculation_record_line
5. CREATE 9 个新索引（含 3 个唯一索引）
COMMIT
```

说明：

1. **部分索引先删后建**：Room 迁移后按 `2.json` 逐表比对索引集合；v1 由
   `CulinarySchemaExtras` 额外创建的 9 个部分索引不在 Room 导出 schema 中，若不先移除，
   v1→v2 校验会因“多余索引”失败。迁移删除后，`CulinaryDatabaseCallback.onOpen`
   按原定义重建，索引名称、列、`WHERE` 条件与 v1 完全一致。
2. **不加 destructive fallback**：`DatabaseFactory` 只注册 `CulinaryMigrations.ALL`；
   无 `fallbackToDestructiveMigration`。
3. **不原地降级**：只登记 `1 → 2`，不登记 `2 → 1`。回滚仅支持“恢复迁移前 v1 备份 +
   使用 v1 构建”，详见 [MB-SC-04](MB-SC-04-ROLLBACK-VERIFICATION.md)。
4. **迁移后自动校验**：Room 用 `2.json` 校验表、列、外键、索引；应用 `onOpen` 重放
   触发器与部分索引；测试额外执行 `foreign_key_check` 与 `quick_check`。
5. **备份边界**：MB-D-007 仍为 `PROPOSED`，本任务不修改任何备份/恢复实现；
   迁移测试只调用既有备份接口生成/校验/恢复 v2 归档。

---

## 6. 兼容性与回滚

| 场景 | 行为 |
|---|---|
| v2 App + v1 DB | 执行 `MIGRATION_1_2`，历史 `servings` 读为 1 |
| v2 App + v2 DB | 正常打开 |
| v1 App + v1 DB | 正常打开（不涉及本任务代码） |
| v1 App + v2 DB | 旧版本 `user_version=1 < 2` 时会拒绝；不得删除/重建数据库 |
| v2 备份 → v2 恢复 | 允许；恢复后数据完整 |
| v1 备份 → v2 恢复 | 允许；恢复后由 v2 执行 v1→v2 |
| 原地降级 v2 → v1 | **不支持**；只能恢复迁移前 v1 备份并使用 v1 构建 |

回滚验证必须同时满足：

- 迁移前 v1 备份可独立打开，`user_version=1`，无 v2 表；
- 备份写回数据库位置后，v2 重新迁移成功，数据可读，无半迁移状态；
- 迁移注册表不含 `2 → 1`，报告不得声称支持原地降级。

---

## 7. 测试矩阵

| 场景 | 测试 | 结果记录 |
|---|---|---|
| 空 v1 库 → v2 | `CulinaryMigrationV1ToV2Test.migrates an empty v1 database to v2` | [MB-SC-02](MB-SC-02-MIGRATION-TEST-RECORD.md) |
| 真实旧数据 → v2，`servings=1` | `...migrates real v1 data and defaults servings to one` | 同上 |
| 迁移后计算记录可写、确认后不可变 | `...migrated schema accepts confirmed calculation records and enforces immutability` | 同上 |
| 11 张休眠表结构不变 | 迁移前后 `sqlite_master.sql` 比较 | 同上 |
| `foreign_key_check` / `quick_check` | 迁移测试内断言 | 同上 |
| 官方 `MigrationTestHelper` | `CulinaryDatabaseMigrationTest`（androidTest） | 已编译；无设备，未执行 |
| v2 备份 → v2 恢复 | `BackupRestoreV2Test` | [MB-SC-03](MB-SC-03-BACKUP-RESTORE-VERIFICATION.md) |
| 回滚/恢复路径 | `CulinaryRollbackV1Test` | [MB-SC-04](MB-SC-04-ROLLBACK-VERIFICATION.md) |

---

## 8. 风险与缓解

| 风险 | 缓解 |
|---|---|
| 部分索引导致 Room 迁移校验失败 | 迁移先删、`onOpen` 重建；JVM 测试用带部分索引的 v1 库验证 |
| 历史 `servings` 语义被改写 | 只加默认值 1，不回填、不重写；测试断言旧行读为 1 |
| 已发布版本被修改 | 发布不可变触发器加入 `servings`；确认后计算记录触发器拒绝 UPDATE/DELETE |
| 重复确认产生重复记录 | 唯一 `payload_hash` + 同 ID 冲突 + Repository 契约测试 |
| 备份实现被误改 | 本任务 `core/data/backup` 实现文件 0 变更；测试只调用既有接口 |
| 无设备无法执行 androidTest | 以 JVM Robolectric 迁移测试为主要证据；androidTest 已编译，报告明确未运行 |
| 旧版扫描脚本把注释/测试中的 `purchase_order*` 当引用 | 3 处为 Stage 2 既有文本；本任务新增业务引用为 0，专项测试通过 |

---

## 9. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0.0 | 2026-11-04 | 建立 v1→v2 迁移方案：表、列、约束、索引、外键、影响范围、回滚与测试矩阵 | 接管工程组 |
