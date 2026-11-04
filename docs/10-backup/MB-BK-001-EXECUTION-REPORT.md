# MB-BK-001 备份一致性修复执行报告

**文档编号**：MB-BK-001-REPORT
**版本**：`1.0.0`
**状态**：`Review`
**日期**：2026-11-04
**任务书**：MB-BK-001（MB-D-007 备份一致性修复，Stage 4 强制前置）
**授权依据**：[MB-D-007 授权](MB-D-007-AUTHORIZATION.md)
**分支**：`misebase/main`
**执行前 HEAD**：`3c3db1eb7c835b42c671b2094d8ee4249d599c6b`（MB-D-007 签收提交）
**结论**：六项技术事项已实现并有可复现 JVM 测试；**状态 Review，未进入 Stage 4**。

---

## 1. 执行摘要

本次只做备份一致性修复，未改 Schema/Entity/表结构/迁移，未开发页面/导航/扫码/库存 UI，未新建模块。

| 事项 | 实现 | 测试 | 状态 |
|---|---|---|---|
| 写屏障 | `WriteBarrier` + `RoomTransactionRunner` 共享同一把 `Mutex` | `WriteBarrierTest` 5 例 | 完成 |
| WAL checkpoint | `QuiescedDatabaseSnapshotter`：屏障内 checkpoint → quick_check → 复制 → 校验 | `QuiescedDatabaseSnapshotterTest` 4 例 | 完成 |
| Worker 真实返回 | `AutoBackupWorker` + `AutoBackupRunnerImpl`；success/retry/failure/取消 | 6 + 8 例 | 完成 |
| 恢复原子性 | checkpoint → 回滚副本校验 → 临时文件 + fsync + rename + 目录 fsync | `RestoreExecutorTest` 6 例 | 完成 |
| Worker 单测 | 成功/校验失败/空间不足/权限/屏障超时/取消 + Scheduler 集成 | 15 例 | 完成 |
| WAL 恢复测试 | 未 checkpoint 事务、并发写、中断注入 | `WalBackupRestoreTest` 2 例、`BackupConcurrencyTest` 1 例 | 完成 |

测试与构建：`test assembleDebug ktlintCheck detekt` **BUILD SUCCESSFUL**；**608 次测试执行 / 0 失败**；Debug APK 构建成功。

---

## 2. 启动前置检查与决策状态

### 2.1 前置检查

| # | 检查 | 实际 | 结论 |
|---:|---|---|---|
| 1 | `git status --porcelain` 为空；记录 HEAD | 输出为空；HEAD `3c3db1eb7c835b42c671b2094d8ee4249d599c6b` | PASS |
| 2 | HEAD 等于或严格后继于 MB-D-007 签收提交 | `checkpoint/misebase-mb-d-007-20261104`（tag 对象 `b3a429f9d2698c245f8106a421c1329686a61d61`）解析提交 = `3c3db1eb…` = HEAD | PASS |
| 3 | S0-05 / MB-S0-07 / README 写明 13 项 ACCEPTED、MB-D-007 ACCEPTED、Stage 4 强制前置 | S0-05 13/18；MB-S0-07 13/18；README 记录 MB-D-007 ACCEPTED 并标注 Stage 4 强制前置 | PASS（README 阶段/测试数陈旧，见 §16） |
| 4 | 授权文件存在，含六项技术事项、验收、Stage 4 条件、工程原则 | [MB-D-007-AUTHORIZATION.md](MB-D-007-AUTHORIZATION.md) 114 行，§2/§3/§4/§5 齐全 | PASS |
| 5 | Schema v2 / 33 表；`1.json`、`2.json` 存在；无未授权迁移 | `2.json` version=2、entities=33；`1.json` version=1、31 表；两文件在库；本次未改 Schema | PASS |
| 6 | 模块集 13；`feature:scan` 不存在 | `settings.gradle.kts` 13 模块；`feature/scan` 不存在；依赖检查 13/13 | PASS |
| 7 | 任一不符即停止 | 全部通过（README 陈旧仅记录，不属于第 3 项硬条件） | PASS |

### 2.2 决策状态

| 状态 | 决策 |
|---|---|
| `ACCEPTED`（13） | MB-D-001、002、003、005、008、009、013、014、015、016、017、018、**007** |
| `PROPOSED`（5） | MB-D-004、006、010、011、012 |

本任务未把任何 `PROPOSED` 写成 `ACCEPTED`；未签收项未启用 CI、未做真机验收、未创建签名、未新建 `feature:scan`。

---

## 3. 执行前 Git 状态与基线标签解析

| 项 | 值 |
|---|---|
| 分支 | `misebase/main` |
| 执行前 HEAD | `3c3db1eb7c835b42c671b2094d8ee4249d599c6b` |
| HEAD 信息 | `docs(decisions): accept mb-d-007 backup consistency as stage 4 prerequisite` |
| `checkpoint/misebase-mb-d-007-20261104` | tag 对象 `b3a429f9d2698c245f8106a421c1329686a61d61`；解析提交 `3c3db1eb7c835b42c671b2094d8ee4249d599c6b` |
| Stage 4 起点标签 | `checkpoint/misebase-stage4-start-20261104` → `c5ea6feeef0a843b4f84590c7ea7f72b0e6693f2`（未移动） |
| `framework-0.1.0` | `2b523de4b87e37a2eb08b4c7c851549350ad811a`（未移动） |
| 执行前工作树 | 干净（`git status --porcelain` 无输出） |

---

## 4. 写屏障设计与实现

**代码**：`core/data/src/main/java/com/culinary/core/data/backup/WriteBarrier.kt`；`RoomTransactionRunner`、`DataModule`、`AppServicesModule` 接入。

- 全应用**一把** `Mutex`；业务写经 `WriteBarrier.withWrite`，备份/恢复经 `acquireForBackup`；读不经过屏障。
- 获取：`withTimeoutOrNull` 包裹 `Mutex.lock`，默认 5 秒；超时返回 `TIMEOUT`，屏障已持有返回 `ALREADY_HELD`，均不继续。
- 释放：`releaseForBackup()` 用 `AtomicBoolean` 保证只解锁一次；未持有时返回 false。
- 非可重入：持有屏障时再次获取被拒绝；屏障内禁止调用写入口（否则自锁）。
- 失败/取消：调用方 `finally` 释放；进程被杀后屏障随进程消失，不持久化、不建表。
- 诊断：`activeWriters`、`queuedWriters`、`barrierHeld`，获取/释放/超时写日志。
- `RoomTransactionRunner` 保留 `Mutex` 兼容构造器，生产走共享 `WriteBarrier`。

测试：`WriteBarrierTest`（串行化、超时、失败释放、嵌套拒绝、排队写被服务）。

---

## 5. WAL checkpoint 实现

**代码**：`core/data/src/main/java/com/culinary/core/data/backup/QuiescedDatabaseSnapshotter.kt`、`AtomicFiles.kt`。

顺序与判定：

```text
获取屏障
  → PRAGMA wal_checkpoint(TRUNCATE) → (busy, log, checkpointed)
  → busy==0 && log==checkpointed 才继续
  → PRAGMA quick_check = ok
  → 复制主库到快照 + fd.sync
  → 只读打开快照：quick_check / user_version
  → 释放屏障
```

- `checkpoint_busy`、`checkpoint_incomplete`、`checkpoint_error`、`checkpoint_no_result` 均失败，不复制、不生成归档。
- 不复制活动 `-wal`/`-shm`；不修改 WAL 模式；不做 destructive 操作。
- 快照失败会删除半成品并在 `finally` 释放屏障。

---

## 6. AutoBackupWorker 返回语义

**代码**：`AutoBackupWorker.kt`、`AutoBackupRunnerImpl.kt`、`AutoBackupRunner`（domain 端口）、`BackupModule`。

- Worker 不再固定 `success`。
- 成功条件：归档存在 + `BackupArchiveVerifier` 通过 + `backup_record` 写入成功。
- 可重试：空间不足、临时 IO、checkpoint busy、记录写入暂时失败 → `Result.retry`。
- 不可重试：权限拒绝、归档校验失败、写屏障超时、快照校验失败 → `Result.failure` + reason。
- 取消：清理临时归档/快照；已 rename 但未记录时删除最终归档；不写成功记录。
- 输入/输出只经 `Data`：`backup_id`、`target_directory`、`archive_path`、`record_id`、`reason`。
- 自动备份默认目录：`getExternalFilesDir(DIRECTORY_DOCUMENTS)/CULINARY/Backups`，回退 `filesDir/backups`；SAF 用户目录属后续设置页任务。
- `backup_record` 经 `TransactionRunner` 写入，状态 `VERIFIED`，记录 SHA-256/大小/schema/时间/URI。

---

## 7. 恢复原子性实现

**代码**：`RestoreExecutor.kt`、`DatabaseRestorer.kt`、`AtomicFiles.kt`。

顺序：

```text
获取写屏障（超时/已持有 → 失败）
  → 恢复前 wal_checkpoint(TRUNCATE)
  → 关闭 Room
  → 当前库 → rollback/<restoreId>.db（原子复制）
  → 校验回滚副本（SQLite 头 + quick_check）
  → 删除 -wal/-shm
  → 同目录临时文件 + fd.sync + ATOMIC_MOVE + 目录 fsync
  → 打开新库：quick_check + foreign_key_check
  → journal VERIFIED → DONE
  → 释放屏障
```

- 目标库在 rename 前不被覆盖；失败只删除临时文件。
- 恢复前 checkpoint，保证回滚副本包含 WAL 中已提交事务。
- 回滚副本不可用则**不开始装库**。
- 回滚使用同样的原子替换。
- 回滚失败 → `RECOVERY_REQUIRED`，保留现场，不自动清库。
- 中断恢复 `recoverIfInterrupted()`：rollback 缺失/损坏不再自动清 journal，改为 `RECOVERY_REQUIRED` 并报告。
- 恢复后连接保持关闭，调用方重启/重建连接。
- 附件恢复未接线：本任务只恢复 DB；设计保留 staging + 原子替换约束，不声称附件恢复完成。

---

## 8. Worker 单元测试结果

命令：`.\gradlew.bat :core:platform:testDebugUnitTest --tests "com.culinary.core.platform.work.*" --console=plain --no-daemon`

| 套件 | 例数 | 覆盖 | 结果 |
|---|---:|---|---|
| `AutoBackupWorkerTest` | 6 | success / retry / failure / 取消 / 输入 Data / 运行器不可解析 | PASS |
| `SchedulerWorkerIntegrationTest` | 1 | 入队 → Worker 真实执行 → 归档结构校验 → SUCCEEDED | PASS |

编排层补充：`AutoBackupRunnerImplTest` 8 例（成功、归档校验失败、空间不足、权限拒绝、屏障超时、取消清理、rename 后取消、记录写入失败）。

详细记录见 [Worker 测试记录](MB-BK-001-WORKER-TEST-RECORD.md)。

---

## 9. WAL 恢复测试结果

| 套件 | 例数 | 覆盖 | 结果 |
|---|---:|---|---|
| `QuiescedDatabaseSnapshotterTest` | 4 | WAL 未 checkpoint 事务、checkpoint busy、屏障超时、失败释放 | PASS |
| `WalBackupRestoreTest` | 2 | 未 checkpoint 事务备份→校验→恢复→读取；恢复后 `quick_check`/`foreign_key_check` | PASS |

关键断言：备份前 `-wal` 有帧；checkpoint 后 `-wal` 长度 0；恢复库包含“最后一条已提交事务”；无 `-wal`/`-shm` 残留。详见 [WAL 验证记录](MB-BK-001-WAL-VERIFICATION.md)。

---

## 10. 并发写与中断注入结果

| 场景 | 测试 | 断言 | 结果 |
|---|---|---|---|
| 40 批并发写 + 备份 | `BackupConcurrencyTest.concurrent writes are captured as a consistent logical point` | 快照内每批 0/2 行；无半事务；活库最终 80 行；记录 1 条；屏障释放 | PASS |
| 备份取消（打包阶段） | `AutoBackupRunnerImplTest.cleans temporary files and writes no record when cancelled` | 无临时/快照残留；0 记录；原库可读 | PASS |
| 备份取消（rename 后） | `AutoBackupRunnerImplTest.removes the renamed archive when cancelled before the record write` | 最终归档删除；0 记录 | PASS |
| 恢复中断 | `RestoreExecutorTest.recovers an interrupted restore from the journal and rollback copy` | 从 journal + rollback 恢复；数据为中断前 | PASS |
| 恢复失败回滚 | `RestoreExecutorTest.rolls back to the previous database when the restore fails` | `ROLLED_BACK`；数据为恢复前 | PASS |
| 回滚失败 | `RestoreExecutorTest.enters RECOVERY_REQUIRED when the rollback copy cannot be restored` | `RECOVERY_REQUIRED`；现场保留 | PASS |
| 回滚副本损坏 | `RestoreExecutorTest.keeps the original database when the rollback copy is corrupt` | 装库前失败；原库不被覆盖；`ROLLED_BACK` | PASS |

限制：中断用异常/journal 模拟，不是真实 kill/断电。

---

## 11. 备份恢复一致性验证结果

- WAL 活库 → 快照 → 归档 → verifier → 恢复 → 读取，全链路 PASS。
- 归档内 DB 与快照逻辑状态一致（计数断言）。
- 并发写下快照为单一逻辑时点（批次完整性断言）。
- 成功必须同时满足“归档存在 + 校验通过 + `backup_record` 写入”。

详见 [一致性验证记录](MB-BK-001-CONSISTENCY-VERIFICATION.md)。

---

## 12. 失败与回滚验证结果

备份失败路径：屏障超时、空间不足、权限拒绝、归档损坏、取消、记录失败，均不写成功记录、清理临时文件、释放屏障。

恢复失败路径：装库失败回滚、回滚失败 `RECOVERY_REQUIRED`、屏障占用拒绝、中断 journal 恢复、非 SQLite 载荷拒绝。

详见 [失败与回滚验证记录](MB-BK-001-FAILURE-ROLLBACK.md)。

---

## 13. 风险登记册更新

[S0-04](../05-handover/S0-04-RISK-REGISTER.md) 升 `1.1.0`：

| 风险 | 原状态 | 新状态 |
|---|---|---|
| S0-R-02 WAL 与备份一致性 | OPEN（高） | **MITIGATED**（残留真机/kill 验证） |
| S0-R-10 文件恢复中断 | PARTIAL | **PARTIAL（收窄）**：DB 原子性已实现；附件与真机未覆盖 |
| S0-R-11 自动备份假成功 | OPEN（高） | **MITIGATED**（残留生产触发点未接线） |

状态统计更新为 `OPEN 6 / PARTIAL 5 / MONITORED 2 / MITIGATED 2 / CLOSED 0`。

---

## 14. 测试与构建结果

命令：

```powershell
.\gradlew.bat test assembleDebug ktlintCheck detekt --console=plain --no-daemon
```

| 项 | 结果 |
|---|---|
| 构建 | `BUILD SUCCESSFUL in 1m 42s`（927 tasks；133 executed） |
| JVM 测试 | **608 次执行 / 0 失败 / 0 跳过**；**374 个唯一用例**（debug+release；JVM 模块单变体、Compose 测试仅 debug） |
| 基线（MB-BK-001 开始前） | **344 个唯一用例 / 548 次执行 / 0 失败**（与 Stage 3 关闭口径一致） |
| 新增 | **30 个唯一用例（60 次执行）**，分布见 §8–§10 与 [Worker 记录](MB-BK-001-WORKER-TEST-RECORD.md) |
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk`，37,678,729 字节 |
| ktlint | PASS |
| detekt | PASS |
| 模块依赖 | 13/13、35 条声明边、DAG、PASS |
| 休眠表 | 11 张、0 处新业务引用、PASS |
| 文档一致性 | PASS |

测试数未下降（唯一用例 344 → 374；执行次数 548 → 608）。

> **统计口径更正（MB-BK-001-CLARIFY-001）**：本报告初稿把“Stage 3 关闭”误写成 Stage 2 基线 274，
> 并用“执行次数 ÷ 2”估算唯一用例数（得到约 304），两者都不正确。正确口径为：
> Stage 3 关闭 344 → 本任务 374（+30）；608 次执行对应 374 个唯一用例，差异来自
> JVM-only 模块单变体、Android 库 debug/release 双变体、Compose/UI 测试仅 debug。
> 未删除任何测试；`git diff --diff-filter=D 3c3db1e..HEAD` 为空。

---

## 15. Git 提交 / 标签 / 变更范围

### 15.1 分提交

| 提交 | 内容 |
|---|---|
| `9310aca` | 设计文档（写屏障/checkpoint/Worker/恢复原子性） |
| `6b282da` | 写屏障 + `RoomTransactionRunner`/DI 接入 + 测试 |
| `db48976` | checkpoint 快照器 + 原子文件工具 + 测试 |
| `0936185` | Worker 真实返回 + 编排 + 域端口 + Worker/集成测试 |
| `619d97b` | 恢复原子性 + 回滚校验 + 测试 |
| 报告提交前修正 | 回滚副本校验失败时不得覆盖原库（`installAttempted` 门控）+ 对应测试 |
| `49686ac` | WAL 恢复、并发写、中断注入测试 |
| 本报告提交 | 执行报告、验证记录、风险登记册、索引 |

### 15.2 检查点

| 项 | 值 |
|---|---|
| 标签名 | `checkpoint/misebase-backup-consistency-20261104` |
| 类型 | annotated tag |
| 报告对象哈希/解析提交 | 见 [检查点回执](MB-BK-001-CHECKPOINT-RECEIPT.md) |
| `framework-0.1.0` | `2b523de4b87e37a2eb08b4c7c851549350ad811a`，未移动 |
| 既有 `checkpoint/*` / `baseline/*` | 未移动；仅新增本检查点 |

### 15.3 变更范围（执行前 HEAD `3c3db1e` → 报告提交）

```text
A core/data/.../backup/AtomicFiles.kt
A core/data/.../backup/AutoBackupRunnerImpl.kt
M core/data/.../backup/DatabaseRestorer.kt
A core/data/.../backup/QuiescedDatabaseSnapshotter.kt
M core/data/.../backup/RestoreExecutor.kt
A core/data/.../backup/WriteBarrier.kt
M core/data/.../di/AppServicesModule.kt
A core/data/.../di/BackupModule.kt
M core/data/.../di/DataModule.kt
M core/data/.../transaction/RoomTransactionRunner.kt
A core/data/src/test/.../AutoBackupRunnerImplTest.kt
A core/data/src/test/.../BackupConcurrencyTest.kt
A core/data/src/test/.../QuiescedDatabaseSnapshotterTest.kt
M core/data/src/test/.../RestoreExecutorTest.kt
A core/data/src/test/.../WalBackupRestoreTest.kt
A core/data/src/test/.../WriteBarrierTest.kt
A core/domain/.../platform/AutoBackupRunner.kt
M core/domain/.../platform/BackupScheduler.kt
M core/platform/.../work/AutoBackupWorker.kt
M core/platform/.../work/WorkManagerBackupScheduler.kt
A core/platform/src/test/.../AutoBackupWorkerTest.kt
A core/platform/src/test/.../SchedulerWorkerIntegrationTest.kt
A docs/10-backup/*.md（设计、执行报告、验证记录、回执）
M docs/05-handover/S0-04-RISK-REGISTER.md
M docs/00-governance/DOCUMENT-INDEX.md
```

未出现：`core/database/.../entity/**`、`core/database/schemas/**`、迁移、Compose 页面、导航、`feature:scan`、库存业务。

### 15.4 工作树

最终 `git status --porcelain` 为空（见检查点回执提交后的复核）。

---

## 16. 未解决问题与风险

| # | 问题 | 影响 | 处理 |
|---:|---|---|---|
| 1 | **`StartupCoordinator.EXPECTED_SCHEMA_VERSION = 1` 与 Schema v2 不一致**（既有缺陷，非本次授权范围） | 已有 v2 库可能在启动门禁被判 `Incompatible` | 未修改；需单独授权修复并补测试 |
| 2 | 附件（图片）未随自动备份/恢复接线 | 备份只含 DB，图片不在归档/恢复内 | 记录为限制；后续附件任务 |
| 3 | SAF 用户目录未接线 | 自动备份使用应用外部私有目录 | 设置页/SAF 后续任务 |
| 4 | 真实进程 kill/断电未验证 | JVM 模拟不能替代真机文件系统 | MB-D-011 未签收；真机验证待授权 |
| 5 | 生产自动备份触发点未接线 | Worker 真实返回，但默认不会自动运行 | 设置页/调度接线后续任务 |
| 6 | 目录 fsync 在部分平台为 best-effort | 极端断电下目录项持久化不保证 | 原子 rename 已保证目标不半写；记录风险 |
| 7 | README 阶段/测试数/备份状态陈旧 | 新成员可能误读 | 未在本次授权改动；建议 MB-D-018 后续修正 |

无未解决的**阻断级**数据安全问题（在 JVM 验证范围内）。

---

## 17. 是否具备审查条件

具备。理由：

- 六项技术事项均有实现与可复现测试；
- `test assembleDebug ktlintCheck detekt` 全绿；
- 备份恢复一致性、WAL、失败/回滚记录齐全；
- Git 变更严格限制在授权范围；
- Schema/Entity/迁移/模块/导航/扫码/库存均未改动；
- 11 张休眠表 0 处新引用；
- 风险登记册已更新。

**状态为 `Review`，等待项目负责人审查；不自动关闭 MB-D-007，不进入 Stage 4。**

---

## 18. 停止声明

**未进入 Stage 4。**

- 未开发库存业务、库存流水、采购清单、计算记录 UI；
- 未开发/修改 Compose 页面、导航、视觉定稿、扫码 UI；
- 未新建 `feature:scan` 或任何模块；
- 未修改 Schema 版本、Entity、表结构、迁移、数据库文件；
- 未升级/更换技术栈，未升级 Room；
- 未使用 destructive migration、未清库、未删除现场；
- 未移动 `framework-0.1.0` 或任何既有 checkpoint 标签；
- 未启用 CI、未做真机验收、未创建签名（MB-D-010/011/012 仍 `PROPOSED`）。

---

## 19. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0.0 | 2026-11-04 | MB-BK-001 执行报告：六项事项、测试、验证、Git、风险与停止声明 | 接管工程组 |
