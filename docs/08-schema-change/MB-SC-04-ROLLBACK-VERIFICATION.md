# MB-SC-04 迁移回滚验证记录

**文档编号**：MB-SC-04
**版本**：`1.0.0`
**状态**：`Review`
**日期**：2026-11-04
**测试类**：`core/database/src/test/java/com/culinary/core/database/CulinaryRollbackV1Test.kt`

---

## 1. 结论先行

**本任务不支持 Schema v2 → v1 的原地降级。**

支持的安全回滚路径只有：

1. **恢复迁移前 v1 备份**，然后安装/运行 v1 构建；或
2. 恢复迁移前 v1 备份后，由 v2 构建重新执行 v1→v2；
3. 迁移前保留 v1 数据库副本，用于诊断与再次迁移。

不支持：

- 直接 `ALTER TABLE` 降级或删除 v2 列/表；
- 通过 `fallbackToDestructiveMigration` 清库；
- 用 v1 构建打开 v2 数据库；
- 在本任务中新增 `Migration(2, 1)`。

---

## 2. 验证用例

命令：

```powershell
.\gradlew.bat :core:database:testDebugUnitTest `
  --tests "com.culinary.core.database.CulinaryRollbackV1Test" `
  --console=plain --no-daemon
```

结果：`BUILD SUCCESSFUL`；1 个用例通过。

测试步骤：

```text
1. 从 1.json 创建 v1 库，写入菜谱/已发布版本
2. 复制 v1 库为“迁移前备份” pre-migration-v1.db
3. 用 v2 Room 打开并执行 MIGRATION_1_2
4. 写入一条迁移后计算记录，确认 user_version=2
5. 关闭数据库
6. 独立只读打开 pre-migration-v1.db：
     user_version = 1
     recipe_version 行数 = 1
     calculation_record / calculation_record_line 不存在
7. 将 pre-migration-v1.db 写回数据库位置，清理 -wal/-shm
8. 用 v2 Room 重新打开：再次执行 v1→v2
     数据仍为 1 行，servings 读为 1
     计算记录为 0 条（迁移后新增数据已随备份回退）
     foreign_key_check 为空，quick_check = ok
9. 断言迁移注册表只有 1→2，没有 2→1
```

---

## 3. 断言结果

| 断言 | 结果 |
|---|---|
| 迁移前 v1 备份可独立打开 | 通过 |
| 备份 `user_version = 1` | 通过 |
| 备份含 `recipe_version`，不含 v2 计算记录表 | 通过 |
| v2 迁移后 `user_version = 2` | 通过 |
| 恢复 v1 备份后由 v2 重新迁移成功 | 通过 |
| 回退后历史 `servings` 仍为 1 | 通过 |
| 迁移后新增计算记录随备份回退为 0 | 通过（预期：会丢失迁移后新增数据） |
| 无半迁移状态 | 通过 |
| 注册表无 `2 → 1` | 通过 |

---

## 4. 对正式数据库的保护

- 测试只使用 Robolectric 隔离数据库文件，不接触用户正式数据库；
- 回滚副本是测试临时目录/独立文件；
- 没有删除数据库、没有破坏性迁移、没有重建迁移历史；
- 没有移动 `framework-0.1.0` 或任何既有 checkpoint 标签。

---

## 5. 生产回滚口径

如果需要从 v2 回退到 v1：

1. 先对当前 v2 数据再做一次备份（保留现场）；
2. 找到迁移前 v1 备份（PRE_MIGRATION 或人工副本）；
3. 校验备份后，恢复到数据库位置；
4. 安装 v1 构建；
5. 记录回退造成的数据损失范围（迁移后新增的 `servings` 非 1 值与计算记录会丢失）；
6. 不得用“清空数据库/删除文件”让旧代码启动。

该流程中的“迁移前自动备份”仍受 MB-D-007 `PROPOSED` 限制；本任务未把自动备份接线，
也没有修改备份/恢复实现。

---

## 6. 已知限制

| 限制 | 影响 |
|---|---|
| 无 v1 APK/真机 | 未在设备上实际安装 v1 构建验证“旧版本拒绝 v2 DB”；仅证明 v1 备份可读、无 2→1 迁移 |
| 无自动迁移前备份 | MB-D-007 未签收；生产回退需人工准备 v1 备份 |
| 无进程中断注入 | 未模拟迁移中进程被杀；由 SQLite 事务语义与现有 journal 设计覆盖，未新增实现 |

---

## 7. 结论

回滚路径可复现，且结论明确：**只支持备份恢复，不支持原地降级**。
本报告不把“恢复备份后重新迁移”写成“原地降级”，也不声称 v1 构建已在设备上验证。

---

## 8. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0.0 | 2026-11-04 | 记录回滚支持范围、验证步骤、断言、限制与生产口径 | 接管工程组 |
