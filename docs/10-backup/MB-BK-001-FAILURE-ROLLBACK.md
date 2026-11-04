# MB-BK-001 失败与回滚验证记录

**文档编号**：MB-BK-001-FAILURE
**版本**：`1.0.0`
**状态**：`Review`
**日期**：2026-11-04
**任务书**：MB-BK-001（MB-D-007 事项 1、3、4、5）
**设计**：[备份一致性设计](MB-BK-001-BACKUP-CONSISTENCY-DESIGN.md) §2、§4、§5
**测试环境**：JVM + Robolectric 4.17（SDK 34）

---

## 1. 备份失败

| 失败注入 | 期望 | 实际测试 | 结果 |
|---|---|---|---|
| 写屏障超时 | `failure(write_barrier_timeout)`；无记录；屏障释放 | `AutoBackupRunnerImplTest.returns failure when the write barrier times out` | PASS |
| 目标空间不足 | `retry(insufficient_storage)`；无记录；无归档 | `...returns retry when the target has insufficient space` | PASS |
| 权限拒绝 | `failure(permission_denied)`；无记录；无归档 | `...returns failure on permission denied` | PASS |
| 归档被破坏 | `failure(archive_verification_failed)`；无记录；临时文件清理 | `...rejects an archive that fails verification and writes no record` | PASS |
| 取消（打包阶段） | 抛 `CancellationException`；无临时/快照残留；无记录；原库可读 | `...cleans temporary files and writes no record when cancelled` | PASS |
| 取消（已 rename、未记录） | 最终归档删除；无记录 | `...removes the renamed archive when cancelled before the record write` | PASS |
| 记录写入失败 | `retry(backup_record_write_failed)`；归档删除；无记录 | `...deletes the archive when the backup record cannot be written` | PASS |

共同不变量：

1. 未完成/失败的备份不产生 `backup_record` 成功记录；
2. 归档只有在校验通过后才以最终名出现；
3. 临时文件/快照在 `finally` 中清理；
4. 屏障在 `finally` 中释放，不因失败永久锁住写入。

## 2. 恢复失败与回滚

命令：

```powershell
.\gradlew.bat :core:data:testDebugUnitTest --tests "com.culinary.core.data.backup.RestoreExecutorTest" --tests "com.culinary.core.data.backup.DatabaseRestoreDrillTest" --console=plain --no-daemon
```

| 场景 | 期望 | 实际测试 | 结果 |
|---|---|---|---|
| 装库后校验失败 | 回滚到恢复前库；journal `ROLLED_BACK` | `RestoreExecutorTest.rolls back to the previous database when the restore fails` | PASS |
| 回滚副本被破坏、回滚失败 | journal `RECOVERY_REQUIRED`；原库文件仍在；不自动清库 | `...enters RECOVERY_REQUIRED when the rollback copy cannot be restored` | PASS |
| 写屏障被占用 | 恢复开始前失败；不写 journal；数据不变 | `...refuses to restore while another writer holds the write barrier` | PASS |
| 恢复中断（journal + rollback） | 启动恢复回滚副本；journal 清理；数据为恢复前 | `...recovers an interrupted restore from the journal and rollback copy` | PASS |
| 非 SQLite 载荷 | 拒绝；原库不变 | `DatabaseRestoreDrillTest.keeps the existing database when the payload is not SQLite` | PASS |
| 恢复后读数据 | 数据完整；原子装库成功 | `DatabaseRestoreDrillTest.restores a backed up database with its data intact` | PASS |

## 3. 恢复原子性实现要点

- 装库：同目录临时文件 → `fd.sync()` → `Files.move(ATOMIC_MOVE)` → best-effort 目录 fsync；目标在 rename 前不被覆盖。
- 回滚同样使用原子替换，不 `copyTo` 原地覆盖。
- 恢复前 checkpoint，避免回滚副本缺少 WAL 中已提交事务。
- 回滚副本在装库前校验（SQLite 头 + `quick_check`），副本不可用即中止。
- 装库后校验 `quick_check` + `foreign_key_check`。
- 回滚失败：`RECOVERY_REQUIRED`，保留现场（不删除 rollback/staging）。

## 4. 限制

- “进程被杀”通过 journal 状态与异常注入模拟，不是真实 kill/断电；真实场景需 MB-D-011 真机验证。
- 附件恢复未接线；本记录只覆盖 DB。
- 目录 fsync 在平台不支持时为 best-effort；原子 rename 仍保证目标不半写。

## 5. 结论

失败路径均有明确结果；成功记录只在实际成功后出现；恢复失败可回滚，回滚失败保留现场并进入 `RECOVERY_REQUIRED`。**PASS（JVM 范围内）**。

## 6. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0.0 | 2026-11-04 | 记录备份失败、取消、恢复回滚与 RECOVERY_REQUIRED 证据 | 接管工程组 |
