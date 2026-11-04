# MB-S2-06 Stage 2 关闭记录

**文档编号**：MB-S2-06
**版本**：`1.0.0`
**状态**：`CLOSED`
**日期**：2026-11-04
**阶段**：Stage 2（核心数据模型与业务规则）
**审查依据**：[MB-S2-01 执行报告](MB-S2-01-EXECUTION-REPORT.md)（提交 `83d1051184fd04a02c59e4cbe87740ebb4eee4a3`）
**关闭前 HEAD**：`3f02592abe91b934c42ea35439cc7fdb0dc67075`
**范围**：仅文档关闭。不进入 Stage 3，不改代码/Schema/备份/导航/模块集。

---

## 1. 审查结论

**Stage 2 审查结论：有条件通过。主体合规，无重大范围偏离。**

| 审查维度 | 结论 |
|---|---|
| 范围 | 合规：只做核心模型/规则/契约/测试；未开发页面、未接 UI、未新建模块 |
| Schema | 合规：v1 / 31 表未变；无 v2、无迁移、无 Entity 字段变更 |
| 休眠表 | 合规：11 张表 0 处业务引用；未删/未迁/未改造 |
| 备份/恢复 | 合规：`AutoBackupWorker`、`BackupArchiveWriter`、`RestoreExecutor`、写屏障、checkpoint 均未改 |
| 决策 | 合规：审查时只依赖 10 项 `ACCEPTED`、8 项 `PROPOSED` 未被写成已批准；后续补签见 §3 |
| 测试/构建 | 合规：264 例 / 0 失败；Debug APK 构建成功；`ktlintCheck`、`detekt`、文档一致性检查 PASS |
| 重大偏离 | 无 |

**通过条件**（2026-11-04 已授权，合并为独立 Schema 变更任务 [MB-SC-00](../08-schema-change/MB-SC-00-AUTHORIZATION.md)；
授权不等于已修改数据库，本轮不实施）：

1. 计算记录持久化：已授权新增表/迁移（待实施）；
2. 配方份数持久化：已授权新增 `recipe_version.servings` 列/迁移（待实施）。

另有两项执行约束（非阻塞条件，Stage 3/4 必须遵守）：

- Repository 写操作必须在 `TransactionRunner.write { }` 内调用（原子性依赖用例事务）；
- `SAUCE` 组成行映射边界：Stage 3 酱汁档案落地前，`RecipeMapper` 遇到该类型返回 `CUL-REP-004`。

## 2. 关闭前状态

| 项 | 值 |
|---|---|
| 关闭前 HEAD（完整） | `3f02592abe91b934c42ea35439cc7fdb0dc67075` |
| 工作区 | `D:\MISEBASE`；分支 `misebase/main` |
| 工作树 | clean |
| 模块集 | 13（app + 6 core + 6 feature） |
| Schema | v1 / 31 表；11 张 `DORMANT` |
| JVM 测试 | **264 例 / 0 失败**（G2 基线 197，Stage 2 新增 67） |
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk`，**36,856,488 字节** |
| 决策状态 | 关闭时 10/18 `ACCEPTED`、8 项 `PROPOSED`；2026-11-04 补签后为 12/18、6 项（§3） |
| 报告状态 | MB-S2-01 `CLOSED`（本记录同步） |

> APK 字节数已统一：MB-S2-01 与 MB-S2-03 均使用最终验证构建的实际值 **36,856,488 字节**。

## 3. 决策状态（关闭时）

| 状态 | 决策 |
|---|---|
| `ACCEPTED`（12） | MB-D-001、002、003、005、008、009、013、014、015、016、017、018 |
| `PROPOSED`（6） | MB-D-004、006、007、010、011、012 |

> 2026-11-04 补记（MB-DECISION-SIGN-002）：MB-D-008（方向 A）、MB-D-009（方案 N1）签收为
> `ACCEPTED`；计数由 10/18 更新为 12/18、其余 6 项。关闭结论与关闭边界不变。

MB-D-013/014/015/016/017 的实施与测试映射见 [MB-S2-05](MB-S2-05-DECISION-MAPPING.md)。

## 4. Schema 变更请求（已授权，未实施）

| # | 请求 | 现状 | 状态 |
|---:|---|---|---|
| 1 | 新增计算记录持久化结构（表或等价方案）+ 迁移 | 只有 `CalculationRecord` DTO、`CalculationRecordRepository` 契约与 fake；无表 | **已授权（[MB-SC-00](../08-schema-change/MB-SC-00-AUTHORIZATION.md)），待实施** |
| 2 | `recipe_version` 增加 `servings` 列 + 迁移 | 领域 `RecipeVersion.servings` 读取按“整批 = 1 份” | **已授权（MB-SC-00），待实施** |

两项已获负责人授权合并为**一次独立 Schema 变更任务**，但**授权不等于已修改数据库**：
当前仍是 Schema v1，未建表、未改 Entity/表结构、未创建 v2/迁移、未改数据库文件。
实施必须按 MB-SC-00 的顺序与迁移规范（MB-S1-05）另立任务执行。

## 5. 关闭边界

| 项 | 状态 |
|---|---|
| 代码 | 冻结于关闭前 HEAD；本关闭提交只含文档 |
| Schema/Entity/迁移/数据库文件 | 未修改 |
| 备份/恢复 | 未修改 |
| Compose 页面/导航/主题/扫码 UI | 未修改；未新建 `feature:scan` |
| 模块集/技术栈 | 未修改 |
| G2 历史报告正文 / G3-1 | 未修改、未恢复 |
| Stage 3 | **未启动**；仅创建起点标签，不构成开发授权 |

## 6. 关闭提交与标签

| 项 | 值 |
|---|---|
| 关闭提交 | `docs(stage2): close stage 2 after review`（完整哈希见 §6.1） |
| `checkpoint/misebase-stage2-closed-20261104` | 见 §6.2 |
| `checkpoint/misebase-stage3-start-20261104` | 见 §6.2 |

### 6.1 关闭提交哈希

```text
a39b3ca7cca3bede69b841d8650bbc13eb4a7334
```

### 6.2 标签解析

```text
checkpoint/misebase-stage2-closed-20261104
  tag object: 34cb267da29d3a7b30b90e647586f91699e7c5dd
  commit:     a39b3ca7cca3bede69b841d8650bbc13eb4a7334

checkpoint/misebase-stage3-start-20261104
  tag object: 42b06b0d4e36828f8578732cc5ae70238fdfdfa5
  commit:     a39b3ca7cca3bede69b841d8650bbc13eb4a7334
```

两个标签均指向关闭提交；后续簿记提交只回填本文件与 MB-S2-01 的哈希，不移动标签。

## 7. 旧标签未移动

| 标签 | 对象 / 解析提交 | 本轮 |
|---|---|---|
| `framework-0.1.0` | `2b523de4b87e37a2eb08b4c7c851549350ad811a`（轻量） | 未移动 |
| `checkpoint/misebase-stage2-start-20261104` | `c6fa133` → `b6a6d8038e01e888f84b193a4a0f5e34893db3a6` | 未移动 |
| `checkpoint/misebase-stage2-20261104` | `c39f874` → `83d1051184fd04a02c59e4cbe87740ebb4eee4a3` | 未移动 |
| `checkpoint/misebase-stage2-decisions-20261104` | `78aaf10` → `99223000d34b15abe3fa8020d5cb7f06ecaab8cf` | 未移动 |
| `checkpoint/misebase-stage1-closed-20261104` | `dc5a6ba` → `b6a6d8038e01e888f84b193a4a0f5e34893db3a6` | 未移动 |

标签总数 30 → 32（仅新增关闭与 Stage 3 起点标签）。

## 8. 未决事项（随 Stage 2 关闭保留）

| # | 事项 | 状态 |
|---:|---|---|
| 1 | 计算记录持久化 Schema 授权 | 已授权（MB-SC-00），待实施 |
| 2 | `recipe_version.servings` Schema 授权 | 已授权（MB-SC-00），待实施 |
| 3 | Repository 事务边界执行 | Stage 3/4 约束 |
| 4 | `SAUCE` 组成行映射 | 待 Stage 3 酱汁档案 |
| 5 | 仪器化迁移测试 2 例 | 未运行，需设备（继承 Stage 1） |
| 6 | S0-R-02 / S0-R-11 备份一致性 | OPEN（MB-D-007 未签收，不得实现） |
| 7 | MB-D-004/008/009 | MB-D-004 `PROPOSED`（扫码 UI 冻结）；MB-D-008/009 已签收（A/N1），Stage 3 任务书内细化 |
| 8 | MB-D-006/010/011/012 | `PROPOSED`；不阻塞，但不得写成已批准 |

## 9. Stage 3 启动条件

- 本记录**只**创建 `checkpoint/misebase-stage3-start-20261104` 起点标签，不启动 Stage 3；
- Stage 3 必须以单独任务书与授权启动；
- 启动前不得把 6 项 `PROPOSED` 当作已批准；Schema 变更任务须在 Stage 3 启动前完成并签收。

## 10. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0.0 | 2026-11-04 | Stage 2 审查“有条件通过”并关闭；记录关闭前 HEAD、决策、Schema 请求、标签与未决事项 | 接管工程组 |
| 1.0.1 | 2026-11-04 | MB-DECISION-SIGN-002：MB-D-008/009 签收后计数 12/18；两项 Schema 请求改为“已授权（MB-SC-00），待实施” | 接管工程组 |
