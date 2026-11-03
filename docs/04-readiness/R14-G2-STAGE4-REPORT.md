# G2 阶段 4（数据层）完成报告

**文档编号**：R14
**版本**：`1.0.0`
**状态**：`Review`（待项目负责人验收）
**日期**：2026-11-04
**实施依据**：[R01 0.2.2](R01-FRAMEWORK-SEQUENCE.md) §7、E02、E03、E05 0.4.1、A03A §8.1、D-005、D-011、D-015
**回档点**：`baseline/g2-stage3-20261104`

---

## 1. 任务目标

建立数据层：Repository 实现 + Mapper、事务协调、文件/图片存储、备份/恢复骨架、Hilt 绑定与集成测试。

## 2. 交付内容

### 2.1 装配与事务（E02）

| 组件 | 说明 |
|---|---|
| `DataModule` | Hilt 单例：数据库（WAL + 单线程写执行器 + 4 线程读池）、写 `Mutex`、`DispatcherProvider`、`IdGenerator`（UUID v4）、`TransactionRunner` |
| `TransactionRunner` | 端口在 `core:domain`；实现 `RoomTransactionRunner`：`Mutex → Room withTransaction → 行更新`，非可重入 |
| `BusyRetryPolicy` | BUSY/LOCKED 最多 3 次，50/150/400 ms + 抖动，包住整个事务；`CancellationException` 不重试 |
| `DbErrorMapper` | 约束 → `CUL-CON-001`；BUSY → `CUL-DB-001`；损坏 → `CUL-DB-002`；磁盘/IO → `CUL-FILE-001`；其他 → `CUL-UNKNOWN-001` |

### 2.2 Repository 与 Mapper

- `IngredientRepositoryImpl`：列表、读取、保存（新建/更新保留 `createdAt`、`revision+1`）、归档；
- `UnitRepositoryImpl`：单位列表、按 ID 查找、食材级换算；
- 数据库枚举 ↔ 领域枚举**显式穷举映射**，不落默认分支；
- Repository 方法只做原子片段，不自行开事务（E02 §4.3）。

### 2.3 图片/文件存储（D-015）

- 布局 `images/<xx>/<uuid>.<ext>`，数据库只存相对路径与元数据；
- `.part` + `fsync` + rename 原子写，失败不留半张图；
- 绝对路径/`..` 拒绝；文件缺失或不可读返回 `null`，按无图降级。

### 2.4 备份归档（E05 §3-§5）

- `BackupManifest`：E05 必填字段（format/app/device/database/attachments/counts/totals/integrity/warnings/encryption）；
- 生成顺序：DB/附件哈希 → `checksums.sha256` → 其哈希 → `manifest.json` → `manifest.sha256`；**manifest 不含自身哈希**；
- 校验：结构 → manifest 哈希 → 版本/状态 → checksums 哈希 → 逐文件哈希；
- ZIP 防护：条目数上限、总大小上限、拒绝绝对路径/`..`/反斜杠。

### 2.5 恢复骨架（E05 §12-§13）

- `RestoreJournal`：`PREPARED … DONE` 阶段机、`INTENT/DONE` 操作记录；
- `RestoreJournalStore`：临时文件 + `fsync` + 原子 rename；缺失返回 `CUL-NF-001`，损坏返回 `CUL-BACKUP-004`；
- `RestorePrecheck`：`format_version` 受支持、`PARTIAL` 需二次确认、`backup_schema > current` 拒绝、较旧 schema 仅在**已测试迁移路径**存在时允许。

## 3. 测试要求与实际结果

| # | 要求（R01 §7.2） | 结果 |
|---|---|---|
| 1 | 食材 Repository 可保存/读取/归档 | **PASS**（5 例） |
| 2 | 事务回滚（中途异常无残留） | **PASS** |
| 3 | 并发写（20 协程） | **PASS** |
| 4 | 图片写入原子；缺失不阻塞读取 | **PASS**（4 例） |
| 5 | 备份往返（空库 + 有数据） | **PASS**（归档级：空附件与含图片各一例） |
| 6 | 数据库异常映射为错误码 | **PASS** |

**数据层测试：32 例 / 0 失败**

| 测试类 | 数量 | 覆盖 |
|---|---:|---|
| `IngredientRepositoryTest` | 5 | 保存/读取/更新/归档/缺失 |
| `RoomTransactionRunnerTest` | 4 | 提交/回滚/20 并发/约束映射 |
| `BusyRetryPolicyTest` | 3 | 重试成功/耗尽/非 BUSY 不重试 |
| `LocalImageStorageTest` | 4 | 原子写/缺失降级/越界/幂等删除 |
| `BackupArchiveTest` | 4 | 有数据/空附件/篡改/缺 manifest |
| `RestoreJournalTest` | 5 | 往返/原子/缺失/损坏/清理 |
| `RestorePrecheckTest` | 7 | 版本矩阵与 PARTIAL 二次确认 |

全工程门禁（本轮实测）：

```text
.\gradlew.bat test ktlintCheck detekt :app:assembleDebug
BUILD SUCCESSFUL
```

工程测试总数 **133**（契约 24 + 数据库 77 + 数据层 32）。

## 4. 偏差与未覆盖（明确边界）

| 项 | 状态 |
|---|---|
| E05 §6 数据库一致性快照（`ONLINE_BACKUP` / checkpoint） | **未实现**；当前备份写入的是调用方提供的 DB 文件字节。真实快照需要写屏障 + checkpoint，排入阶段 5/9 |
| 备份加密（`.culinary`，AES-GCM） | **未实现**；E05 中为可选能力，D-011 未要求默认加密 |
| 恢复执行器（实际文件移动/回滚） | **未实现**；本阶段交付 journal 状态机与预检，执行编排在阶段 5+（需要关闭数据库与文件替换） |
| `BackupStoragePort` 平台实现（SAF） | 阶段 5 |
| 备份 DB 的 `integrity_check`/`foreign_key_check` | 未接；阶段 9 备份冒烟 |

这些都不属于 R01 阶段 4 的退出条件，但属于 G2 最终质量门禁，已在本文标注，不会当作已完成。

## 5. Git

| 项 | 值 |
|---|---|
| 阶段 3 基线 | `baseline/g2-stage3-20261104` |
| 阶段 4 提交 | `feat(data)` / `feat(backup)` / `feat(file)` / `feat(restore)` |
| 阶段 4 基线标签 | `baseline/g2-stage4-20261104` |

## 6. 结论

R01 §7.2 的 6 项退出条件全部满足；数据层、图片存储、备份归档与恢复预检均为可测试的真实实现，
未用假数据或空壳绕过。偏差项已逐条列出并归属后续阶段。

**请项目负责人验收阶段 4。** 验收通过后进入阶段 5（平台能力：扫码、SAF、WorkManager 最小实现）。

## 7. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0.0 | 2026-11-04 | 记录数据层、图片存储、备份/恢复骨架与 32 例测试 | 工程组 |
