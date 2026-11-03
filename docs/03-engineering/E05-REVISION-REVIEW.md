# E05 0.4.0 修订复核包

**文档编号**：E05A（复核包，不计入 17 项正式交付物）
**版本**：`0.1.0`
**状态**：`Review`
**日期**：2026-11-04
**对应文档**：[E05 备份与恢复 0.4.0](E05-BACKUP-RESTORE.md)
**决策状态**：D-011 = PENDING；G1 = PENDING

---

## 1. 修订范围

本次只修订备份/恢复方案，不扩大架构范围、不新增业务模块。D-004 已接受为 15 模块；D-011 仍需复核。

## 2. 修改条款清单

| # | 复核意见 | 修订落点 | 结果 |
|---:|---|---|---|
| 1 | manifest 自身哈希循环依赖 | E05 4.2/4.3/5.1/5.2 | manifest 不再含自身 hash；`manifest.sha256` 独立；`checksums.sha256` 排除自身/manifest |
| 2 | schema 版本判断方向 | E05 4.4、12、12.1、18 | 统一为 `>` 拒绝、`==` 继续、`<` 需完整已测试迁移路径 |
| 3 | 缺失图片处理规则 | E05 7.3、18 | 默认失败；仅用户明确继续才 `PARTIAL`；恢复再次确认 |
| 4 | journal 与中断恢复 | E05 13.1–13.4、18 | INTENT/DONE + fsync、幂等移动、DB/WAL/SHM、回滚失败 Recovery |
| 5 | 一致性快照回退 | E05 6.1–6.3.1、18 | checkpoint 返回值检查、读连接等待/重试/终止、写屏障覆盖后台、源库健康检查、Online API 未验证不标记支持 |

## 3. 备份文件哈希结构

```text
culinary-backup-<timestamp>.zip
├─ manifest.json
│    ├─ database[].sha256
│    ├─ attachments[].sha256
│    └─ integrity.checksums_sha256
├─ checksums.sha256          # 只列 DB/附件；不含自身/manifest
├─ manifest.sha256           # SHA256(最终 manifest.json)；外部根
├─ database/culinary.db
└─ attachments/images/...

关系：
checksums.sha256  --SHA256-->  manifest.integrity.checksums_sha256
manifest.json     --SHA256-->  manifest.sha256
manifest.sha256   --无内部再哈希--> 外部根
```

生成顺序：

1. DB/附件落盘并逐文件算 SHA-256；
2. 写 `checksums.sha256`（只含 DB/附件）；
3. 算 `checksums_sha256`；
4. 写最终 `manifest.json`（含 `checksums_sha256`）；
5. 算 `manifest.json` 的 SHA-256，写入 `manifest.sha256`；
6. 打包；加密时对整个 ZIP 做 AES-GCM。

校验顺序：

1. 容器/GCM tag；
2. manifest 必填字段；
3. `manifest.sha256` vs manifest；
4. `checksums_sha256` vs `checksums.sha256`；
5. 逐文件 hash；
6. DB 完整性/可打开；
7. 附件引用一致性。

> 未加密 ZIP 的 `manifest.sha256` 只能防意外损坏；防篡改需加密备份。加密时 GCM tag 是外部密码学根。

## 4. 恢复 journal 状态与中断恢复

### 4.1 状态机

```text
PREPARED
  -> DB_CLOSING
  -> DB_MOVED_ASIDE
  -> DB_INSTALLED
  -> ATTACHMENTS_MOVED_ASIDE
  -> ATTACHMENTS_INSTALLED
  -> MIGRATING
  -> VERIFIED
  -> DONE

失败:
  -> ROLLBACK_STARTED
  -> ROLLED_BACK
  -> RECOVERY_REQUIRED
```

### 4.2 每次文件移动

```text
写 journal: operation={kind,source,target,state=INTENT}, fsync
执行移动
写 journal: state=DONE, phase=..., fsync
```

- DB、`-wal`、`-shm` 分别记录；图片目录优先整体 rename，否则逐文件。
- 移动前检查 source/target；两者都存在时比对 hash/size，不盲目覆盖。
- 启动时同时检查 journal 与实际位置；不只看 phase。

### 4.3 中断恢复判定

| 现场 | 动作 |
|---|---|
| `DONE` | 清理 staging/rollback，删除 journal |
| `ROLLED_BACK` 且旧库校验通过 | 清理，删除 journal |
| DB 已移走/已安装，附件未完成 | 优先回滚 DB，保留 staging |
| 附件已安装，迁移未完成 | 尝试迁移/校验；失败回滚 |
| 回滚/移动冲突 | `RECOVERY_REQUIRED`，保留全部文件 |
| journal 丢失但 staging/rollback 存在 | Recovery，不自动清理 |

### 4.4 数据库替换

- 写屏障 + 等待写事务 + `RoomDatabase.close()`。
- DB/WAL/SHM 全部移入 rollback；install 前确保 live 无旧 WAL/SHM。
- 迁移失败必须回滚，当前库保持恢复前状态。
- 回滚失败：Recovery，保留 staging/rollback，不自动清理。

## 5. 对应测试用例与预期

| # | 测试 | 预期 |
|---:|---|---|
| 1 | manifest 不含自身 hash | 生成/校验均通过；无自引用 |
| 2 | checksums 不含自身/manifest | 结构检查通过；含则拒绝 |
| 3 | `backup_schema > current` | 拒绝，现有数据不变 |
| 4 | `backup_schema == current` | 继续校验 |
| 5 | `backup_schema < current`，路径完整已测试 | 恢复 + 迁移成功 |
| 6 | `backup_schema < current`，路径缺失/未测试 | 拒绝，不跳过迁移 |
| 7 | 缺失图片，默认 | 备份失败，不生成 VERIFIED |
| 8 | 缺失图片，用户明确继续 | PARTIAL；记录 ID/路径；恢复前再次确认 |
| 9 | checkpoint 返回 busy | 等待/重试；仍失败中止，不复制 |
| 10 | 活动读连接导致 checkpoint 失败 | 按等待/重试/终止处理；不强制继续 |
| 11 | 后台任务写入 | 被写屏障排队；快照一致 |
| 12 | 快照后源库 | 读成功 + BEGIN IMMEDIATE/ROLLBACK 成功 |
| 13 | 每个文件移动阶段强杀 | 重启后按 journal + 实际位置幂等恢复/回滚 |
| 14 | 回滚失败 | RECOVERY_REQUIRED；保留文件，不自动清理 |
| 15 | 迁移失败 | 回滚到恢复前，当前库不被破坏 |
| 16 | 未验证 Online Backup API | 不得标记支持；必须走已验证路径 |

## 6. 当前状态

- E05 `0.4.0`：`Review`。
- D-011：`PENDING`。
- D-004：`ACCEPTED`（15 模块 + backup 边界）。
- G5：发布门禁（不阻塞框架）。
- G1：`PENDING`。
- 未创建正式工程，未创建 Release keystore。

## 7. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 0.1.0 | 2026-11-04 | 按 G1 复核意见提交 E05 0.4.0 的五项修订证据 | 架构组 |
