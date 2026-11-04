# MB-S1-06 备份/恢复测试缺口与修复方案

**文档编号**：MB-S1-06
**版本**：`0.1.0`
**状态**：`Review`（分析/测试/方案；**未修改实现**）
**日期**：2026-11-04
**依据**：MB-D-007 **仍为 `PROPOSED`**；S0-04 S0-R-02 / S0-R-10 / S0-R-11
**边界**：本文件不修改 `AutoBackupWorker` 返回逻辑、`BackupArchiveWriter`、`RestoreExecutor`、
写屏障、WAL checkpoint 或任何备份/恢复行为；只做缺口分析、测试方案、修复方案与验证方案。

> **结论（审查补记）**：备份/恢复**未修复，仅分析与方案**。S0-R-02（WAL/备份一致性）与
> S0-R-11（自动备份假成功）仍为 **OPEN（高）**；S0-R-10（恢复中断）仍为 PARTIAL。
> 本阶段没有实施写屏障、checkpoint、Worker 编排或恢复原子性改造。

---

## 1. 现状（代码事实）

| 组件 | 位置 | 当前行为 | 与本任务关系 |
|---|---|---|---|
| `BackupArchiveWriter` | `core/data/.../backup/` | `databaseFile.readBytes()` 后直接打包；写 manifest/checksums；不 checkpoint、不加锁、不 `quick_check` | **不改** |
| `BackupArchiveVerifier` | 同上 | 校验 ZIP/manifest/checksums/大小/zip-bomb；不校验“与活库逻辑一致” | **不改** |
| `RestoreExecutor` | 同上 | 关连接 → 回滚副本 → 删 sidecar → 原子临时文件 + `copyTo` → `integrity_check` → journal | **不改** |
| `AutoBackupWorker` | `core/platform/.../work/` | `doWork()` 固定 `Result.success()`，未编排备份 | **不改** |
| `WorkManagerBackupScheduler` | 同上 | 周期/一次性入队；生产代码无调用方 | **不改** |
| `BackupStoragePort` / `SafBackupStorage` | domain/platform | SAF 输出写入，接口不暴露 `Uri` | **不改** |

**缺失组件**：写屏障/`WriteGate`、`wal_checkpoint(TRUNCATE)` 调用、备份编排器、
自动备份结果与 `backup_record` 的绑定、活跃 WAL 备份/恢复测试、附件恢复。

## 2. 测试覆盖盘点（现有）

| 测试类 | 例数 | 覆盖 | 未覆盖 |
|---|---:|---|---|
| `BackupArchiveTest` | 4 | 归档写入/校验、空附件、篡改 DB、缺 manifest | WAL、并发写、checkpoint、活库一致性 |
| `BackupRoundTripTest` | 2 | TRUNCATE 日志模式下的空库/有数据 round-trip | **WAL 模式**、未 checkpoint 帧、并发写 |
| `DatabaseRestoreDrillTest` | 2 | 备份恢复数据完整、非 SQLite 拒绝 | 活跃 WAL、附件、进程中断 |
| `RestoreExecutorTest` | 3 | 关连接/清 sidecar/装库/校验、失败回滚、journal 中断恢复 | **真实杀进程**、WAL 帧、目录 fsync、回滚失败 |
| `RestoreJournalTest` | 5 | journal 原子写/损坏/清理 | 与真实恢复交叠的崩溃点 |
| `RestorePrecheckTest` | 7 | schema/格式/PARTIAL 预检 | 迁移路径真实执行 |
| `WorkManagerBackupSchedulerTest` | 3 | 入队/取消/唯一工作 | **Worker 执行结果**、备份产物、失败重试 |

> 现有 3 个“恢复演练”类测试均在 `JournalMode.TRUNCATE` 下建库；`RestoreExecutorTest` 只写入
> 字符串 `stale` 到 `-wal`，并未产生真实 WAL 帧。因此“活跃 WAL 场景”仍未验证。

## 3. S0-R-02：WAL 与备份一致性

### 3.1 缺口

| # | 缺口 | 证据 | 风险 |
|---|---|---|---|
| R02-G1 | 备份不执行 `wal_checkpoint(TRUNCATE)` | `BackupArchiveWriter.write` 只 `readBytes()` | 已提交事务留在 WAL，备份缺数据 |
| R02-G2 | 备份无写屏障/写锁 | 代码中不存在 `WriteGate` | 备份期间并发写导致页撕裂/跨事务快照 |
| R02-G3 | 测试全是 TRUNCATE | `BackupRoundTripTest` / `DatabaseRestoreDrillTest` | WAL 场景零覆盖 |
| R02-G4 | 无并发写 + 备份测试 | 无相关测试 | 无法证明备份是一致快照 |
| R02-G5 | 无备份前 `quick_check` | Writer 无检查 | 损坏库被当作好备份 |
| R02-G6 | 无“备份内容 = 活库逻辑状态”断言 | Verifier 只校验归档完整性 | 自洽但过期的备份仍 PASS |
| R02-G7 | 无进程中断备份测试 | 无 | 留下半写归档/临时文件 |

### 3.2 修复方案（MB-D-007 签收后，另立任务）

1. 增加 `WriteGate`（或等价单写者屏障）：备份/恢复期间暂停业务写，读事务可继续；
2. 备份顺序：获取写屏障 → `PRAGMA wal_checkpoint(TRUNCATE)` → `PRAGMA quick_check` →
   读取 DB 文件（或使用 SQLite backup API/在线快照）→ 校验 → 释放；
3. 备份完成后计算归档 SHA-256，并记录 `backup_record`（状态、字节数、时间、schema）；
4. 备份失败/被中断：保留诊断信息，不把半成品标记为成功；
5. 不改变 E05 归档格式；不引入 destructive 操作。

### 3.3 验证方案

| 测试 ID | 场景 | 断言 |
|---|---|---|
| R02-T1 | WAL 模式写入并提交后立即备份 | 备份恢复后能看到最后一条已提交记录 |
| R02-T2 | 备份前有未 checkpoint 的 WAL 帧 | checkpoint 后 WAL 为空或已截断；备份含帧 |
| R02-T3 | 20 协程并发写 + 备份 | 备份是单一逻辑时点；无重复/缺失事务 |
| R02-T4 | 备份过程中杀进程 | 无“成功”记录；临时文件可清理；原库可打开 |
| R02-T5 | 损坏 DB + `quick_check` | 备份被拒绝并报错，不生成 VERIFIED 归档 |
| R02-T6 | 备份后修改活库 | 恢复备份得到备份时点状态，不是最新状态 |

## 4. S0-R-10：恢复中断/损坏

### 4.1 缺口

| # | 缺口 | 证据 | 风险 |
|---|---|---|---|
| R10-G1 | 活跃 WAL 恢复未验证 | 测试用 TRUNCATE；`stale` 不是真实 WAL | 恢复后缺已提交事务或 WAL 重放错误 |
| R10-G2 | 无真实进程 kill | `afterInstallHook` 是异常注入 | 无法覆盖断电/被杀的文件系统状态 |
| R10-G3 | 最终装库不是原子 rename | `temporary.copyTo(databaseFile)` | 复制中途崩溃可能留下半文件 |
| R10-G4 | 未 fsync 父目录 | 仅 `fd.sync()` 临时文件 | rename/目录项可能未持久化 |
| R10-G5 | 附件未随恢复 | 只接收 DB bytes | 图片丢失/引用悬空 |
| R10-G6 | 回滚失败路径未测 | 无测试 | `RECOVERY_REQUIRED` 后无操作手册 |
| R10-G7 | 恢复 UI 未接线 | G2-U05 | 用户无法完成恢复 |
| R10-G8 | 磁盘满/权限失败未测 | 无 | 恢复中途失败可能不可回滚 |

### 4.2 修复方案（MB-D-007 签收后）

1. 保持“关连接 → 回滚副本 → 装库 → `integrity_check` → journal”的顺序；
2. 将最终装库改为“同目录临时文件 + fsync + 原子 rename + fsync 目录”，失败不覆盖原库；
3. 恢复前对当前库做一次可用的回滚副本，并校验副本；
4. 附件按 manifest 恢复到私有目录，先 staging 再原子替换；缺失附件不阻塞 DB 恢复但记录诊断；
5. 回滚失败进入 `RECOVERY_REQUIRED`，提供只读诊断页/导出日志，不自动清库；
6. 恢复完成后要求进程重启/重建连接（沿用现有约束）。

### 4.3 验证方案

| 测试 ID | 场景 | 断言 |
|---|---|---|
| R10-T1 | WAL 模式 + 未 checkpoint 的已提交事务，备份后恢复 | 事务保留；`integrity_check=ok` |
| R10-T2 | 恢复在 `DB_INSTALLED` 前 kill | 启动 journal 恢复回滚副本；原库数据完整 |
| R10-T3 | 恢复在 `DB_INSTALLED` 后、`VERIFIED` 前 kill | 启动恢复回到恢复前状态或完成校验，不半可用 |
| R10-T4 | 注入磁盘满/写失败 | 回滚成功；`RECOVERY_REQUIRED` 仅在回滚也失败时出现 |
| R10-T5 | 损坏/截断备份 | 预检拒绝；现场不删除 |
| R10-T6 | 附件缺失/损坏 | DB 恢复成功；附件诊断记录；不阻塞启动 |
| R10-T7 | 恢复后立即冷启动 | 无 WAL/SHM 残留；schema 版本正确 |

## 5. S0-R-11：自动备份“假成功”

### 5.1 缺口

| # | 缺口 | 证据 | 风险 |
|---|---|---|---|
| R11-G1 | Worker 固定返回 success | `AutoBackupWorker.doWork()` | 用户以为有备份，实际没有 |
| R11-G2 | 无 Worker 单测 | 只有 Scheduler 测试 | 无法回归 |
| R11-G3 | 生产无调用方 | 搜不到 `enqueue` 调用 | 自动备份实际未启用 |
| R11-G4 | 无产物/校验断言 | 无 | 无法证明成功 |
| R11-G5 | 失败/重试策略未定义 | 无 | 失败被吞或无限重试 |

### 5.2 修复方案（MB-D-007 签收后）

1. Worker 调用备份编排器（写屏障 + checkpoint + 归档 + 校验 + `backup_record`）；
2. 只有“归档存在 + manifest/checksums 校验通过 + 记录写入成功”才返回 `success`；
3. 可重试失败返回 `retry`，不可重试（权限/空间/损坏）返回 `failure` 并记录；
4. 调度器在应用启动/设置页接线；保留唯一工作名与约束；
5. Worker 输入/输出用 `Data` 传递备份 ID/路径，不传大对象。

### 5.3 验证方案

| 测试 ID | 场景 | 断言 |
|---|---|---|
| R11-T1 | 正常备份 | Worker `success`；归档存在且 `BackupArchiveVerifier` PASS；`backup_record` 写入 |
| R11-T2 | 写入失败 | Worker `retry`/`failure`；无“成功”记录；临时文件清理 |
| R11-T3 | 归档校验失败 | 不返回 success；错误可见 |
| R11-T4 | 空间不足/权限拒绝 | `failure`；不崩溃；诊断记录 |
| R11-T5 | 重复调度 | 唯一工作不重复；幂等键/备份 ID 不重复 |
| R11-T6 | Scheduler → Worker 集成 | 入队后真正执行并产出可验证归档 |

## 6. 执行顺序建议（签收后）

```text
1. 先补测试（WAL/并发/中断/Worker），保持实现不变，确认失败用例复现缺口
2. 再实现 WriteGate + checkpoint + 原子装库 + Worker 编排
3. 每次只改一个行为，测试先行；每步可回档
4. 完成后重跑 R02/R10/R11 全矩阵 + 真机恢复演练
```

## 7. 本阶段未做（明确）

- 未修改任何备份/恢复实现文件；`git diff` 中不应出现：
  `core/data/**/backup/*.kt`、`core/platform/**/work/*.kt`；
- 未新增备份/恢复测试（避免在未签收时改变行为）；
- 未把 `AutoBackupWorker` 的 `success` 改为 `failure`/`retry`；
- 未实现写屏障或 checkpoint；
- 未把备份标记为“一致备份”。

## 8. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 0.1.0 | 2026-11-04 | 盘点 S0-R-02/R-10/R-11 的代码事实、测试缺口、修复与验证方案；未改实现 | 接管工程组 |
