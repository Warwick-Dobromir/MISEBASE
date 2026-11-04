# MB-BK-002 StartupCoordinator Schema 版本阻断修复报告

**文档编号**：MB-BK-002-REPORT
**版本**：`1.0.0`
**状态**：`Review`
**日期**：2026-11-04
**任务书**：MB-BK-002（修复 `StartupCoordinator.EXPECTED_SCHEMA_VERSION` 阻断缺陷）
**依据**：MB-BK-001 审查澄清（问题一确认阻断级；问题二为报告口径错误）
**分支**：`misebase/main`

---

## 1. 执行摘要

| 项 | 结果 |
|---|---|
| 缺陷 | `EXPECTED_SCHEMA_VERSION = 1` 与 Schema v2 不一致；已持久化 v2 库第二次启动被 `Incompatible` 软锁 |
| 修复 | 常量改为 `2`，并加“必须与 `@Database(version = 2)` 同步”的注释 |
| 测试 | `StartupCoordinatorTest` 5 例全 PASS；覆盖全新库、持久化 v2、v1→v2、v3 拒绝、损坏库 |
| 全量 | **612 次执行 / 376 唯一用例 / 0 失败**；Debug APK 成功；ktlint/detekt PASS |
| 范围 | 只改启动版本常量 + 测试；无 Schema/Entity/迁移/备份/UI/导航/模块集变更 |
| 检查点 | 新标签 `checkpoint/misebase-backup-consistency-v2-20261104`；旧标签未移动 |
| 状态 | `Review`；不进入 Stage 4 |

---

## 2. 执行前 HEAD

| 项 | 值 |
|---|---|
| 分支 | `misebase/main` |
| 执行前 HEAD | `f8ede2cfb09ad820cc4ff459b57007f3d2fd3bf4` |
| HEAD 信息 | `docs(backup): correct mb-bk-001 test baseline and unique-count statistics` |
| 工作树 | 干净（`git status --porcelain` 无输出） |

---

## 3. 变更文件清单

| 状态 | 文件 |
|---|---|
| M | `core/data/src/main/java/com/culinary/core/data/startup/StartupCoordinator.kt` |
| M | `core/data/src/test/java/com/culinary/core/data/startup/StartupCoordinatorTest.kt` |
| A | `docs/10-backup/MB-BK-002-STARTUP-SCHEMA-FIX.md` |
| A | `docs/10-backup/MB-BK-002-CHECKPOINT-RECEIPT.md` |
| M | `docs/00-governance/DOCUMENT-INDEX.md` |

未修改：`CulinaryDatabase` 的 `version`、Entity、表结构、Schema JSON、迁移、数据库文件；备份/恢复实现；`StartupState`/UI；导航；模块集；Compose 页面。

---

## 4. 修复内容与测试结果

### 4.1 修复

`StartupCoordinator.kt`：

```kotlin
const val EXPECTED_SCHEMA_VERSION = 2
```

注释明确：必须与 `CulinaryDatabase` 的 `@Database(version = 2)` 同步；升级 Schema 时同时修改两处，并补“已持久化库再次启动”的回归测试。

未改动检查逻辑本身：

```text
existingVersion > expected → Incompatible
actual < expected          → Recovery(MIGRATION_FAILED)
否则                        → Ready
```

### 4.2 测试结果

命令：

```powershell
.\gradlew.bat :core:data:testDebugUnitTest --tests "com.culinary.core.data.startup.StartupCoordinatorTest" --console=plain --no-daemon
```

| 测试 | 断言 | 结果 |
|---|---|---|
| `reports ready for a fresh database` | 全新库 → `Ready` | PASS |
| `reports ready for a persisted schema v2 database` | 建 v2 库、写数据、关闭；默认 coordinator → `Ready`，DB 版本 2 | PASS |
| `migrates a v1 database to v2 and reports ready` | 构造 v1 夹具（无 `servings`、无计算记录表、`user_version=1`）→ 启动 → `Ready`；迁移后 `user_version=2`、`servings` 存在、数据保留 | PASS |
| `rejects a database created by a newer app` | `user_version=3` → `Incompatible(3, 2)` | PASS |
| `routes a corrupt database to recovery without deleting it` | 损坏文件 → `Recovery(DATABASE_CORRUPT)`，文件保留 | PASS |

v1 夹具说明：

- 不依赖 instrumentation / `room-testing`，在 JVM 测试内用当前 v2 库反向构造 v1；
- 重建不含 `servings` 的 `recipe_version`、删除 v2 新增计算记录表、恢复 v1 `identityHash`；
- 断言夹具确实是 v1（`version=1`、无 `servings`、无 `calculation_record*`）后，再调用 coordinator；
- 迁移由生产 `DatabaseFactory` 的 `CulinaryMigrations` 执行，测试真实覆盖 v1→v2 路径。

### 4.3 全量测试与构建

```powershell
.\gradlew.bat test assembleDebug ktlintCheck detekt --console=plain --no-daemon
```

| 项 | 结果 |
|---|---|
| 构建 | `BUILD SUCCESSFUL` |
| JVM 测试 | **612 次执行 / 376 唯一用例 / 0 失败** |
| 修复前 | 608 次执行 / 374 唯一用例 |
| 新增 | **2 个唯一用例（4 次执行）**，均在本类 |
| Debug APK | 构建成功 |
| ktlint / detekt | PASS |

---

## 5. 提交完整哈希

| 提交 | 完整哈希 | 内容 |
|---|---|---|
| 修复提交 | `30d57bbc630b92431903dc084631d6116851986b` | `fix(startup): align expected schema version with database v2` |
| 报告提交 | 由检查点回执记录解析提交 | 本报告、回执、索引 |
| 回执提交 | 由检查点回执自身记录 | 标签对象哈希补记 |

---

## 6. 新检查点对象哈希与解析提交

| 项 | 值 |
|---|---|
| 标签名 | `checkpoint/misebase-backup-consistency-v2-20261104` |
| 类型 | annotated tag |
| 对象哈希 | 见 [检查点回执](MB-BK-002-CHECKPOINT-RECEIPT.md) |
| 解析提交 | 见 [检查点回执](MB-BK-002-CHECKPOINT-RECEIPT.md) |
| 关系 | 旧检查点 `checkpoint/misebase-backup-consistency-20261104` 的**后继**，不是替换；旧标签仍可回档 |

---

## 7. 旧标签解析结果

| 标签 | 对象哈希 | 解析提交 | 是否移动 |
|---|---|---|---|
| `checkpoint/misebase-backup-consistency-20261104` | `4a271e8ac0081b8ff21b1966756d0432a7767671` | `8787c059a4a888f16ad8837ef8722316d762c257` | **未移动** |
| `checkpoint/misebase-mb-d-007-20261104` | `b3a429f9d2698c245f8106a421c1329686a61d61` | `3c3db1eb7c835b42c671b2094d8ee4249d599c6b` | 未移动 |
| `framework-0.1.0` | `2b523de4b87e37a2eb08b4c7c851549350ad811a` | 未移动 |
| `checkpoint/misebase-stage4-start-20261104` | `c5ea6feeef0a843b4f84590c7ea7f72b0e6693f2` | 未移动 |

两个备份一致性检查点的关系：

- 旧检查点 `…backup-consistency-20261104` → `8787c05`：MB-BK-001 的写屏障/checkpoint/Worker/恢复原子性，以及当时代码与报告；
- 新检查点 `…backup-consistency-v2-20261104`：在旧检查点之后，包含
  1. `f8ede2c` 测试统计口径更正；
  2. `30d57bb` Schema 版本阻断修复 + 回归测试；
  3. 本报告与检查点回执。
- 旧标签保留，用于回档与审查追溯；新标签是审查后的修复坐标。

---

## 8. 工作树状态

- `git status --porcelain`：为空（回执提交后复核）。
- 新标签解析提交包含版本修复与报告统计更正；旧标签未移动。

---

## 9. 停止声明

**未进入 Stage 4。**

- 未开发库存业务、库存流水、采购清单、计算记录 UI；
- 未开发/修改 Compose 页面、导航、视觉定稿、扫码 UI；
- 未新建 `feature:scan` 或任何模块；
- 未修改 Schema 版本、Entity、表结构、迁移、数据库文件；
- 未修改备份/恢复实现；
- 未升级/更换技术栈；
- 未移动 `framework-0.1.0` 及任何既有 checkpoint 标签；
- 未启用 CI、未做真机验收、未创建签名（MB-D-010/011/012 仍 `PROPOSED`）。

**状态 `Review`，等待审查结论；不自动关闭 MB-D-007。**

---

## 10. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0.0 | 2026-11-04 | MB-BK-002：Schema 版本阻断修复、回归测试、全量验证与新检查点 | 接管工程组 |
