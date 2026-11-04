# MB-BK-001 WAL checkpoint 与 WAL 恢复验证记录

**文档编号**：MB-BK-001-WAL
**版本**：`1.0.0`
**状态**：`Review`
**日期**：2026-11-04
**任务书**：MB-BK-001（MB-D-007 事项 2、6）
**设计**：[备份一致性设计](MB-BK-001-BACKUP-CONSISTENCY-DESIGN.md) §3
**测试环境**：JVM 单元测试 + Robolectric 4.17（SDK 34）、Android 平台 SQLite（WAL 模式）、Windows 11、JDK 21、Gradle 8.14.3
**证据类型**：可复现测试。**不替代** MB-D-011 真机结论。

---

## 1. checkpoint 实现

- 位置：`core/data/.../backup/QuiescedDatabaseSnapshotter.kt`。
- 顺序：写屏障 → `PRAGMA wal_checkpoint(TRUNCATE)` → 读取 `(busy, log, checkpointed)` → `PRAGMA quick_check` → 复制主库 → 校验快照 → 释放屏障。
- `busy != 0` → `checkpoint_busy`，不复制；
- `log != checkpointed` → `checkpoint_incomplete`，不复制；
- 无结果 → `checkpoint_no_result`；
- 查询异常 → `checkpoint_error`。
- 快照只读校验：`quick_check = ok`、`user_version` 与活库一致；不复制 `-wal`/`-shm`。

## 2. 可复现场景

命令：

```powershell
.\gradlew.bat :core:data:testDebugUnitTest --tests "com.culinary.core.data.backup.QuiescedDatabaseSnapshotterTest" --tests "com.culinary.core.data.backup.WalBackupRestoreTest" --console=plain --no-daemon
```

| 场景 | 测试 | 断言 | 结果 |
|---|---|---|---|
| WAL 中有未 checkpoint 的已提交事务 | `QuiescedDatabaseSnapshotterTest.captures a snapshot that includes committed WAL transactions` | `journal_mode=wal`；`-wal` 存在；快照 `user_version=2`；快照内 `ingredient` 计数 = 3；屏障已释放 | PASS |
| checkpoint 后 WAL 截断 | 同上 + `WalBackupRestoreTest.backs up a committed WAL transaction and restores it intact` | 快照后源库 `-wal` 长度 = 0 | PASS |
| checkpoint 返回 busy | `QuiescedDatabaseSnapshotterTest.fails when wal checkpoint reports busy and releases the barrier` | 失败 reason=`checkpoint_busy`；不生成快照；屏障释放 | PASS |
| 写持有屏障导致获取超时 | `QuiescedDatabaseSnapshotterTest.fails when the write barrier times out` | 失败 reason=`write_barrier_timeout`；不继续 | PASS |
| 快照失败后写可继续 | `QuiescedDatabaseSnapshotterTest.a failed snapshot does not block later writes` | 后续 `withWrite` 成功 | PASS |
| 未 checkpoint 事务备份→恢复→读取 | `WalBackupRestoreTest.backs up a committed WAL transaction and restores it intact` | 归档 verifier PASS；恢复库包含“最后一条已提交事务”；`quick_check=ok`；`foreign_key_check` 0 行；恢复后无 `-wal`/`-shm` | PASS |
| 事务批次完整 | `WalBackupRestoreTest.restored WAL snapshot keeps the last committed batch after a checkpoint` | 恢复库中 `批次1-%` 恰好 2 行；`quick_check=ok` | PASS |

## 3. 关键日志字段

`wal_checkpoint` 事件记录 `busy`、`log`、`checkpointed`；`backup_snapshot_ready` 记录 `schema_version`、`journal_mode`、`size_bytes`。失败事件记录明确 reason，不吞掉原因。

## 4. 结论与限制

- **结论**：WAL 场景下备份前 checkpoint 实际执行；已提交事务进入快照；busy 时拒绝继续；恢复后完整性检查通过。**PASS**。
- **限制**：
  - 测试运行在 Robolectric/平台 SQLite 上，不是真机文件系统；断电/真实进程 kill 未覆盖（MB-D-011 未签收）。
  - 附件（图片）未随恢复接线；本记录只针对 DB。
  - checkpoint 严格失败，不做“等待读连接最多 5 秒”的退避；该选择写入设计说明，避免把 busy 误判为成功。

## 5. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0.0 | 2026-11-04 | 记录 checkpoint 实现、WAL 场景、断言与限制 | 接管工程组 |
