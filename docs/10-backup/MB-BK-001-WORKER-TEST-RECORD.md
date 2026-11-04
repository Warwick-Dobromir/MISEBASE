# MB-BK-001 Worker 单元测试与返回语义记录

**文档编号**：MB-BK-001-WORKER
**版本**：`1.0.0`
**状态**：`Review`
**日期**：2026-11-04
**任务书**：MB-BK-001（MB-D-007 事项 3、5）
**设计**：[备份一致性设计](MB-BK-001-BACKUP-CONSISTENCY-DESIGN.md) §4
**测试环境**：JVM 单元测试 + Robolectric 4.17（SDK 34）、WorkManager 2.10.5

---

## 1. 返回语义

| 实际结果 | Worker 返回 | 输出 |
|---|---|---|
| 归档存在 + `BackupArchiveVerifier` 通过 + `backup_record` 写入成功 | `Result.success` | `backup_id`、`archive_path`、`record_id` |
| 空间不足、临时 IO、checkpoint busy、记录写入暂时失败 | `Result.retry` | 无（WorkManager 2.10 API 限制） |
| 权限拒绝、归档校验失败、写屏障超时、快照校验失败 | `Result.failure` | `backup_id`、`reason` |
| 协程取消 | `Result.failure` | `backup_id`、`reason=cancelled`；临时文件已清理 |

成功条件在 `AutoBackupRunnerImpl` 内部按顺序判定；归档先写临时文件、校验通过后才原子 rename，`backup_record` 最后写入。记录失败时删除最终归档，避免“有归档无记录”。

## 2. Worker 单测

命令：

```powershell
.\gradlew.bat :core:platform:testDebugUnitTest --tests "com.culinary.core.platform.work.AutoBackupWorkerTest" --console=plain --no-daemon
```

| 场景 | 测试 | 断言 | 结果 |
|---|---|---|---|
| 正常成功 | `returns success only with archive path and record id` | `Result.Success`；三个输出字段齐全；输入 backup_id 传给运行器 | PASS |
| 输入路径 | `passes the target directory from input data` | `AutoBackupRequest("backup-2", "/tmp/target")` | PASS |
| 可重试 | `maps a retryable result to retry` | `Result.Retry` | PASS |
| 不可重试 | `maps a non-retryable result to failure with a reason` | `Result.Failure`；reason=permission_denied；backup_id 回传 | PASS |
| 取消 | `maps cancellation to failure and never success` | `Result.Failure`；reason=cancelled；不返回 success | PASS |
| 依赖不可解析 | `fails when the runner cannot be resolved` | `Result.Failure`；reason=runner_unavailable | PASS |

## 3. 编排层失败/取消测试

命令：

```powershell
.\gradlew.bat :core:data:testDebugUnitTest --tests "com.culinary.core.data.backup.AutoBackupRunnerImplTest" --console=plain --no-daemon
```

| 场景 | 测试 | 断言 | 结果 |
|---|---|---|---|
| 成功 | `writes a verified archive and a backup record on success` | 归档存在；verifier PASS；归档内 3 行；`backup_record` 1 条且 `VERIFIED`；屏障释放 | PASS |
| 归档校验失败 | `rejects an archive that fails verification and writes no record` | `Failure(archive_verification_failed)`；0 记录；目标目录无归档；屏障释放 | PASS |
| 空间不足 | `returns retry when the target has insufficient space` | `Retry(insufficient_storage)`；0 记录；无归档 | PASS |
| 权限拒绝 | `returns failure on permission denied` | `Failure(permission_denied)`；0 记录；无归档 | PASS |
| 写屏障超时 | `returns failure when the write barrier times out` | `Failure(write_barrier_timeout)`；0 记录 | PASS |
| 取消（归档临时文件阶段） | `cleans temporary files and writes no record when cancelled` | 抛 `CancellationException`；0 记录；无归档/无快照残留；原库可读 | PASS |
| 取消（已 rename 后） | `removes the renamed archive when cancelled before the record write` | 抛 `CancellationException`；最终归档被删除；0 记录 | PASS |
| 记录写入失败 | `deletes the archive when the backup record cannot be written` | `Retry(backup_record_write_failed)`；归档删除；0 记录 | PASS |

## 4. Scheduler → Worker 集成

命令：

```powershell
.\gradlew.bat :core:platform:testDebugUnitTest --tests "com.culinary.core.platform.work.SchedulerWorkerIntegrationTest" --console=plain --no-daemon
```

- `WorkManagerBackupScheduler.enqueueImmediateBackup(backupId, targetDirectory)` 入队；
- 测试 WorkManager 同步执行 Worker；
- 断言 `WorkInfo.State.SUCCEEDED`、输出 backup_id/record_id、归档存在；
- 归档结构校验：`manifest.json`、`manifest.sha256`、`checksums.sha256`、`database/culinary.db` 齐全；manifest hash、checksums hash、DB hash 匹配；manifest `backup_id` 与入队一致，`integrity.status=VERIFIED`。

> 生产 `BackupArchiveVerifier` 的完整校验在 `core:data` 测试中覆盖；平台集成测试只验证调度链路真实执行与归档结构，避免跨层测试依赖。

## 5. 结论

- Worker 不再固定 `success`；无“已入队=成功”的替代。
- 失败不写成功记录；取消清理临时文件。
- **PASS**。
- 限制：设置页/生产自动备份触发点尚未接线；本任务只保证 Worker 被调度时返回真实结果。

## 6. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0.0 | 2026-11-04 | 记录 Worker 返回语义、单测、编排失败/取消与 Scheduler 集成 | 接管工程组 |
