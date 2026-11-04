# MB-SC-03 备份恢复验证记录

**文档编号**：MB-SC-03
**版本**：`1.0.0`
**状态**：`Review`
**日期**：2026-11-04
**决策边界**：MB-D-007 仍为 `PROPOSED`；本任务**未修改**任何备份/恢复实现
**测试类**：`core/data/src/test/java/com/culinary/core/data/backup/BackupRestoreV2Test.kt`

---

## 1. 验证目标

在 Schema v2 上验证：

1. 用**既有** `BackupArchiveWriter` 生成 v2 备份；
2. 用**既有** `BackupArchiveVerifier` 校验归档；
3. 用**既有** `DatabaseRestorer` 恢复；
4. 恢复后的 v2 数据完整：`recipe_version.servings` 可读，计算记录及需求行可读；
5. 迁移/备份没有改变备份实现代码。

本验证是**文件级**恢复验证，不是真机 SAF/UI 备份流程验收。

---

## 2. 测试数据

| 对象 | 值 |
|---|---|
| 单位 | 种子 `g`（克） |
| 食材 | `ingredient-flour`、`ingredient-water` |
| 菜谱 | `recipe-backup` / `备份菜谱` |
| 菜谱版本 | `recipe-backup-v1`，`servings = 3`，已发布、当前版本 |
| 计算记录 | `calc-backup`，`servings = 3.0`，`engine_version = stage2-1`，`payload_hash = c*64`，`CONFIRMED` |
| 需求行 | 2 行：面粉 200 g（非可选）、水 50 g（可选） |
| 备份 manifest | `schema_version = 2` |

---

## 3. 执行步骤

```powershell
.\gradlew.bat :core:data:testDebugUnitTest `
  --tests "com.culinary.core.data.backup.BackupRestoreV2Test" `
  --console=plain --no-daemon
```

结果：`BUILD SUCCESSFUL`；1 个用例通过。

测试内部顺序：

```text
创建 v2 Room 文件库并写入数据
  → 关闭数据库
  → BackupArchiveWriter 写 ZIP（schemaVersion = 2）
  → BackupArchiveVerifier 校验 manifest / checksums / DB 哈希
  → 从 ZIP 取出 database/culinary.db 字节
  → DatabaseRestorer 恢复到独立文件
  → Room 打开恢复后的库
  → 读取 servings、计算记录、需求行
  → foreign_key_check + quick_check
```

---

## 4. 断言结果

| 断言 | 结果 |
|---|---|
| manifest `app.schema_version = 2` | 通过 |
| 归档校验 `Outcome.Success` | 通过 |
| `DatabaseRestorer.restore` 成功 | 通过 |
| 恢复库 `PRAGMA user_version = 2` | 通过 |
| 恢复库 `recipe_version.servings = 3` | 通过 |
| `calculation_record` 可按 ID 读出 | 通过 |
| `recipe_version_id` / `servings` / `engine_version` / `payload_hash` / `status` 一致 | 通过 |
| 需求行 2 行，首行数量 200.0 | 通过 |
| `foreign_key_check` 为空 | 通过 |
| `quick_check = ok` | 通过 |

---

## 5. 实现变更边界

```powershell
git diff --name-only a698c863d5fe81cf8dca1cfdeef4bedff16ffa08..HEAD -- `
  core/data/src/main/java/com/culinary/core/data/backup
```

结果：**空**。以下文件均未修改：

- `AutoBackupWorker`（本仓库当前不存在该实现文件，也未新增）
- `BackupArchiveWriter`
- `RestoreExecutor`
- `DatabaseRestorer`
- `RestoreJournal` / `RestoreJournalStore` / `RestorePrecheck`
- 写屏障 / WAL checkpoint

验证只调用既有公开接口，不改变归档格式、manifest、checksum、恢复顺序或回滚逻辑。

---

## 6. 已知限制

| 限制 | 影响 |
|---|---|
| MB-D-007 未签收 | 备份前 checkpoint/写屏障未实现；本验证不能证明生产备份与 WAL 一致 |
| 无真机 | 未验证 SAF、系统文件选择器、真实存储权限与进程重启 |
| 文件级恢复 | 未执行 App 内完整恢复 UI/流程；`RestoreExecutor` 的进程重启路径未在本任务重新验收 |
| 备份实现未改 | 这是任务硬性边界，不是缺陷 |

---

## 7. 结论

在“不修改备份/恢复实现”的边界内，v2 备份 → 校验 → 恢复 → 数据读取链路通过；
`servings` 与计算记录（含需求行）在恢复后完整。该结论只覆盖文件级数据完整性，
不替代 MB-D-007 签收后的生产一致性备份验收。

---

## 8. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0.0 | 2026-11-04 | 记录 v2 备份恢复验证、断言结果、实现边界与限制 | 接管工程组 |
