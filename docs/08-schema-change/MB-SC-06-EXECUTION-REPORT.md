# MB-SC-06 Schema 变更执行报告

**文档编号**：MB-SC-06
**版本**：`2.0.0`
**状态**：`CLOSED`
**日期**：2026-11-04
**授权**：[MB-SC-00](MB-SC-00-AUTHORIZATION.md)
**分支 / 基线**：`misebase/main`，起点 `a698c863d5fe81cf8dca1cfdeef4bedff16ffa08`
**报告正文提交**：`6d0dbac759a62bc8bfef6a78aebf9f18feb00950`
**关闭记录**：[MB-SC-CLOSE-001](MB-SC-CLOSE-001-SCHEMA-CHANGE-CLOSURE.md)
**关闭标签**：`checkpoint/misebase-schema-change-closed-20261104`

---

## 1. 执行摘要

本任务按 MB-SC-00 授权，在 Schema v1（31 表）基础上完成一次**合并迁移**到 Schema v2（33 表）：

1. 新增计算记录持久化结构：`calculation_record` 与 `calculation_record_line`；
2. `recipe_version` 增加 `servings INTEGER NOT NULL DEFAULT 1`，历史行语义保持“整批 = 1 份”；
3. 新增 `MIGRATION_1_2`，`exportSchema = true`，`1.json` 字节不变，新增 `2.json`；
4. `CalculationRecordRepository` 从“契约 + fake”升级为“契约 + Room 实现”；
5. 完成 JVM 迁移、完整性、Repository 契约、v2 备份恢复、回滚路径验证；
6. 未开发计算记录 UI/Use Case 页面，未进入 Stage 3，未修改备份/恢复实现，未新建模块。

结果：`test` **274 例 / 0 失败**；`assembleDebug` 成功；`ktlintCheck`、`detekt` 通过。
审查结论：**有条件通过**；本报告状态 **CLOSED**。保留项见
[MB-SC-CLOSE-001 §5](MB-SC-CLOSE-001-SCHEMA-CHANGE-CLOSURE.md)。关闭不等于 Stage 3 授权，
**不自动进入 Stage 3**。

---

## 2. 启动前置检查与决策状态

### 2.1 前置检查

| # | 检查 | 结果 |
|---:|---|---|
| 1 | `git status --porcelain` 为空；记录 HEAD | **通过**；HEAD `a698c863d5fe81cf8dca1cfdeef4bedff16ffa08` |
| 2 | HEAD 等于或严格后继于 Stage 3 起点标签 | **通过**；标签解析 `42b06b0d4e36828f8578732cc5ae70238fdfdfa5`，`git merge-base --is-ancestor` 为真 |
| 3 | S0-05 / MB-S0-07 / README 的决策状态 | **通过**；MB-D-008/009 为 ACCEPTED，计数 12/18 |
| 4 | MB-SC-00 授权文件存在且写明两项变更、顺序、边界 | **通过** |
| 5 | Schema v1；`schemas/` 只有 `1.json`；无未授权迁移 | **通过**；`CulinaryMigrations.ALL` 为空 |
| 6 | 模块集 13 | **通过**；`check-module-deps.ps1` 报告 13 模块 |
| 7 | 任一条件不符即停止 | 未触发 |

### 2.2 决策状态（不得混用）

| 状态 | 决策 |
|---|---|
| **ACCEPTED（12）** | MB-D-001、002、003、005、008、009、013、014、015、016、017、018 |
| **PROPOSED（6，不得当作已批准）** | MB-D-004、006、007、010、011、012 |

本任务没有把任何 `PROPOSED` 写成 `ACCEPTED`，没有替负责人签收，没有进入 Stage 3。

---

## 3. 执行前 Git 状态与标签解析

| 项 | 值 |
|---|---|
| 分支 | `misebase/main` |
| 执行前 HEAD | `a698c863d5fe81cf8dca1cfdeef4bedff16ffa08` |
| 执行前工作树 | 空（`git status --porcelain` 无输出） |
| Stage 3 起点标签 | `checkpoint/misebase-stage3-start-20261104` → `42b06b0d4e36828f8578732cc5ae70238fdfdfa5` |
| 祖先关系 | 起点标签是 HEAD 的祖先 |
| `framework-0.1.0` | `2b523de4b87e37a2eb08b4c7c851549350ad811a`，本任务未移动 |
| 既有 checkpoint 标签 | 全部未移动；新增 `checkpoint/misebase-schema-change-20261104` |

---

## 4. 迁移方案

完整方案见 [MB-SC-01](MB-SC-01-MIGRATION-PLAN.md)。摘要：

| 变更 | 内容 |
|---|---|
| 加列 | `recipe_version.servings INTEGER NOT NULL DEFAULT 1`，约束 `> 0` |
| 新表 | `calculation_record`：版本 ID、份数、引擎版本、payload_hash、状态、创建/确认时间 |
| 新表 | `calculation_record_line`：记录 ID、行号、食材、数量、单位、可选标记 |
| 索引 | 4 个头表索引（`payload_hash` 唯一）+ 5 个行表索引（2 个唯一） |
| 外键 | 头 → `recipe_version` RESTRICT；行 → 头 CASCADE；行 → 食材/单位 RESTRICT |
| 现有 31 表 | 30 表完全不变，1 表仅加列；不重写历史数据 |
| 休眠表 | 11 张不删、不迁、不改结构、不写数据 |

迁移事务：移除 v1 运行时部分索引 → 加列 → 建两张表 → 建 9 个索引 → 提交。
`onOpen` 重建 9 个部分索引并安装/重建触发器。

---

## 5. Schema 变更实施

| 项 | 值 |
|---|---|
| `@Database(version)` | `2` |
| 表数 | **33**（31 + 2） |
| `exportSchema` | `true` |
| `1.json` | 未修改；SHA-256 `A305AFD334C8069C8342ED0664944D8817F24BC1C06F251E1DF4DA4BFD070B7B` |
| `2.json` | 新增；154,925 bytes；SHA-256 `E9E3676A9CFD63F06E65F61906A87B11EBB95BD7E18E931430F359D08C160377` |
| `2.json` identityHash | `7db1e3391433d9f6873f2b3199a1ab1f` |
| 迁移注册 | `CulinaryMigrations.ALL = [MIGRATION_1_2]` |
| destructive migration | 无 |
| 原地降级 | 无 `2 → 1` |

实现文件：

- `CulinaryDatabase.kt`：版本 2，注册 2 个 Entity 与 `calculationRecordDao()`；
- `RecipeVersionEntity.kt`：新增 `servings`；
- `CalculationRecordEntity.kt` / `CalculationRecordLineEntity.kt`；
- `CalculationRecordStatusDb.kt` / `CoreConverters.kt`；
- `CalculationRecordDao.kt`；
- `CulinaryMigrations.kt`；
- `CulinarySchemaExtras.kt`：`servings` 检查、发布不可变包含 `servings`、计算记录/行不可变触发器、部分索引迁移支持；
- `schemas/.../2.json`。

---

## 6. DTO / Mapper / Repository 变更

完整说明见 [MB-SC-05](MB-SC-05-DTO-MAPPER-REPOSITORY.md)。摘要：

| 层 | 变更 |
|---|---|
| `RecipeVersion` | 无签名变更；`servings` 从数据库真实读出 |
| `RecipeMapper` | 映射 `version.servings` |
| `RecipeRepositoryImpl` | 写回 `servings` |
| `CalculationRecordMapper` | Entity ↔ Domain，行按 `line_no` 稳定排序 |
| `CalculationRecordRepositoryImpl` | Room 实现既有契约；头+行单事务写入；确认后不可变 |
| `DataModule` / `RepositoryModule` | DAO 提供与 Repository 绑定 |
| UI / Use Case 页面 | **未新增、未接入** |

契约保持：只保存 `CONFIRMED`；同 ID 冲突不覆盖；同 `payload_hash` 返回既有记录；
`PREVIEW` 拒绝；确认后不提供修改路径。

---

## 7. 迁移测试结果

完整记录见 [MB-SC-02](MB-SC-02-MIGRATION-TEST-RECORD.md)。

| 测试 | 结果 |
|---|---|
| 空 v1 库 → v2 | 通过 |
| 真实旧数据 → v2，旧 `servings = 1` | 通过 |
| 迁移后计算记录可写、确认后不可变 | 通过 |
| 11 张休眠表 `sqlite_master.sql` 前后一致 | 通过 |
| 9 个运行时部分索引重建 | 通过 |
| `foreign_key_check` / `quick_check` | 通过 |
| 官方 `MigrationTestHelper`（androidTest） | **已编译，未执行**（无设备） |

JVM 测试使用 `1.json` 的 `createSql` 与 `identityHash` 建 v1，由真实 Room 执行迁移，
不是手写 DDL 的“伪迁移”。

---

## 8. 备份恢复验证结果

完整记录见 [MB-SC-03](MB-SC-03-BACKUP-RESTORE-VERIFICATION.md)。

| 断言 | 结果 |
|---|---|
| `BackupArchiveWriter` 生成 v2 归档 | 通过 |
| `BackupArchiveVerifier` 校验 | 通过 |
| `DatabaseRestorer` 恢复 | 通过 |
| 恢复后 `user_version = 2` | 通过 |
| `recipe_version.servings = 3` 可读 | 通过 |
| 计算记录头 + 2 行需求可读 | 通过 |
| 恢复后 `foreign_key_check` / `quick_check` | 通过 |
| 备份/恢复实现变更 | **0** |

限制：MB-D-007 仍为 `PROPOSED`；该验证是文件级数据完整性，不证明生产 WAL 一致性备份，
不替代真机 SAF/恢复流程验收。

---

## 9. 迁移回滚验证结果

完整记录见 [MB-SC-04](MB-SC-04-ROLLBACK-VERIFICATION.md)。

**结论：回滚仅支持备份恢复，不支持原地降级。**

- 迁移前 v1 备份可独立打开：`user_version=1`，无 v2 表；
- 恢复 v1 备份后由 v2 重新迁移成功，历史 `servings=1`，无半迁移状态；
- 迁移后新增计算记录随备份回退为 0（预期数据损失）；
- 迁移注册表只有 `1 → 2`，没有 `2 → 1`；
- 未在设备上安装 v1 APK，因此“旧版本拒绝打开新 DB”只按 Room 版本规则与用户版本号说明，
  不伪称已做真机验证。

---

## 10. 测试与构建结果

### 10.1 门禁

| 命令 | 结果 |
|---|---|
| `.\gradlew.bat test --console=plain --no-daemon` | **BUILD SUCCESSFUL**；274 唯一用例 / 0 失败 |
| `.\gradlew.bat assembleDebug --console=plain --no-daemon` | **BUILD SUCCESSFUL** |
| `.\gradlew.bat ktlintCheck --console=plain --no-daemon` | **BUILD SUCCESSFUL** |
| `.\gradlew.bat detekt --console=plain --no-daemon` | **BUILD SUCCESSFUL** |
| `:core:database:compileDebugAndroidTestKotlin` | **BUILD SUCCESSFUL**（仅编译） |
| `check-module-deps.ps1` | PASS；13 模块 / 35 声明边 / 275 import 边 |
| `check-dormant-tables.ps1` | **FAIL（3 处旧文本误报）**；详见 [MB-SC-07](MB-SC-07-DORMANT-TABLES-PROOF.md)；新增业务引用 0 |

### 10.2 测试分布（唯一用例）

| 模块 | 用例 |
|---|---:|
| `core:common` | 9 |
| `core:domain` | 100 |
| `core:database` | 81 |
| `core:data` | 56 |
| `core:platform` | 19 |
| `app` | 6 |
| `feature:ingredients` | 3 |
| **合计** | **274** |

基线 264；新增 **10**：

- v1→v2 迁移 3；
- 回滚路径 1；
- 计算记录 Room 契约 4；
- 菜谱 `servings` 读取 1；
- v2 备份恢复 1。

### 10.3 产物

| 项 | 值 |
|---|---|
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` |
| APK 大小 | 36,868,970 bytes |
| APK SHA-256 | `5B1D7BCBF8AB6072FCF610467F51760A15489DD6AC95199FB3D01876CD7259CE` |
| JDK | Launcher JVM 21.0.8 |
| Gradle | 8.14.3 |
| Room | 2.8.5（未升级） |

---

## 11. Git 提交 / 标签 / 变更范围

### 11.1 提交

| # | 提交 | 内容 |
|---:|---|---|
| 1 | `ba3229c2a0fc4609d27b8b0c3795cd1607daefa0` | 迁移方案 |
| 2 | `c592d4723d156935cf018bc77371651248d7a544` | 计算记录 Entity / Converter / DAO |
| 3 | `b3db3b73c093e456ee94fd5038442bdfbb0007f7` | Migration、`servings`、`2.json`、触发器 |
| 4 | `88fe57b2c51af2bbc93a404826253d6b124eaab2` | DTO / Mapper / Repository / DI |
| 5 | `64839b6f3c4e4bac39b12571f6f1b17efbe68274` | 迁移测试与 schema 导出测试 |
| 6 | `05eca4c41fce2fd5ff32791ef81df472af0ff345` | 菜谱 servings 与计算记录 Repository 测试 |
| 7 | `c4c8abc0a45d33f5ede8a61083b3ec5ca7ebd801` | v2 备份恢复验证 |
| 8 | `61820c1663fe83f762c42f9b5e92b7ef3ab4f5c4` | 回滚路径验证 |
| 9 | `f18721ec6124949a818f22a54404611ace834b31` | 迁移后部分索引重建断言 |

报告正文提交：`6d0dbac759a62bc8bfef6a78aebf9f18feb00950`。检查点标签：`checkpoint/misebase-schema-change-20261104`。
最终 HEAD 哈希钉扎提交在最终回执中给出；未移动任何既有标签。

### 11.2 变更范围摘要

`git diff --name-status a698c863d5fe81cf8dca1cfdeef4bedff16ffa08..HEAD` 的主要类别：

```text
A  core/database/src/main/java/.../entity/CalculationRecordEntity.kt
A  core/database/src/main/java/.../entity/CalculationRecordLineEntity.kt
A  core/database/src/main/java/.../model/CalculationRecordStatusDb.kt
A  core/database/src/main/java/.../dao/CalculationRecordDao.kt
A  core/database/src/main/java/.../schemas/.../2.json
A  core/data/src/main/java/.../mapper/CalculationRecordMapper.kt
A  core/data/src/main/java/.../repository/CalculationRecordRepositoryImpl.kt
A  测试：CulinaryMigrationV1ToV2Test / CulinaryRollbackV1Test /
   CalculationRecordRepositoryTest / BackupRestoreV2Test
M  core/database/CulinaryDatabase.kt / CulinaryMigrations.kt / CulinarySchemaExtras.kt
M  core/database/RecipeVersionEntity.kt / CoreConverters.kt
M  core/data/RecipeMapper.kt / RecipeRepositoryImpl.kt / DI
M  既有测试：SchemaExportTest / DatabaseFoundationTest / DebugInfoProviderTest /
   RepositoryContractTest / CulinaryDatabaseMigrationTest
A  docs/08-schema-change/MB-SC-01..07
```

未出现：

- `settings.gradle.kts` 变更；
- `feature:scan` 或任何新模块；
- Compose 页面、导航、视觉定稿、扫码 UI；
- `core/data/src/main/java/.../backup` 下任何实现变更；
- 11 张休眠表的 Entity/DAO/列/数据变更；
- 既有 checkpoint / `framework-0.1.0` 移动；
- G3-1 / G2 历史报告正文修改。

### 11.3 工作树

交付时 `git status --porcelain` 为空（由最终回执复核）。

---

## 12. 未解决问题与风险

| # | 事项 | 状态 / 影响 |
|---:|---|---|
| R-01 | 无 Android 设备/模拟器，官方 `MigrationTestHelper` 未执行 | 已编译；JVM 迁移测试为主要证据；Stage 6 真机验收必须补跑 |
| R-02 | MB-D-007 仍 `PROPOSED` | 迁移前自动备份/写屏障/WAL checkpoint 未实现；本任务未改备份实现，v2 备份验证只是文件级 |
| R-03 | 不支持原地降级 | 只能恢复迁移前 v1 备份；回退会丢失迁移后新增数据 |
| R-04 | 旧休眠表扫描脚本 3 处文本误报 | 均为 Stage 2 既有注释/测试；本任务新增业务引用 0；Stage 3 期间可清理 |
| R-05 | 迁移临时移除 9 个运行时部分索引 | Room 校验要求；`onOpen` 原样重建；11 张表 SQL 不变；**关闭审查已接受** |
| R-06 | 无真机 SAF/进程重启恢复 | 未验证 App 内完整恢复 UI 与真实存储权限 |
| R-07 | 计算记录无业务 UI | 符合本任务边界；实际页面/Use Case 接线留待后续授权 |

---

## 13. 审查结论与保留项

**审查结论：有条件通过（CLOSED）。**

可通过依据：

- Schema v2 与 `2.json` 已生成；`1.json` 字节未变；
- JVM 迁移、完整性、Repository、备份恢复、回滚测试均通过；
- 构建、ktlint、detekt 通过；
- 变更范围集中在允许目录；
- 备份/恢复实现、休眠表、模块集、技术栈、标签均未越界；
- 报告与验证记录可复现。

关闭保留项（详见 [MB-SC-CLOSE-001 §5](MB-SC-CLOSE-001-SCHEMA-CHANGE-CLOSURE.md)）：

1. 部分索引临时移除/重建 **已接受**；
2. androidTest 未执行，Stage 6 真机验收必须补跑；
3. MB-D-007 未签收，备份为文件级验证；
4. 旧扫描脚本 3 处误报，Stage 3 期间可清理。

结论：**Schema 变更任务关闭；不构成 Stage 3 开发授权，不得据此启动 Stage 3。**

---

## 14. 停止声明

本任务在 Schema 变更、迁移、测试、备份恢复验证、回滚验证与报告提交后停止。

**未进入 Stage 3；未开发任何 Compose 页面、导航、视觉定稿、扫码 UI；未新建模块；
未修改备份/恢复实现；未移动 `framework-0.1.0` 或既有 checkpoint 标签；未恢复 G3-1；
未把任何 PROPOSED 写成 ACCEPTED。**

---

## 15. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0.0 | 2026-11-04 | Schema v1→v2 执行报告；状态 Review；含测试、备份、回滚、Git 与风险 | 接管工程组 |
| 2.0.0 | 2026-11-04 | 审查结论有条件通过；状态改为 CLOSED；补记关闭记录与关闭标签；保留项按 CLOSE-001 §5 执行 | 接管工程组 |
