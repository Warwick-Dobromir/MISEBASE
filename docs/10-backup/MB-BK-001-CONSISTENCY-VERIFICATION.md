# MB-BK-001 备份恢复一致性验证记录

**文档编号**：MB-BK-001-CONSISTENCY
**版本**：`1.0.0`
**状态**：`Review`
**日期**：2026-11-04
**任务书**：MB-BK-001（MB-D-007 事项 1、2、4、6）
**设计**：[备份一致性设计](MB-BK-001-BACKUP-CONSISTENCY-DESIGN.md)
**测试环境**：JVM + Robolectric 4.17（SDK 34）、Android SQLite、Windows 11

---

## 1. 验证链

```text
WAL 活库（未 checkpoint 已提交事务）
  → 写屏障 → wal_checkpoint(TRUNCATE) → quick_check
  → 快照复制 + quick_check/user_version
  → 释放屏障
  → E05 归档写入（manifest/checksums/ZIP）
  → BackupArchiveVerifier
  → 恢复（回滚副本校验 + 原子 rename）
  → 只读读取
  → quick_check + foreign_key_check
```

## 2. 证据表

命令：

```powershell
.\gradlew.bat :core:data:testDebugUnitTest --tests "com.culinary.core.data.backup.WalBackupRestoreTest" --tests "com.culinary.core.data.backup.BackupConcurrencyTest" --tests "com.culinary.core.data.backup.AutoBackupRunnerImplTest" --console=plain --no-daemon
```

| 验证项 | 测试 | 关键断言 | 结果 |
|---|---|---|---|
| WAL 已提交事务进入备份 | `WalBackupRestoreTest.backs up a committed WAL transaction and restores it intact` | 备份前 `-wal` 有帧；checkpoint 后 `-wal` 长度 0；归档 verifier PASS；恢复后含最后一条已提交记录 | PASS |
| 备份文件完整 | 同上 | manifest/checksums/DB 哈希全部匹配 | PASS |
| 恢复后完整性 | 同上 | `quick_check=ok`；`foreign_key_check` 0 行；无 `-wal`/`-shm` 残留 | PASS |
| 事务批次不撕裂 | `WalBackupRestoreTest.restored WAL snapshot keeps the last committed batch after a checkpoint` | 恢复库中批次恰好 2 行 | PASS |
| 并发写 + 备份单一逻辑时点 | `BackupConcurrencyTest.concurrent writes are captured as a consistent logical point` | 快照中每个批次 0 或 2 行；总数偶数；无半事务；活库最终 80 行；记录 1 条；屏障释放 | PASS |
| 成功条件 | `AutoBackupRunnerImplTest.writes a verified archive and a backup record on success` | 归档 + verifier + `backup_record(VERIFIED)` 三者同时成立 | PASS |
| 归档内容与活库一致 | 同上 | 归档内 DB 的 `ingredient` 计数 = 3 | PASS |

## 3. 并发测试设计说明

- 40 个批次，每批在**同一事务**内插入 2 行（`batch-N-a`、`batch-N-b`）。
- 40 个写协程与 1 个备份协程并发；写全部经 `RoomTransactionRunner`，与备份共享同一 `WriteBarrier`。
- 断言不看“是否恰好捕获到全部 40 批”，而看**一致性**：快照内每个批次要么两行都在、要么都不在；不存在只含一半的事务。
- 结果：PASS。该断言能捕获“备份期间并发写导致跨事务快照”的缺陷。

## 4. 未覆盖/限制

- 真机、真实断电/进程 kill 不在本记录范围（MB-D-011 `PROPOSED`）。
- 附件（图片）未随自动备份/恢复接线；本记录仅覆盖 DB。
- `backup_record` 使用 `VERIFIED` 状态；恢复 UI 尚未接线。

## 5. 结论

备份 → 校验 → 恢复 → 读取链路在 WAL 与并发写场景下可复现通过；一致性结论**成立（JVM 范围内）**。

## 6. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0.0 | 2026-11-04 | 记录 WAL/并发/成功条件的备份恢复一致性证据 | 接管工程组 |
