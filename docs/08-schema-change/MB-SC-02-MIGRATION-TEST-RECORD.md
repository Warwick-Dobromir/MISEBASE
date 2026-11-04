# MB-SC-02 迁移测试记录

**文档编号**：MB-SC-02
**版本**：`1.0.0`
**状态**：`Review`
**日期**：2026-11-04
**对象**：`MIGRATION_1_2`（Schema v1 → v2）
**环境**：Windows 11 / Gradle 8.14.3 / Launcher JVM 21.0.8 / Room 2.8.5 / Robolectric 4.17

---

## 1. 测试策略

| 层 | 测试 | 是否执行 | 说明 |
|---|---|---|---|
| JVM（主要证据） | `core:database` Robolectric | 是 | 从导出的 `1.json` 逐表建 v1 库，写入 v1 `identity_hash`，由真实 Room 执行 `MIGRATION_1_2` 并用 `2.json` 校验 |
| 官方 MigrationTestHelper | `CulinaryDatabaseMigrationTest`（androidTest） | 编译，未执行 | 当前 `adb devices` 为空；无设备/模拟器，不能伪造执行结果 |
| 完整性 | `PRAGMA foreign_key_check` / `quick_check` | 是 | 在空库、真实数据、回滚重开三处断言 |
| 休眠表 | `sqlite_master.sql` 前后比较 | 是 | 11 张表逐表字节比较 |
| 部分索引 | `sqlite_master` 名称检查 | 是 | 9 个 v1 运行时部分索引在 `onOpen` 后全部重建 |

JVM 测试没有手写 31 张表的 DDL；它执行 `schemas/.../1.json` 中的 Room `createSql` 与
`identityHash`，因此走的是与真实升级相同的 Room 迁移入口。

---

## 2. 执行命令与结果

```powershell
.\gradlew.bat test --console=plain --no-daemon
```

结果：`BUILD SUCCESSFUL`；JVM 唯一测试用例 **274**，失败 **0**，错误 **0**，跳过 **0**。
（`test` 会执行 debug/release 两个变体；274 为去重后的唯一用例数。）

专项命令：

```powershell
.\gradlew.bat :core:database:testDebugUnitTest `
  --tests "com.culinary.core.database.CulinaryMigrationV1ToV2Test" `
  --tests "com.culinary.core.database.SchemaExportTest" `
  --tests "com.culinary.core.database.DatabaseFoundationTest" `
  --console=plain --no-daemon
```

结果：`BUILD SUCCESSFUL`。

```powershell
.\gradlew.bat :core:database:compileDebugAndroidTestKotlin --console=plain --no-daemon
```

结果：`BUILD SUCCESSFUL`（仅编译；无设备，未运行）。

---

## 3. 用例与断言

### 3.1 空库迁移

用例：`CulinaryMigrationV1ToV2Test.migrates an empty v1 database to v2`

- v1：31 张表，无业务数据；
- 迁移后 `user_version = 2`；
- 表数 **33**；
- `recipe_version.servings`：`INTEGER`、`NOT NULL`、默认值 `'1'`；
- `calculation_record` 存在且 `count = 0`；
- `foreign_key_check` 为空；
- `quick_check = ok`。

### 3.2 真实旧数据迁移

用例：`...migrates real v1 data and defaults servings to one`

v1 数据：单位、食材、菜谱、已发布菜谱版本、`is_current=1`，并模拟 v1 `onOpen`
创建的部分索引（`ux_ingredient_normalized_name`、`ux_recipe_current`）。

断言：

- `recipe_version` 行仍为 1 行，`recipe_id` / `version_no` / `yield_qty` 不变；
- 旧行 `servings = 1`；
- `recipe`、`ingredient` 行数保持 1；
- 9 个部分索引在迁移后全部存在（含 `ux_supplier_normalized_name`）；
- 11 张 `DORMANT` 表的 `sqlite_master.sql` 与迁移前**完全相等**；
- `foreign_key_check` 为空，`quick_check = ok`。

### 3.3 迁移后计算记录写入与不可变

用例：`...migrated schema accepts confirmed calculation records and enforces immutability`

- 通过 `CalculationRecordDao.insertConfirmed` 写入 `CONFIRMED` 记录（份数 2.5）与 1 行需求；
- 读回 `servings = 2.5`，行数 1；
- 直接 SQL `UPDATE calculation_record` 被触发器拒绝；
- 直接 SQL `UPDATE calculation_record_line` 被拒绝；
- `recipe_version.servings = 0` 被拒绝；
- 迁移后完整性检查仍通过。

### 3.4 Schema 导出

`SchemaExportTest`：

- `1.json` 字节哈希锁定为 `a305afd334c8069c8342ed0664944d8817f24bc1c06f251e1df4da4bfd070b7b`；
- `1.json` 仍为 31 表、version 1；
- `2.json` 为 33 表、version 2；
- `CulinaryMigrations.ALL` 恰好 1 项，`startVersion=1`、`endVersion=2`。

`DatabaseFoundationTest` 的 schema 版本断言已从 1 更新为 2。

---

## 4. 官方 MigrationTestHelper 用例（已编译，未执行）

`core/database/src/androidTest/.../CulinaryDatabaseMigrationTest.kt` 包含：

| 用例 | 内容 |
|---|---|
| `createsV1AndMatchesTheExportedSchema` | v1 基线 |
| `migratesEmptyV1ToV2AndValidatesSchema` | `runMigrationsAndValidate(..., 2, true, MIGRATION_1_2)` |
| `migratesRealV1DataAndKeepsDormantTablesUntouched` | 真实数据 + 休眠表 SQL 比较 |

**未执行原因**：`adb devices -l` 无设备。报告不把“已编译”写成“已通过”。

---

## 5. 结论

- v1 → v2 迁移在空库与真实旧数据上通过；
- `servings` 默认值与约束有效；
- 计算记录表可写、可读、确认后不可变；
- 迁移后外键与快速完整性检查通过；
- 11 张休眠表的表 SQL 未变化；
- 官方 androidTest 尚未在设备上执行，作为审查保留项。

---

## 6. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0.0 | 2026-11-04 | 记录 v1→v2 JVM 迁移、完整性、休眠表与 androidTest 编译结果 | 接管工程组 |
