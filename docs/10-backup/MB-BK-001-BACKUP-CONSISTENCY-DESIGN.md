# MB-BK-001 备份一致性修复设计说明

**文档编号**：MB-BK-001-DESIGN
**版本**：`1.0.0`
**状态**：`Review`
**日期**：2026-11-04
**任务书**：MB-BK-001（MB-D-007 备份一致性修复）
**依据**：[MB-D-007 授权](MB-D-007-AUTHORIZATION.md)、[E05 备份/恢复](../03-engineering/E05-BACKUP-RESTORE.md)、[E02 数据库并发](../03-engineering/E02-DATABASE-CONCURRENCY.md)、[S0-04 风险登记册](../05-handover/S0-04-RISK-REGISTER.md)
**边界**：本文件只描述备份一致性修复。不改 Schema/Entity/表结构/迁移，不开发 Compose 页面、导航、扫码 UI、库存业务，不新建模块，不进入 Stage 4。

---

## 1. 问题与目标

当前实现的四个缺口（来自 MB-S1-06 与 S0-R-02/R-10/R-11）：

| 缺口 | 现状 | 目标 |
|---|---|---|
| 写屏障 | 无；备份与业务写可并发 | 备份/恢复期间暂停业务写，读继续；写排队 |
| WAL checkpoint | 备份直接 `databaseFile.readBytes()` | 屏障内 `wal_checkpoint(TRUNCATE)` → `quick_check` → 复制 → 校验 → 释放 |
| Worker 真实返回 | `doWork()` 固定 `Result.success()` | 只有归档存在且校验通过且 `backup_record` 写入成功才 success |
| 恢复原子性 | 临时文件 `copyTo` 覆盖；无目录 fsync；回滚副本不校验 | 同目录临时文件 + fsync + 原子 rename + fsync 目录；回滚副本先校验 |

总体执行顺序（自动备份）：

```text
获取写屏障（超时即失败）
  → PRAGMA wal_checkpoint(TRUNCATE)，busy/未截断即失败
  → PRAGMA quick_check = ok
  → 复制主库到快照临时文件 + fsync
  → 用只读连接校验快照（quick_check / user_version / journal_mode）
  → 释放写屏障
  → 写 ZIP 临时文件 + fsync + 原子 rename
  → BackupArchiveVerifier 校验归档
  → 写 backup_record（经 TransactionRunner）
  → 返回 success
```

失败任一步：删除临时文件；不写成功记录；返回 retry 或 failure。

---

## 2. 写屏障（Write Barrier）

### 2.1 单写者语义

- 全应用只有**一把**写互斥量：`kotlinx.coroutines.sync.Mutex`，由 `WriteBarrier` 持有。
- 业务写路径：`RoomTransactionRunner.write` → `WriteBarrier.withWrite` → 同一把 `Mutex` → Room `withTransaction`。
- 备份路径：`WriteBarrier.acquireForBackup` → 同一把 `Mutex` 的 `tryLock` 轮询。
- **不新增第二把写锁**，不依赖 `PRAGMA locking_mode=EXCLUSIVE`，不改 WAL 模式，不改 Schema。
- 读事务不经过 `WriteBarrier`，备份/恢复期间读继续。

`RoomTransactionRunner` 的锁顺序保持固定：`写屏障（Mutex）→ SQLite 事务 → 行更新`。非可重入：事务块内再次调用 `write` 仍会死锁，调用方必须避免嵌套；备份代码在持有屏障期间**禁止**调用任何写入口（包括 `TransactionRunner.write` 与 DAO 写）。

### 2.2 获取/释放/超时

`WriteBarrier` 的公开语义：

| 操作 | 行为 |
|---|---|
| `withWrite(block)` | 排队获取 `Mutex`；成功后计数 `activeWriters`；执行块；释放 |
| `acquireForBackup(timeout)` | 以 `tryLock` 轮询获取；获取成功置 `barrierHeld=true`，返回 `ACQUIRED` |
| 超时 | 到 deadline 仍未获取，返回 `TIMEOUT`；**不继续备份** |
| 嵌套/并发获取 | 屏障已持有（同一或另一协程）时返回 `ALREADY_HELD`，拒绝嵌套 |
| `releaseForBackup()` | 仅当 `barrierHeld` 为 true 时解锁并复位；未持有时返回 false，不重复解锁 |

默认超时 5 秒，轮询 25 ms；超时值由调用方（快照器/恢复执行器）显式传入，便于测试。获取屏障失败返回明确的 `Outcome.Failure`（`CUL-BACKUP-003`，context `reason=write_barrier_timeout`），不静默继续。

### 2.3 备份期间的排队行为

- 备份持有 `Mutex` 时，新业务写阻塞在 `withWrite`，不丢写、不报错、不降级为“尽量避让”。
- 屏障持有时间覆盖“checkpoint + 复制 + 快照校验”；之后立即释放，ZIP 打包/归档校验/记录写入在屏障外执行。
- `backup_record` 必须在屏障释放后写入；否则会与屏障自锁。写记录经 `TransactionRunner`，仍受同一把 `Mutex` 串行化。
- 排队数/活动写数写入诊断日志（`write_barrier` 事件），便于验证“备份期间写确实排队”。

### 2.4 失败释放与进程被杀

- 任何异常/取消都通过 `try/finally` 释放屏障；释放失败只记录诊断，不吞掉原始错误。
- 屏障是**进程内内存状态**（`Mutex` + `AtomicBoolean`），不写盘、不建表、不持久化。进程被杀后屏障随进程消失；重启后新的 `WriteBarrier` 从“未持有”开始，不会留下永久锁。
- 进程被杀可能留下快照临时文件/半写 ZIP；启动时不需要“解锁”，但不得存在成功记录（成功记录在最后一步才写）。

### 2.5 诊断与测试

- 诊断字段：`active_writers`、`queued_writers`、`barrier_held`、`wait_ms`、`result`。
- 测试覆盖：正常获取/释放；持有写时备份超时；备份失败后屏障释放；嵌套获取拒绝；20 协程并发写仍串行。

---

## 3. WAL checkpoint 与一致性快照

### 3.1 时机

在**写屏障内**、读取数据库文件**之前**执行：

1. 获取写屏障（等待当前写事务结束）；
2. `PRAGMA wal_checkpoint(TRUNCATE)`；
3. 读取返回三元组 `(busy, log, checkpointed)`；
4. `busy == 0` 且 `log == checkpointed` 才继续；否则立即失败并释放屏障。

不等待读连接、不重试到“看起来成功”、不跳过 checkpoint。checkpoint 失败返回 `CUL-BACKUP-003`，context 记录三元组，Worker 映射为 `retry`（读连接是瞬态的）。

### 3.2 顺序

```text
屏障 → wal_checkpoint(TRUNCATE) → quick_check → 复制 DB → 校验快照 → 释放屏障
```

- `quick_check` 在活库上执行；失败则不复制、不生成归档。
- 复制**只复制主库文件**，不复制活动 `-wal`/`-shm`；checkpoint 已把已提交页落主库。
- 快照复制使用同目录/临时目录文件 + `fd.sync()`；复制后用独立只读连接校验：
  - `PRAGMA quick_check` = `ok`；
  - `PRAGMA user_version` 与活库一致；
  - 记录 `PRAGMA journal_mode` 供诊断。
- 快照校验通过后才释放屏障；释放后不再读写该临时文件与活库混用。
- 不修改 WAL 模式、不关闭 WAL、不做 destructive 操作。

### 3.3 备份文件包含什么

- 包含：checkpoint 后主库中的**已提交事务**。
- 不包含：未提交事务、活动 WAL 帧、`-shm` 内容。
- 备份是一致性快照：备份期间业务写被屏障暂停，快照对应单一逻辑时点；读可继续，不影响快照。

### 3.4 源库健康检查

- checkpoint 后、释放前 `quick_check` 通过；
- 快照只读打开成功；
- 释放后业务写可继续（由屏障释放保证）。

---

## 4. AutoBackupWorker 返回语义

### 4.1 输入/输出

Worker 只用 `Data` 传标量：

| 方向 | Key | 含义 |
|---|---|---|
| 输入 | `backup_id` | 可选；缺省由编排器生成 UUID |
| 输入 | `target_directory` | 可选；绝对路径；缺省用应用外部私有目录 |
| 输出 | `backup_id` | 本次备份 ID |
| 输出 | `archive_path` | 成功时的归档绝对路径 |
| 输出 | `record_id` | 成功时的 `backup_record.id` |
| 输出 | `reason` | 失败/重试原因码 |

不传 DB 字节、ZIP 字节或其他大对象。

### 4.2 状态映射

| 实际结果 | Worker 返回 | 条件 |
|---|---|---|
| 成功 | `Result.success` + 输出 Data | 归档存在、`BackupArchiveVerifier` 通过、`backup_record` 写入成功 |
| 可重试 | `Result.retry` | 空间不足、临时 IO、checkpoint busy、记录写入暂时失败 |
| 不可重试 | `Result.failure` + 输出 Data | 权限拒绝、归档校验失败、写屏障超时、快照校验失败、配置/路径非法 |
| 取消 | `Result.failure`（reason=cancelled） | 协程取消；清理临时文件；不写成功记录 |

“已入队/已调度”不是成功；只有上述成功条件全部满足才 success。

### 4.3 编排与记录

- Worker 调用 domain 端口 `AutoBackupRunner`（实现位于 `core:data/backup`），由 Hilt `EntryPoint` 解析；测试通过显式注入的测试运行器替换。
- 归档写到目标目录的临时文件（`.<name>.tmp`），fsync 后原子 rename 为最终名；成功记录写入前不 rename 为“成功”可发现状态的说法不成立——**最终名只在归档校验通过后出现**。
- `backup_record` 通过 `TransactionRunner` 写入，状态 `VERIFIED`，记录归档 SHA-256、大小、schema 版本、时间、`file_uri`。
- 记录写入失败：删除最终归档，返回 `retry`/`failure`，不留“有归档无记录”的假成功。
- 取消/异常：删除临时归档与快照；最终归档若已 rename 但记录未写，也删除（成功记录是提交点）。

### 4.4 目标目录

未配置 SAF 时使用 E05 §9.2 优先级 2：

```text
getExternalFilesDir(DIRECTORY_DOCUMENTS)/CULINARY/Backups
```

不可用时回退 `filesDir/backups`。SAF 用户目录选择属于设置页/后续任务，本次不新增 UI；Worker 的 `target_directory` 输入为未来接线保留。

---

## 5. 恢复原子性

### 5.1 顺序

```text
校验输入/未完成 journal
  → 获取写屏障（超时失败）
  → wal_checkpoint(TRUNCATE) + 关闭 Room
  → 当前 DB 复制到 rollback/<restoreId>.db
  → 校验 rollback 副本（SQLite 头 + quick_check + foreign_key_check）
  → 删除 -wal/-shm
  → 同目录临时文件写入新库 + fsync + 原子 rename + fsync 目录
  → 打开新库：integrity_check + foreign_key_check
  → journal VERIFIED → DONE
  → 释放屏障
```

失败：

```text
→ 用 rollback 副本原子替换回原库（同目录临时文件 + fsync + rename）
→ 校验回滚库
→ 成功：journal ROLLED_BACK
→ 失败：journal RECOVERY_REQUIRED，保留 rollback/staging/现场，不自动清库
```

### 5.2 原子装库

- 临时文件必须与目标库**同目录**，避免跨文件系统 rename 退化为复制。
- 写入后 `flush` + `FileDescriptor.sync()`，再 `Files.move(..., ATOMIC_MOVE)`；不支持原子移动时降级为普通 move，并在诊断中记录。
- rename 后对父目录执行 best-effort fsync（`android.system.Os.open` + `Os.fsync`），失败只记录，不影响已完成的原子替换。
- 失败时原库在 rename 前不被覆盖；临时文件删除。

### 5.3 回滚副本

- 恢复前必须 checkpoint（避免 WAL 中已提交事务未进主库）并关闭连接，再复制。
- 复制后立即校验：SQLite 文件头、`quick_check`、`foreign_key_check`；副本不可用则**不开始装库**。
- 回滚也使用原子替换，不 `copyTo` 原地覆盖。
- 回滚成功记 `ROLLED_BACK`；回滚失败记 `RECOVERY_REQUIRED`，保留现场，UI/诊断进入 Recovery，不自动删库、不 destructive。

### 5.4 中断恢复

- `restore.journal` 仍用“临时文件 + fsync + 原子 rename”，阶段推进先写 journal 再做文件操作。
- 启动 `recoverIfInterrupted()`：未完成 journal + rollback 副本存在 → 原子回滚；rollback 不存在或校验失败 → 不猜、不删，保持现场并报告。
- 恢复完成后连接保持关闭，调用方必须重启进程/重建 `CulinaryDatabase`（沿用现有约束）。

### 5.5 附件

当前自动备份/恢复**未接附件恢复**（`RestoreExecutor` 只接收 DB 字节）。本次不新增附件恢复实现；设计保留“先 staging 再原子替换、缺失附件不阻塞 DB 恢复但记录诊断”的约束，待附件接线任务执行。该限制写入风险与验证记录。

---

## 6. 测试矩阵

| 编号 | 场景 | 期望 |
|---|---|---|
| WB-1 | 正常获取/释放 | 写可继续；`barrierHeld=false` |
| WB-2 | 写持有期间备份超时 | `TIMEOUT`；不继续；错误明确 |
| WB-3 | 备份失败 | `finally` 释放；后续写成功 |
| WB-4 | 嵌套获取 | `ALREADY_HELD`；不二次解锁 |
| CK-1 | WAL 有未 checkpoint 已提交事务 | checkpoint 成功；快照含该事务 |
| CK-2 | checkpoint busy | 失败；不生成归档 |
| WK-1 | 正常备份 | success；归档存在；verifier 通过；record 写入 |
| WK-2 | 归档校验失败 | 非 success；无成功记录；临时文件清理 |
| WK-3 | 空间不足 | retry |
| WK-4 | 权限拒绝 | failure |
| WK-5 | 写屏障超时 | failure |
| WK-6 | 取消 | 清理临时文件；无成功记录 |
| WK-7 | Scheduler → Worker | 入队后真实执行并产出可校验归档 |
| RS-1 | 正常恢复 | 原子安装；数据正确；journal DONE |
| RS-2 | 装库后失败 | 回滚到恢复前库；`ROLLED_BACK` |
| RS-3 | 回滚失败 | `RECOVERY_REQUIRED`；现场保留 |
| WAL-1 | 未 checkpoint 事务备份→恢复→读取 | 最后已提交事务存在 |
| WAL-2 | 恢复后完整性 | `quick_check`、`foreign_key_check` 通过 |
| INT-1 | 备份中断 | 无成功记录；临时文件清理；原库可打开 |
| INT-2 | 恢复中断 | journal 回滚；原库可打开；无半恢复 |
| CON-1 | 并发写 + 备份 | 单一逻辑时点；无重复/缺失事务 |

---

## 7. 明确不改变

- Schema 版本、Entity 字段、表结构、DAO 查询语义、迁移、数据库文件布局；
- 备份格式（manifest/checksums/ZIP 路径）与 `format_version`；
- WAL 模式；
- 模块集（13）、导航、Compose 页面、扫码 UI；
- 11 张休眠表：0 处新业务引用；
- `framework-0.1.0` 与既有 checkpoint 标签。

---

## 8. 已知限制与风险

| 项 | 说明 | 处理 |
|---|---|---|
| 附件未随自动备份/恢复 | 当前只备份 DB；图片附件恢复未接线 | 记录为限制；不声称完整附件恢复 |
| SAF 目标未接线 | 自动备份使用外部私有目录回退 | 设置页/SAF 属后续任务 |
| 目录 fsync 平台差异 | JVM/部分文件系统不支持目录 fsync | best-effort，记录诊断；原子 rename 已保证目标不半写 |
| 进程被杀后残留临时文件 | 无启动清理 | 残留不影响成功判定；记录风险 |
| `StartupCoordinator` 期望版本仍为 1 | 与 Schema v2 不一致（既有缺陷，非本次授权范围） | 不修改；在报告“未解决问题”中列出，要求单独授权 |

---

## 9. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0.0 | 2026-11-04 | 建立写屏障、WAL checkpoint、Worker 返回语义、恢复原子性设计与测试矩阵 | 接管工程组 |
