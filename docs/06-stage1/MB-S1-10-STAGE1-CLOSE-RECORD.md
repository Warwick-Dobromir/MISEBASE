# MB-S1-10 Stage 1 关闭记录

**文档编号**：MB-S1-10
**版本**：`1.0.0`
**状态**：`CLOSED`
**日期**：2026-11-04
**阶段**：Stage 1（工程范围清理）
**关闭前 HEAD**：`2ccc0adc4d2634e55025a51f0c722f7542cb2350`
**审查依据**：[MB-S1-09 审查回执](MB-S1-09-REVIEW-RECEIPT.md)
**范围**：仅关闭记录。不进入 Stage 2，不修改业务代码。

---

## 1. 关闭结论

**Stage 1 审查通过，Stage 1 关闭。**

- 模块集实际为 13（app + 6 core + 6 feature）；
- 依赖检查 13 节点 / 35 声明边 / 155 import 边 / 0 违规，违规注入全部捕获；
- 11 张休眠表 DORMANT，0 处允许范围外引用，未删/未迁/未改结构；
- 迁移仅准备规范/模板/方案，未创建 Schema v2、未实施迁移；
- 备份/恢复未修复，仅分析与方案；
- 未开发功能页面、未实现扫码 UI、未做视觉定稿、未重排导航；
- JVM 测试 197 例 / 0 失败；Debug APK 构建成功；工作树 clean。

## 2. 审查依据（MB-S1-09 四项补记）

| # | 审查项 | 关闭确认 |
|---:|---|---|
| 1 | 导航变更性质 | 仅删除已删除模块的编译引用，**不是 MB-D-009 导航 IA 决策**；未新增/重排入口；MB-D-009 仍 `PROPOSED` |
| 2 | 实际起点与证据 | Stage 1 实际起点 `f8de1bc`；`stage1-start` 标签 `1ede793`；二者差一个 Stage 0 文档提交；已补 `git diff --name-status f8de1bc..HEAD` 与 `git status --porcelain` |
| 3 | 仪器化测试 | `CulinaryDatabaseMigrationTest` **2 例未运行**；Stage 1 PASS **不包含**该证据 |
| 4 | 备份结论 | 备份/恢复**未修复，仅分析与方案**；S0-R-02、S0-R-11 仍 **OPEN（高）** |

四项补记完整，审查无未闭合的文档项。

## 3. 关闭时实际 HEAD

| 项 | 值 |
|---|---|
| 工作区 | `D:\MISEBASE` |
| 分支 | `misebase/main` |
| 关闭前 HEAD（完整） | `2ccc0adc4d2634e55025a51f0c722f7542cb2350` |
| 工作树 | clean（`git status --porcelain` 空输出） |

该 HEAD 是 Stage 1 审查回执完成后的簿记提交，包含 MB-S1-09 审查回执与 MB-S1-01 的审查补记。

## 4. 决策状态

### 4.1 已接受（5/18）

| 决策 | 内容 |
|---|---|
| MB-D-001 | 接管基线 `705e0ca`；分支 `misebase/main`；项目名 MISEBASE；包名暂留 |
| MB-D-002 | 五项功能；采购清单归 Inventory；计划/采购不独立；P01 Superseded |
| MB-D-003 | 目标 13 模块；删除 planning/procurement；products 缩减 |
| MB-D-005 | 11 张休眠表保留并标 DORMANT；不删/不迁/不建 v2 |
| MB-D-018 | 纯文档修正；A-G2-001 用 Errata；不改历史正文 |

### 4.2 仍为 PROPOSED（13 项，不得当作已批准）

`MB-D-004`、`MB-D-006`、`MB-D-007`、`MB-D-008`、`MB-D-009`、`MB-D-010`、
`MB-D-011`、`MB-D-012`、`MB-D-013`、`MB-D-014`、`MB-D-015`、`MB-D-016`、`MB-D-017`。

其中：

- **MB-D-004** 扫码 UI 归属：未新建 `feature:scan`，未实现扫码 UI；
- **MB-D-007** 备份一致性：未改备份/恢复实现；
- **MB-D-008/009** UI 视觉/导航：未做视觉定稿、未重排导航。

## 5. 关闭边界

| 项 | 关闭状态 |
|---|---|
| 业务代码 / Compose 页面 / 导航 / 主题 | 未修改 |
| `settings.gradle.kts` / Gradle 依赖 / 模块集 | 未修改（仍为 13） |
| Schema / Entity / DAO / 迁移 / 数据库文件 | 未修改；无 Schema v2 |
| 备份/恢复实现 | 未修改；未修复，仅分析与方案 |
| G2 历史报告正文 | 未修改 |
| G3-1 代码 | 未恢复、未修改 |
| 功能页面 / 扫码 UI / 视觉定稿 / 导航重排 | 未新增、未实施 |

## 6. 关闭提交与标签

| 项 | 值 |
|---|---|
| 关闭提交 | `docs(stage1): close stage 1 after review`（完整哈希见 §6.1） |
| `checkpoint/misebase-stage1-closed-20261104` | tag object `dc5a6ba` → commit `b6a6d80` |
| `checkpoint/misebase-stage2-start-20261104` | tag object `c6fa133` → commit `b6a6d80` |

### 6.1 关闭提交哈希

```text
b6a6d8038e01e888f84b193a4a0f5e34893db3a6
```

### 6.2 新标签解析

```text
checkpoint/misebase-stage1-closed-20261104
  tag object: dc5a6ba1bb7f7924864ca0d49f434062bbee4c38
  commit:     b6a6d8038e01e888f84b193a4a0f5e34893db3a6

checkpoint/misebase-stage2-start-20261104
  tag object: c6fa133d460de4edfe8fb4d05e57d71d989ac869
  commit:     b6a6d8038e01e888f84b193a4a0f5e34893db3a6
```

## 7. 旧标签不可移动

| 标签 | 标签对象 | 指向提交 | 本轮 |
|---|---|---|---|
| `framework-0.1.0` | `2b523de`（轻量） | `2b523de` | **未移动** |
| `checkpoint/misebase-stage0-closed-20261104` | `23c5dd4` | `1ede793` | **未移动** |
| `checkpoint/misebase-stage1-start-20261104` | `0ea67d3` | `1ede793` | **未移动** |
| `checkpoint/misebase-stage1-20261104` | `4010536` | `93d0d92` | **未移动** |
| `checkpoint/misebase-stage1-reviewed-20261104` | `2dff911` | `22851dc` | **未移动** |

## 8. 未决事项（随 Stage 1 关闭保留）

| # | 事项 | 状态 |
|---:|---|---|
| 1 | MB-D-007 未签收 | 备份一致性不得实现 |
| 2 | S0-R-02 WAL/备份一致性 | OPEN（高） |
| 3 | S0-R-10 恢复中断 | PARTIAL |
| 4 | S0-R-11 自动备份假成功 | OPEN（高） |
| 5 | 仪器化迁移测试 2 例 | 未运行，需设备 |
| 6 | 迁移实施 / Schema v2 | 未授权、未实施 |
| 7 | MB-D-004/008/009 | 扫码 UI/视觉/导航仍待决策 |
| 8 | 休眠表长期保留 | 未处置，结构冻结 |

## 9. Stage 2 启动条件

Stage 1 已关闭，**具备 Stage 2 启动条件**，但：

- 本任务**不进入 Stage 2**；
- 不编写 Stage 2 任务书；
- Stage 2 必须以单独任务书与授权启动；
- 启动前不得把上述 PROPOSED 决策当作已批准。

## 10. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0.0 | 2026-11-04 | Stage 1 审查通过并关闭；记录关闭前 HEAD、决策状态、边界、标签与未决事项 | 接管工程组 |
