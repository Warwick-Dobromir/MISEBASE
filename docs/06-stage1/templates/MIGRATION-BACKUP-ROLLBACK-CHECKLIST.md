# 迁移前备份与回滚验证清单（模板）

> **状态**：`TEMPLATE` / `PLAN`。本清单是执行迁移前的强制门禁，**不代表迁移已实施**。
> 当前数据库为 schema v1；未创建 v2，未执行任何迁移。

## 0. 回档点（必须唯一、可解析）

| 项 | 记录值 |
|---|---|
| Git 分支 | `misebase/main` |
| 迁移前提交 | `<40 位 commit hash>` |
| 回档标签 | `checkpoint/misebase-before-migration-v<to>-<date>` |
| DB schema 版本 | `v<from>` |
| 备份文件路径 | `<应用私有目录或 SAF 目标>` |
| 备份 SHA-256 | `<sha256>` |
| 备份大小 | `<bytes>` |
| 验证人/时间 | `<name / ISO-8601>` |

## 1. 迁移前

- [ ] 工作树 clean，`git status --porcelain` 为空；
- [ ] 已记录 `git rev-parse HEAD`，并创建回档标签；
- [ ] 应用已停止写入（前台无编辑、后台 Worker 已取消/等待）；
- [ ] 已获取写锁/写屏障（若尚未实现，必须先完成 MB-D-007 修复，不得带病迁移）；
- [ ] 执行 `wal_checkpoint(TRUNCATE)`（WAL 模式）；
- [ ] 执行 `PRAGMA quick_check` 或 `integrity_check`，结果 `ok`；
- [ ] 生成备份（E05 归档），校验 manifest/checksums；
- [ ] 备份文件复制到第二位置（仓库外/另一目录），记录 SHA-256；
- [ ] 记录关键表行数、31 张表清单、11 张休眠表清单；
- [ ] 记录当前 APK 版本、schema identity hash。

## 2. 迁移执行

- [ ] 只在备份校验通过后开始；
- [ ] 迁移在单一事务中执行（Room `Migration` 由框架包裹）；
- [ ] 记录开始/结束时间与迁移版本；
- [ ] 失败立即停止，不重试 destructive 操作，不删除现场。

## 3. 迁移后验证

- [ ] `PRAGMA foreign_key_check` 为空；
- [ ] `PRAGMA integrity_check` 返回 `ok`；
- [ ] 关键表行数与迁移前一致（按声明允许的差异）；
- [ ] 索引/触发器存在；
- [ ] 11 张休眠表仍存在且结构未变；
- [ ] 冷启动、列表/详情读写、备份、恢复冒烟通过；
- [ ] 记录迁移后 schema version 与 identity hash。

## 4. 失败回滚

- [ ] 关闭数据库连接，停止写入；
- [ ] 保留失败现场（DB、WAL/SHM、journal、日志）；
- [ ] 用迁移前备份恢复（按 E05/RestoreExecutor 流程）；
- [ ] 或使用回滚副本/journal 恢复；
- [ ] 恢复后重复 §3 的完整性检查；
- [ ] 记录失败原因、影响范围、是否可重试；
- [ ] 未查明原因前不得再次尝试迁移。

## 5. 回档（代码 + 数据）

| 场景 | 操作 |
|---|---|
| 代码回滚、数据库未升级 | `git checkout <回档 commit>` / 切换标签；数据库无需处理 |
| 代码回滚、数据库已升级 | 先恢复到回滚 tag 对应的 APK/代码；用迁移前备份恢复数据库；禁止让旧代码直接打开新 schema |
| 仅数据回滚 | 用备份恢复；保留当前库副本用于诊断 |
| 仅代码回滚 | 保留新库副本；旧代码必须拒绝打开更高 schema（启动门禁） |

## 6. 证据归档

- [ ] 迁移前/后 `git rev-parse HEAD`；
- [ ] 备份文件 SHA-256 与大小；
- [ ] 迁移测试 XML/报告；
- [ ] `foreign_key_check` / `integrity_check` 输出；
- [ ] 回滚演练记录（时间、结果、操作人）；
- [ ] 结论：PASS / FAIL / BLOCKED。

> **模板/测试 ≠ 迁移已实施。** 未完成本清单全部项目，不得把迁移标记为完成。
