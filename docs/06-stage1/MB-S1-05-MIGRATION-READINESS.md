# MB-S1-05 数据库迁移准备物

**文档编号**：MB-S1-05
**版本**：`0.1.0`
**状态**：`Review`（准备物；**迁移未实施**）
**日期**：2026-11-04
**依据**：MB-D-005（ACCEPTED）、MB-S1-001 任务书 §4.6/§6、S0-R-01
**结论先行**：本文件只建立规范、模板、验证与回档设计；**没有创建 Schema v2，没有写迁移，没有改 Entity/DAO/数据库文件**。

---

## 1. 当前事实（2026-11-04）

| 项 | 值 |
|---|---|
| 数据库 | Room 2.8.5，schema **v1** |
| 表数 | **31**（其中 **11** 张 `DORMANT`） |
| 导出 schema | `core/database/schemas/com.culinary.core.database.CulinaryDatabase/1.json` |
| 迁移实现 | `CulinaryMigrations.ALL = emptyArray()`（无迁移） |
| 迁移测试 | `CulinaryDatabaseMigrationTest` 2 例（androidTest，本次未运行） |
| Schema v2 | **不存在** |
| 本次 Stage 1 对 schema/Entity/DAO/迁移/DB 文件的修改 | **0** |

> 本阶段只增加文档与模板。`1.json`、Entity、DAO、`CulinaryMigrations`、数据库文件均未改动；
> 可用 `git diff --name-status 2b523de..HEAD -- core/database` 验证（应只有合法变更或为空）。

## 2. 准备物清单

| # | 交付物 | 位置 | 作用 |
|---|---|---|---|
| 1 | 迁移规范 | [templates/MIGRATION-SPEC-TEMPLATE.md](templates/MIGRATION-SPEC-TEMPLATE.md) | 迁移声明、DDL、登记、校验、禁止事项 |
| 2 | 迁移测试模板 | [templates/MigrationTestTemplate.kt.txt](templates/MigrationTestTemplate.kt.txt) | `MigrationTestHelper` 骨架（不参与编译） |
| 3 | 备份/回滚验证清单 | [templates/MIGRATION-BACKUP-ROLLBACK-CHECKLIST.md](templates/MIGRATION-BACKUP-ROLLBACK-CHECKLIST.md) | 迁移前/中/后门禁与回档矩阵 |
| 4 | 回档点设计 | 本文 §5 | Git + 备份 + journal 的三层回档 |
| 5 | 休眠表边界 | [MB-S1-04](MB-S1-04-DORMANT-TABLES.md) + `config/dormant-tables.json` | 防止迁移误碰 11 张表 |
| 6 | 迁移失败分析 | [S0-04 S0-R-01](../05-handover/S0-04-RISK-REGISTER.md) | 既有风险与验证方法 |

## 3. 迁移规范（摘要）

完整可填写模板见 [MIGRATION-SPEC-TEMPLATE.md](templates/MIGRATION-SPEC-TEMPLATE.md)。要点：

1. **先决策、后迁移**：每个 schema 变更必须有 `ACCEPTED` 决策与单独任务；MB-D-005 只授权“保留休眠表、不建 v2”。
2. **只前向、非破坏**：禁止 `fallbackToDestructiveMigration`；不删用户数据；新增 `NOT NULL` 列必须回填。
3. **11 张休眠表不动**：任何涉及 `production_plan*`、`material_requirement*`、`procurement_requirement`、`purchase_order*`、`goods_receipt*`、`supplier` 的结构变更，先停止并另立任务。
4. **单一登记处**：迁移只放 `CulinaryMigrations.ALL`，版本号连续；`exportSchema=true`。
5. **测试同提交**：`MigrationTestHelper` 覆盖空库、真实旧数据、相邻版本、失败注入；测试通过才算实现。
6. **迁移前备份**：按 E05 生成、校验、异地保存；无备份不迁移。
7. **失败保留现场**：不自动清库、不删除 WAL/journal；按 `RestoreExecutor` / 回滚副本恢复。

## 4. 迁移执行流程（未来）

```text
0. 决策 ACCEPTED + 单独任务授权
1. 记录回档点：git commit + tag + schema v1 + 备份 SHA-256
2. 停止写入 → 写锁/写屏障 → wal_checkpoint(TRUNCATE) → quick_check
3. E05 备份 → 校验 manifest/checksums → 复制到第二位置
4. 记录行数/表清单/identity hash
5. DDL + 数据回填（单事务）
6. 校验：foreign_key_check / integrity_check / 行数 / 索引 / 触发器 / 休眠表结构
7. 冷启动 + 读写 + 备份恢复冒烟
8. 保留回档副本 ≥ 1 个发布周期
9. 失败：停止写入 → 保留现场 → 回滚副本/journal → 复检 → 报告
```

**当前阻塞**：步骤 2 的写屏障/checkpoint 属于 MB-D-007 范围，仍为 `PROPOSED`；在签收并实现前，
不得把上述流程用于真实用户数据。

## 5. 回档点设计

采用三层回档，三者都要有，不能只靠 Git：

| 层 | 对象 | 建立时机 | 验证 |
|---|---|---|---|
| L1 代码 | Git commit + `checkpoint/misebase-before-migration-*` 标签 | 迁移前 | `git rev-parse <tag>` 可解析；工作树 clean |
| L2 数据 | E05 备份 ZIP（DB + 附件 + manifest/checksums） | 迁移前 | 重新校验 SHA-256；在另一目录可读 |
| L3 现场 | 回滚副本 `<restoreId>.db` + `RestoreJournal` | 恢复/迁移开始时 | journal 阶段可读；副本可打开；`integrity_check=ok` |

规则：

- L1 与 L2 必须绑定同一个提交/时间点；标签名与备份文件记录在迁移报告中；
- 旧版本应用不得打开更高 schema：启动门禁必须拒绝并提示，不得 destructive 清库；
- 代码回滚但数据已升级时，先恢复 L2，再启动旧代码；
- 仅数据回滚时保留当前库副本，供诊断；
- 回档后重复 `foreign_key_check` + `integrity_check` + 关键表行数比对。

## 6. 迁移测试矩阵（模板落地后）

| 场景 | 输入 | 期望 |
|---|---|---|
| 空库升级 | v1 空库 | 迁移成功，schema 匹配 `2.json` |
| 真实旧数据 | v1 + 食材/菜谱/库存样本 | 行数保留，FK 正确，约束生效 |
| 相邻版本 | v1→v2 | 只执行一次，版本号 2 |
| 连续升级 | v1→v2→v3 | 顺序执行，无跳过 |
| 失败注入 | 错误 SQL/磁盘满 | 抛错、回滚、不删现场 |
| 降级 | v2 库 + v1 APK | 启动门禁拒绝，不清库 |
| 休眠表 | v1 的 11 表 | 结构/数据不变 |
| 幂等/重入 | 迁移中断后重跑 | journal 恢复，最终一致 |

> 测试模板与矩阵是准备物；**没有任何一项被标记为已通过**（当前无 v2）。

## 7. 验收口径

本准备物完成的条件：

- 规范/模板/清单存在且可执行；
- 明确“模板/测试 ≠ 迁移已实施”；
- 未创建 Schema v2、未改 schema/Entity/DAO/迁移/DB 文件；
- 11 张休眠表未被删除/迁移/改造；
- 回档点设计与现有 Git/E05/journal 能力一致；
- 风险与阻塞（MB-D-007 写屏障/checkpoint）写明。

## 8. 未决与风险

| # | 事项 | 状态 |
|---|---|---|
| M-01 | 写屏障/WAL checkpoint 未实现 | **BLOCKED by MB-D-007（PROPOSED）** |
| M-02 | 迁移前自动备份未接线 | 方案已写，未实现 |
| M-03 | 迁移测试仅 2 例且本次未跑（需设备） | 未验证 |
| M-04 | 旧版本拒绝打开新 schema 的启动门禁 | 已有版本检查，未覆盖“迁移中失败”场景 |
| M-05 | 是否永久保留 11 张休眠表 | 待未来决策；当前保留 |

## 9. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 0.1.0 | 2026-11-04 | 建立迁移规范/测试模板/备份回滚清单/回档点设计；声明未实施迁移、未创建 v2 | 接管工程组 |
