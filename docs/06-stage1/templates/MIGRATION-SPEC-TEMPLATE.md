# 数据库迁移规范模板（vN → vN+1）

> **状态**：`TEMPLATE`。本文件是迁移准备的填写模板，**不代表任何迁移已实施**。
> 截至 2026-11-04，MISEBASE 数据库为 **schema v1（31 表）**，未创建 v2、未写入迁移。
> 实施迁移必须先有 MB-D-005 之外的单独正式授权，并满足本模板与
> [MB-S1-05](../MB-S1-05-MIGRATION-READINESS.md) 的全部门禁。

## 1. 变更声明

| 项 | 内容 |
|---|---|
| 迁移编号 | `MIGRATION_<from>_<to>` |
| 起点版本 | `v<from>` |
| 目标版本 | `v<to>` |
| 关联决策 | `MB-D-xxx`（必须是 `ACCEPTED`） |
| 变更类型 | 加表 / 加列 / 加索引 / 改约束 / 数据回填（可多选） |
| 是否涉及休眠表 | 是 / 否（若“是”，必须单独授权） |
| 回档点 | 提交哈希 + 标签 + 迁移前备份文件 |

## 2. Schema diff

```text
<!-- 从 core/database/schemas/<db>/<from>.json 与 <to>.json 生成 diff；
     逐项列出新增/删除/修改的表、列、索引、触发器、外键。 -->
```

要求：

1. 只允许前向、非破坏性变更；禁止 `fallbackToDestructiveMigration`；
2. 不删除用户数据；必须删除时先备份、再做数据迁移、再删列/表；
3. 涉及 11 张 `DORMANT` 表的任何结构变更，一律先停止并另立任务；
4. 新列必须有明确默认值或可空；新增 `NOT NULL` 列必须回填。

## 3. 迁移实现

```kotlin
// core/database/src/main/java/com/culinary/core/database/CulinaryMigrations.kt
internal val MIGRATION_<from>_<to> = object : Migration(<from>, <to>) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1) DDL（CREATE/ALTER/INDEX）
        // 2) 数据回填（分批、可重入）
        // 3) 约束/触发器重放（如需要）
    }
}
```

登记：

```kotlin
val ALL: Array<Migration> = arrayOf(MIGRATION_<from>_<to>)
```

## 4. 迁移后校验

必须全部通过：

- `PRAGMA foreign_key_check` 返回空；
- `PRAGMA integrity_check` 返回 `ok`；
- 关键表行数与迁移前一致（允许声明的新增行除外）；
- 索引/触发器存在且生效（查询 `sqlite_master`）；
- 休眠表数量与结构未被改动（11 张，逐表比对）；
- 应用冷启动、读写、备份、恢复冒烟通过。

## 5. 测试

- 使用 [MigrationTestTemplate.kt.txt](MigrationTestTemplate.kt.txt)；
- 覆盖：`v<from>` 空库、真实旧数据、相邻版本、连续升级、失败回滚；
- 测试放在 `core/database/src/androidTest/`，需要设备/模拟器；
- **模板/测试存在 ≠ 迁移已实施**。

## 6. 备份、回滚与回档点

按 [MIGRATION-BACKUP-ROLLBACK-CHECKLIST.md](MIGRATION-BACKUP-ROLLBACK-CHECKLIST.md) 执行：

1. 迁移前生成并校验备份；
2. 记录回档点（Git commit + tag + 备份文件 SHA-256）；
3. 迁移失败时保留现场，按 journal/回滚副本恢复；
4. 迁移成功后保留回档副本至少一个发布周期。

## 7. 禁止事项

- 未签收决策、无回档点、无迁移前备份时实施迁移；
- 创建“正式 Schema v2”但没有迁移测试与回滚验证；
- 修改/删除 11 张休眠表；
- 用 destructive migration 或清库“解决”迁移失败；
- 在迁移中执行不可重入的文件 I/O 或网络操作。

## 8. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 0.1.0 | 2026-11-04 | 建立迁移规范模板；未实施任何迁移 | 接管工程组 |
