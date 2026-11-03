# G2 阶段 9（质量门禁与基线）完成报告

**文档编号**：R19
**版本**：`1.2.0`
**状态**：`Approved`（项目负责人 2026-11-04 验收通过）
**日期**：2026-11-04
**实施依据**：[R01 0.2.2](R01-FRAMEWORK-SEQUENCE.md) §12、R09、E05 §13、E06
**回档点**：`baseline/g2-stage8-20261104`（`18a5aa8`）

---

## 1. 任务目标

质量门禁：CI、lint、迁移测试、备份/恢复演练、离线冒烟、Release/R8、依赖规则、基线与文档。

## 2. 交付内容

| 项 | 内容 |
|---|---|
| CI | `.github/workflows/ci.yml`：ktlint、detekt、lint、test、assembleDebug + assembleRelease |
| 迁移测试 | `CulinaryDatabaseMigrationTest`（MigrationTestHelper，读取导出的 `1.json`） |
| Release/R8 | `release` 开启 minify/shrinkResources，产出 APK 与 `mapping.txt` |
| 恢复（文件级） | `DatabaseRestorer`：SQLite 头校验 → 回滚副本 → 清 sidecar → 临时文件 + fsync + 原子替换 |
| 恢复（完整流程） | `RestoreExecutor`：关闭连接 → 备份 → 装库 → `integrity_check` → journal 推进；失败回滚；启动时 journal 恢复 |
| 复盘 | `DatabaseRestoreDrillTest` + `RestoreExecutorTest` |
| 离线 | 断网冷启动；APK 无 `INTERNET` |
| 依赖规则 | `scripts/check-module-deps.ps1` → PASS |

## 3. 退出条件核对（R01 §12.2，按证据类别拆分）

| # | 条件 | 结论 | 证据类别与说明 |
|---|---|---|---|
| 1 | CI 全绿 | **BLOCKED（远端）** | workflow 已提交；本机 `git remote -v` 为空、无 `gh` CLI，**无法在远端实跑**；本地等价命令通过不能替代远端 CI |
| 1b | 本地等价门禁 | **PASS（本地）** | `ktlintCheck detekt lintDebug test assembleDebug` → BUILD SUCCESSFUL（先 `:app:clean`，见 §5 的 lint 说明） |
| 2 | Release/R8 构建成功 | **PASS** | `:app:assembleRelease` 成功；APK 23,533,968 bytes；`mapping.txt` 35.5 MB |
| 3 | 迁移测试全绿 | **PASS（模拟器）** | `Starting 2 tests` → `Finished 2 tests`，BUILD SUCCESSFUL |
| 4 | 备份恢复演练通过 | **PASS（文件级 + 完整流程；在线快照未验证）** | 5 例：2 例 drill、3 例完整流程；见 §4；E05 §6 在线快照/加密未实现 |
| 5 | 真机/模拟器安装通过 | **PASS（模拟器）** | `adb install -r` + 冷启动 |
| 6 | 基线标签与产物归档 | **PARTIAL** | 标签随后验收落定；APK/mapping 在 `app/build/outputs/`，**未入库归档** |
| 7 | 框架启动报告更新为“框架已就绪” | **PASS** | R04 §9 追加 G2 后记 |

## 4. 恢复证据（本轮修订重点）

### 4.1 完整流程测试（`RestoreExecutorTest`，Robolectric + 真实 Room）

| 测试 | 验证点 |
|---|---|
| `restores the database closes the connection and clears sidecars` | 关闭连接（`isOpen == false`）、删除 `-wal`/`-shm`、journal `DONE`、重新打开 Room 后数据为备份内容 |
| `rolls back to the previous database when the restore fails` | 装库后注入异常 → 回滚到恢复前库（含恢复前记录）、journal `ROLLED_BACK`、重新打开 Room 可读 |
| `recovers an interrupted restore from the journal and rollback copy` | 手工构造 `DB_INSTALLED` journal + 回滚副本 + 损坏的当前库 → `recoverIfInterrupted` 还原、journal 清理、重新打开 Room 可读 |

### 4.2 文件级测试（`DatabaseRestoreDrillTest`）

| 测试 | 验证点 |
|---|---|
| `restores a backed up database with its data intact` | 真实 Room 库 → 归档 → 校验 → 取 DB → 恢复 → 只读计数 |
| `keeps the existing database when the payload is not SQLite` | 非 SQLite 负载不覆盖现有库 |

### 4.3 本轮修复的真实缺陷

- `RestoreJournalStore` 原先用 `renameTo` 覆盖已存在的 journal，Windows 下第二次写入失败，
  导致 journal 停留在 `PREPARED`；已改为 `Files.move(REPLACE_EXISTING, ATOMIC_MOVE)` 降级策略。
- 该缺陷正是“只做文件级替换测试”发现不了的，本轮完整流程测试将其暴露。

### 4.4 未验证的 E05 条件（明确列出，不标 PASS）

| 条件 | 状态 |
|---|---|
| E05 §6 在线快照（写屏障 + `wal_checkpoint`） | **未实现、未验证** |
| 附件（图片）随备份恢复 | **未验证**（当前恢复只处理 DB） |
| 备份加密（E05 可选） | **未实现** |
| WAL 模式下的恢复 | 测试数据库使用 `TRUNCATE` journal mode；生产 WAL 仅验证 sidecar 删除，**未验证活跃 WAL 场景** |
| 恢复 UI 接线 | **未做**（负责人明确本次不要求） |

## 5. 实测记录

```text
.\gradlew.bat :app:clean
.\gradlew.bat ktlintCheck detekt lintDebug test assembleDebug
BUILD SUCCESSFUL in 2m 6s

.\gradlew.bat :app:assembleRelease
BUILD SUCCESSFUL（R8 + resource shrink）

.\gradlew.bat :core:database:connectedDebugAndroidTest
Starting 2 tests on Pixel_5(AVD) - 11
Finished 2 tests on Pixel_5(AVD) - 11

powershell -File scripts\check-module-deps.ps1
PASS: module count, module set and dependency directions match A03A.
```

- 离线：`svc wifi disable` + `svc data disable` 后 `ping 8.8.8.8` 返回 `Network is unreachable`，
  App 冷启动进入首页。
- lint 说明：并行执行 release 构建后，`lint` 曾因 KSP 生成路径过期崩溃（lint 自身 bug）；
  `:app:clean` 后 `lintDebug` 通过，CI 使用 `lintDebug`。

## 6. 未闭合项（保留，未删除此前阶段的遗留）

| 项 | 来源 |
|---|---|
| GitHub CI 实跑 | 本阶段：无远端仓库/凭据 |
| 扫码/SAF 设备级验证 | 阶段 5 延后；阶段 7 未接 UI |
| 自动化 Compose/仪器化 E2E | 阶段 7 |
| 恢复 UI 接线 | 本阶段（负责人不要求） |
| 场景 3 来源建模、商品映射、快照元数据 | 阶段 8 |
| E05 在线快照、附件恢复、加密、活跃 WAL 恢复 | 本阶段（见 §4.4） |
| APK/mapping 入库归档 | 本阶段 |

## 7. Git

| 项 | 值 |
|---|---|
| 阶段 8 基线 | `baseline/g2-stage8-20261104`（`18a5aa8`） |
| 阶段 9 提交 | `b9e1af9`、`c5bc8f9`、`cf6af06`（恢复完整流程）、本报告提交 |
| 阶段 9 标签 | `baseline/g2-stage9-20261104`、`framework-0.1.0`（验收时指向本修订提交；阶段 0–8 基线未改动） |

## 8. 结论

本地门禁、迁移、R8、安装、离线、文件级与完整流程恢复均有实测证据；
远端 CI 因缺少仓库与凭据 **BLOCKED**，在线快照/附件/加密/活跃 WAL 恢复明确**未验证**。
未闭合项全部保留，未将任何未完成项写成 PASS。

**请项目负责人验收阶段 9 修订版（R19 1.1.0）。**

## 9. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0.0 | 2026-11-04 | 记录质量门禁、迁移/恢复/离线证据、基线与未闭合项 | 工程组 |
| 1.1.0 | 2026-11-04 | 补 RestoreExecutor 完整流程证据（成功/回滚/journal 恢复）；拆分证据类别；CI 标 BLOCKED；补 E05 未验证项；修复 journal 覆盖缺陷 | 工程组 |
| 1.2.0 | 2026-11-04 | 项目负责人验收通过阶段 9；状态改为 Approved | 工程组 |
