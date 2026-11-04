# MB-D-007 备份一致性决策关闭记录

**文档编号**：MB-D-007-CLOSE
**版本**：`1.0.0`
**状态**：`CLOSED`
**日期**：2026-11-04
**决策**：MB-D-007 备份一致性优先
**关闭依据**：MB-BK-001、MB-BK-002、`checkpoint/misebase-backup-consistency-v2-20261104`
**范围**：只记录关闭结论、证据、保留项与 Stage 4 启动条件；不进入 Stage 4

---

## 1. 决策与审查结论

| 项 | 内容 |
|---|---|
| 决策 | MB-D-007 备份一致性优先 |
| 原状态 | `ACCEPTED`（2026-11-04，Stage 4 强制前置） |
| 审查结论 | **通过** |
| 新状态 | **`CLOSED`（2026-11-04，审查通过）** |
| 关闭范围 | 六项技术事项、Schema 版本阻断修复、测试与检查点 |
| 是否进入 Stage 4 | **否**；Stage 4 可启动，等待单独任务书与负责人启动确认 |

MB-D-007 的 Stage 4 强制前置已满足。本关闭记录**不构成** Stage 4 开发授权；Stage 4 仍需单独任务书。

---

## 2. 六项技术事项完成摘要

| # | 事项 | 交付 | 证据 |
|---:|---|---|---|
| 1 | 写屏障 | `WriteBarrier`；与 `RoomTransactionRunner` 共享同一把 `Mutex`；超时/嵌套拒绝/失败释放 | `WriteBarrierTest` 5 例 |
| 2 | WAL checkpoint | 屏障内 `wal_checkpoint(TRUNCATE)` → `quick_check` → 复制 → 快照校验 → 释放 | `QuiescedDatabaseSnapshotterTest` 4 例 |
| 3 | `AutoBackupWorker` 真实返回 | success 必须“归档存在 + 校验通过 + `backup_record` 写入成功”；失败/重试/取消映射明确 | `AutoBackupWorkerTest` 6 例、`AutoBackupRunnerImplTest` 8 例 |
| 4 | 恢复原子性 | 回滚副本校验 → 同目录临时文件 + fsync + 原子 rename + 目录 fsync；回滚失败 `RECOVERY_REQUIRED` | `RestoreExecutorTest` 6 例 |
| 5 | Worker 单元测试 | 成功、校验失败、空间不足、权限拒绝、屏障超时、取消；Scheduler→Worker 集成 | 13 例（含编排层） |
| 6 | WAL 恢复测试 | 未 checkpoint 已提交事务、恢复后完整性、并发写、中断注入 | `WalBackupRestoreTest` 2 例、`BackupConcurrencyTest` 1 例 |

---

## 3. 阻断缺陷修复（MB-BK-002）

审查发现 `StartupCoordinator.EXPECTED_SCHEMA_VERSION = 1` 与 Schema v2 不一致：

- 已持久化 v2 库第二次启动时 `existingVersion=2 > 1` → `Incompatible`，主界面不可达；
- 修复为 `EXPECTED_SCHEMA_VERSION = 2`，与 `CulinaryDatabase @Database(version = 2)` 对齐；
- `StartupCoordinatorTest` 5 例：全新库、持久化 v2、v1→v2 迁移、v3 拒绝、损坏库；
- 修复提交 `30d57bbc630b92431903dc084631d6116851986b`；
- 修复报告：[MB-BK-002](MB-BK-002-STARTUP-SCHEMA-FIX.md)。

---

## 4. 测试与验证证据

| 项 | 结果 |
|---|---|
| 全量 JVM 测试 | **612 次执行 / 376 唯一用例 / 0 失败 / 0 跳过** |
| `StartupCoordinatorTest` | 5 例 / 0 失败 |
| Worker + 编排 + 集成 | 15 例 / 0 失败 |
| WAL 恢复 + 并发 + 中断 | 5 例 / 0 失败 |
| Debug APK | 构建成功 |
| ktlint / detekt | PASS |
| 模块依赖 | 13/13、DAG、PASS |
| 休眠表 | 11 张、0 处允许范围外引用、PASS |
| 文档一致性 | PASS |

验证记录：

- [MB-BK-001 执行报告](MB-BK-001-EXECUTION-REPORT.md)
- [WAL 验证](MB-BK-001-WAL-VERIFICATION.md)
- [Worker 测试记录](MB-BK-001-WORKER-TEST-RECORD.md)
- [一致性验证](MB-BK-001-CONSISTENCY-VERIFICATION.md)
- [失败与回滚验证](MB-BK-001-FAILURE-ROLLBACK.md)
- [MB-BK-002 Schema 版本修复](MB-BK-002-STARTUP-SCHEMA-FIX.md)

测试为 JVM/Robolectric 范围；**不替代** MB-D-011 真机结论。

---

## 5. 检查点与回档

| 项 | 值 |
|---|---|
| 最终代码检查点 | `checkpoint/misebase-backup-consistency-v2-20261104` |
| 对象哈希 | `fba896452614495d49fe1328adb6d19338f0968e` |
| 解析提交 | `a69b5d4ef22cf8ada5f03578020ef70e1e40979d` |
| 覆盖内容 | MB-BK-001 实现、测试统计更正、MB-BK-002 版本修复、MB-BK-002 报告 |
| 旧检查点 | `checkpoint/misebase-backup-consistency-20261104` → 对象 `4a271e8ac0081b8ff21b1966756d0432a7767671`；提交 `8787c059a4a888f16ad8837ef8722316d762c257`；**未移动** |
| 原始签收检查点 | `checkpoint/misebase-mb-d-007-20261104` → 对象 `b3a429f9d2698c245f8106a421c1329686a61d61`；提交 `3c3db1eb7c835b42c671b2094d8ee4249d599c6b`；未移动 |
| `framework-0.1.0` | `2b523de4b87e37a2eb08b4c7c851549350ad811a`；未移动 |
| Stage 4 起点旧标签 | `checkpoint/misebase-stage4-start-20261104` → `c5ea6feeef0a843b4f84590c7ea7f72b0e6693f2`；未移动 |

本关闭记录提交后创建：

- `checkpoint/misebase-mb-d-007-closed-20261104`
- `checkpoint/misebase-stage4-go-20261104`

两个标签均解析到本关闭提交；对象哈希与解析提交见交付报告 §6。`stage4-go` 只标记“Stage 4 可启动”，不替代任务书或负责人启动确认。

### 5.1 关闭标签回执

| 标签 | 对象哈希 | 解析提交 |
|---|---|---|
| `checkpoint/misebase-mb-d-007-closed-20261104` | `9e3e9a5528f908dfb80fc2265200abcbbd0bf760` | `e76c452911da5141e7ab6d1b76b1246ade96d05d` |
| `checkpoint/misebase-stage4-go-20261104` | `c734847efb153bfd4c7ba53a606866483fc5a293` | `e76c452911da5141e7ab6d1b76b1246ade96d05d` |

两个标签均为 annotated tag，打标签时间 2026-11-04 23:57:39 +0800；旧标签未移动。

---

## 6. 风险登记册更新

[S0-04](../05-handover/S0-04-RISK-REGISTER.md) `1.1.0`：

| 风险 | 状态 | 说明 |
|---|---|---|
| S0-R-02 WAL 与备份一致性 | **MITIGATED** | 写屏障 + checkpoint + WAL/并发测试；残留真机/kill 验证 |
| S0-R-10 文件恢复中断 | **PARTIAL（收窄）** | DB 原子性、回滚、`RECOVERY_REQUIRED` 已验证；附件与真机 kill 未覆盖 |
| S0-R-11 自动备份假成功 | **MITIGATED** | Worker 真实返回 + 成功条件 + 单测；生产触发点未接线 |

---

## 7. 保留项

以下项目**不在** MB-D-007 关闭范围内，不得视为已完成：

1. **附件（图片）未接线**：自动备份/恢复当前只覆盖 DB；附件恢复未实现。
2. **SAF 用户目录未接线**：自动备份使用应用外部私有目录；设置页/SAF 目标待后续任务。
3. **生产自动备份触发点未接线**：Worker 返回真实，但默认不会自动运行；调度接线待后续任务。
4. **真机 kill/断电未验证**：JVM/Robolectric 用异常/journal 模拟；MB-D-011 仍 `PROPOSED`。
5. **目录 fsync 为 best-effort**：平台不支持时降级；原子 rename 仍保证目标不半写。
6. **README 部分历史内容仍陈旧**：本次只更新当前阶段/决策/数据安全相关段落；完整文档修订需单独授权。
7. **v1 测试夹具为反向构造**：在 JVM 测试内由 v2 库反向构造 v1，不依赖 instrumentation/`room-testing`；不是从 `1.json` 重建的独立夹具。

---

## 8. Stage 4 启动条件

Stage 4 只有在以下条件同时满足后才可启动：

1. 本关闭记录已落库（`MB-D-007` 状态 `CLOSED`）；
2. 项目负责人明确确认 Stage 4 启动，并下达单独 Stage 4 任务书；
3. Stage 4 任务书明确范围、禁止项、测试与回滚要求。

`checkpoint/misebase-stage4-go-20261104` 仅标记前置条件已满足，不构成启动授权；本记录也不授权库存业务、扫码 UI 或任何 Stage 4 页面开发。

---

## 9. 停止声明

**未进入 Stage 4。**

- 未开发库存业务、库存流水、采购清单、计算记录 UI；
- 未开发/修改 Compose 页面、导航、视觉定稿、扫码 UI；
- 未新建 `feature:scan` 或任何模块；
- 未修改业务代码、备份/恢复实现、Schema/Entity/迁移、导航、模块集；
- 未移动 `framework-0.1.0` 及任何既有 checkpoint 标签；
- 未启用 CI、未做真机验收、未创建签名（MB-D-010/011/012 仍 `PROPOSED`）。

---

## 10. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0.0 | 2026-11-04 | MB-D-007-CLOSE-001：记录审查通过、六项完成、阻断修复、612/376/0 证据、检查点、风险、保留项与 Stage 4 启动条件 | 接管工程组 |
