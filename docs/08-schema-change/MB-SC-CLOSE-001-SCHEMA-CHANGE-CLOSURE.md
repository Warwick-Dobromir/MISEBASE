# MB-SC-CLOSE-001 Schema 变更关闭记录

**文档编号**：MB-SC-CLOSE-001
**版本**：`1.0.0`
**状态**：`CLOSED`
**日期**：2026-11-04
**关闭对象**：Schema v1 → v2（MB-SC-00 授权；MB-SC-01～07 交付）
**审查结论**：**有条件通过**
**范围**：仅关闭记录与报告状态；不进入 Stage 3，不改代码/Schema/备份/导航/模块集

---

## 1. 审查结论

Schema 变更任务 **有条件通过**：

- 主体合规：Schema v2 为 33 表；`recipe_version.servings` 与计算记录持久化结构均已落地；
- 迁移、完整性、Repository、v2 备份恢复、回滚路径有可复现证据；
- JVM 测试 274 例 / 0 失败；Debug APK 构建成功；ktlint/detekt 通过；
- 无重大范围偏离：未修改备份/恢复实现，未开发页面/导航/扫码 UI，未新建模块，
  未进入 Stage 3，未触碰 11 张休眠表结构，未移动既有标签；
- 保留项已记录并接受，见 §5。

本结论为 **CLOSED**，不是 Stage 3 开发授权。

---

## 2. 关闭时实际 HEAD

| 项 | 值 |
|---|---|
| 分支 | `misebase/main` |
| 关闭审查基线 HEAD（实际 HEAD） | `ddece88aa9b58003f57853bf3995f1209fb5dba8` |
| 该 HEAD 的状态 | 报告 `Review`；Schema v2 已实施；工作树为空 |
| 该 HEAD 的 Schema | v2，33 表；`1.json` 未变；`2.json` 已提交 |

关闭动作在此 HEAD 之上只增加文档；不改变 Schema、代码、备份、导航或模块集。

---

## 3. 关闭标签

| 项 | 值 |
|---|---|
| 标签名 | `checkpoint/misebase-schema-change-closed-20261104` |
| 标签类型 | annotated tag |
| 标签对象哈希 | 见最终回执（标签创建后报告） |
| 标签解析提交 | 见最终回执（标签创建后报告） |

标签用于标记“Schema 变更审查关闭”这一文档提交坐标，不是发布标签，也不构成 Stage 3 启动授权。

### 3.1 关闭提交

| 项 | 值 |
|---|---|
| 关闭记录提交 | `6a71f685b4d1d0b89964319751c6837e16a7adb8` |
| 提交信息 | `docs(schema): close schema change after review` |
| 关闭审查基线 HEAD | `ddece88aa9b58003f57853bf3995f1209fb5dba8` |
| 提交内容 | 关闭记录、MB-SC-06 状态与索引；无代码/Schema 变更 |

---

## 4. 标签完整性

关闭前核对：

| 项 | 值 |
|---|---|
| `framework-0.1.0` | `2b523de4b87e37a2eb08b4c7c851549350ad811a`，未移动 |
| 既有 `checkpoint/*` | 25 个（含 `checkpoint/misebase-schema-change-20261104`），全部未移动 |
| 关闭标签 | 新增第 26 个 `checkpoint/*`；不改写任何既有标签 |

关闭前后对 `refs/tags` 全量快照比对，除新增关闭标签外无差异。

---

## 5. 保留项（已接受）

| # | 保留项 | 关闭处置 |
|---:|---|---|
| 1 | 迁移为通过 Room 校验，临时移除 9 个 v1 运行时部分索引，`onOpen` 原样重建 | **已接受**。11 张表 `sqlite_master.sql` 前后一致，9 个索引全部重建；作为迁移技术操作记录在案 |
| 2 | androidTest 未执行（无设备）；`MigrationTestHelper` 仅编译 | **保留**。Stage 6 真机验收必须补跑 `CulinaryDatabaseMigrationTest`，不得以 JVM 测试替代真机结论 |
| 3 | MB-D-007 仍 `PROPOSED`；备份验证为文件级 | **保留**。未修改备份/恢复实现；v2 备份恢复结论不证明生产 WAL 一致性，MB-D-007 签收后另行验收 |
| 4 | 旧 `check-dormant-tables.ps1` 对 3 处 Stage 2 既有注释/测试文本误报 | **保留**。本任务新增业务引用 0；可在 Stage 3 期间清理脚本匹配口径或文本，不在本次关闭中修改业务文件 |

---

## 6. 关闭后的边界

- 不进入 Stage 3；
- 不开发 Compose 页面、导航、视觉定稿、扫码 UI；
- 不新建 `feature:scan` 或其他模块；
- 不修改 Schema、Entity、DAO、Migration、备份/恢复、导航或模块集；
- 不移动 `framework-0.1.0`、既有 checkpoint 或本关闭标签；
- 不把任何 `PROPOSED` 写成 `ACCEPTED`。

---

## 7. 停止声明

Schema 变更任务在审查关闭记录、报告状态更新与关闭标签创建后停止。

**未进入 Stage 3。**

---

## 8. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0.0 | 2026-11-04 | 记录 Schema 变更有条件通过、关闭 HEAD、关闭标签、保留项与边界 | 接管工程组 |
