# CULINARY G2 阶段收档文件

**文档编号**：A-G2-001
**版本**：`1.0.0`
**状态**：`Archived`（G2 已收口；本文件为最终归档记录）
**日期**：2026-11-04
**基线**：`framework-0.1.0`（`2b523de`）
**依据**：[R01 框架实施顺序](../04-readiness/R01-FRAMEWORK-SEQUENCE.md)、[决策登记册](DECISION-REGISTER.md) 0.20.0、R11–R19 阶段报告

---

## 1. 文件效力

1. 本文件完整记录 G2 的范围、交付、验收证据、基线与遗留事项，作为 G2 的最终归档依据。
2. G2 已关闭，不因遗留事项重新开启；任何重新打开需项目负责人单独授权。
3. 已验收的阶段基线不得修改、移动或覆盖。
4. G2 期间尝试的 G3 规划与 G3-1 代码已按项目负责人指示丢弃，不属于本档案范围，也不构成 G2 基线的一部分。

## 2. G2 目标与结果

**目标**：在已签收的 G1 成果上，按 R01 顺序完成工程骨架、核心契约、数据库、数据层、平台能力、应用外壳、
食材纵向切片、算法验证与质量门禁，形成可构建、可安装、可回档的 Android 框架基线。

**结果**：阶段 1–9 全部完成并逐阶段验收，`framework-0.1.0` 冻结于 `2b523de`，工作区 clean。

## 3. 阶段验收总表

| 阶段 | 名称 | 报告 | 基线提交 | 主要交付 | 测试证据 |
|---|---|---|---|---|---|
| 1 | 工程骨架 | [R11](../04-readiness/R11-G2-STAGE1-REPORT.md) | `11b8862` | 15 模块 DAG、版本目录、约定插件、可构建/可安装 | `assembleDebug` 通过 |
| 2 | 核心契约 | [R12](../04-readiness/R12-G2-STAGE2-REPORT.md) | `bd99de3` | `Outcome`/`CulinaryError`/`CUL-*`、领域模型与端口、单位/配方图纯 JVM 规则 | 24 例契约测试 |
| 3 | 数据库 v1 | [R13](../04-readiness/R13-G2-STAGE3-REPORT.md) | `553a49f` | schema v1（31 表）、外键/WAL/busy_timeout、15 条单位种子、部分唯一索引与追加保护触发器、schema 导出 | 77 例数据库测试 |
| 4 | 数据层 | [R14](../04-readiness/R14-G2-STAGE4-REPORT.md) | `1620ef2` | Repository/Mapper、单写者事务协调、BUSY 重试、原子图片存储、E05 备份归档/校验、恢复 journal 与预检 | 34 例数据层测试 |
| 5 | 平台能力 | [R15](../04-readiness/R15-G2-STAGE5-REPORT.md) | `4f023ef` | WorkManager 调度、相机权限三态、SAF 适配、CameraX + bundled ML Kit 解码 | 19 例平台测试 |
| 6 | 应用外壳 | [R16](../04-readiness/R16-G2-STAGE6-REPORT.md) | `f1dc6ab` | 导航、启动版本门禁、兼容性/Recovery 页、崩溃日志、调试入口 | 164 例（累计）+ 设备实测 |
| 7 | 食材切片 | [R17](../04-readiness/R17-G2-STAGE7-REPORT.md) | `5522692` | 食材列表/新建/编辑/归档全链路（UI→用例→Repository→Room） | 167 例（累计）+ 设备 E2E |
| 8 | 算法验证 | [R18](../04-readiness/R18-G2-STAGE8-REPORT.md) | `18a5aa8` | 配方展开、需求汇总、缺口计算、快照 payload；200 节点 < 500 ms | 183 例（累计） |
| 9 | 质量门禁 | [R19](../04-readiness/R19-G2-STAGE9-REPORT.md) | `2b523de` | 迁移测试、R8 Release、恢复完整流程演练、离线冒烟、依赖规则、基线 | 188 例（累计） |

## 4. 最终基线

| 项 | 值 |
|---|---|
| 框架基线 | `framework-0.1.0` |
| 指向提交 | `2b523de` |
| 阶段基线 | `baseline/g2-stage1-20261104`（`11b8862`）… `baseline/g2-stage9-20261104`（`2b523de`） |
| 工作区 | clean |
| 决策登记册 | `docs/00-governance/DECISION-REGISTER.md`，版本 `0.20.0` |

核验命令：

```text
git rev-parse --short framework-0.1.0        # 2b523de
git for-each-ref --format='%(refname:short) %(objectname:short)' refs/tags
git status --short                            # 空输出
```

## 5. 架构与模块

15 个真实 Gradle 模块：

`app`、`core:{common,domain,database,data,designsystem,platform}`、
`feature:{ingredients,sauces,recipes,products,inventory,planning,procurement,settings}`。

**已确认边界**：

- 领域模型在 `core:domain/model`，不建独立 `core:model`；
- `core:domain` 不依赖 Android/Room/Compose；
- `core:database` 不依赖 `core:domain`；
- feature 之间零直接依赖；
- UI 不直接依赖 `core:data`/`core:database`；
- 平台能力在 `core:platform`，备份格式/校验/恢复编排在 `core:data`；
- `core:data` 不直接依赖 Android SAF。

依赖方向由 `scripts/check-module-deps.ps1` 断言（15 模块、43 条允许边、无 `core:model`），G2 收口时 PASS。

## 6. 关键决策（详见登记册 0.20.0）

| 决策 | 内容 | 状态 |
|---|---|---|
| D-004 | 15 模块 DAG 与依赖方向 | ACCEPTED |
| D-005 | 业务主键 TEXT、小写 UUID v4 | ACCEPTED |
| D-006 | 菜谱版本不可变、子配方固定版本 | ACCEPTED |
| D-007 | 计划项固定 `recipe_version_id`、确认生成不可变快照 | ACCEPTED |
| D-008 | 单位换算规则（维度/食材级显式换算） | ACCEPTED |
| D-011 | E05 备份格式与恢复规范 | ACCEPTED |
| D-012 | Hilt 2.57.2（不使用 2.59+） | ACCEPTED |
| D-013 | CameraX 1.4.2 + ML Kit 17.3.0 bundled | ACCEPTED |
| D-015 | 图片存私有目录 + `image_asset` 元数据，不存 BLOB | ACCEPTED |

其余决策以登记册为准；G2 收口时登记册版本为 `0.20.0`。

## 7. 数据库与数据契约

- Room 2.8.5，schema v1 共 **31 张表**，导出
  `core/database/schemas/com.culinary.core.database.CulinaryDatabase/1.json`；
- `PRAGMA foreign_keys=ON`、WAL、`busy_timeout=5000`、`synchronous=NORMAL`；
- 业务主键 TEXT（UUID v4），不使用自增整数；
- 约束补充：部分唯一索引（在用名称）与只追加触发器（流水/快照保护），
  由 `CulinarySchemaExtras` 在每次打开时以 `IF NOT EXISTS` 重放；
- 单写者：`Mutex` + 单线程事务执行器 + BUSY 有限重试（3 次，50/150/400 ms）；
- 不可 destructive migration；迁移失败/数据库损坏进入 Recovery，不自动清空。

## 8. 质量证据（G2 收口时实测）

| 项 | 结果 |
|---|---|
| 工程测试 | **188 例**：domain 40 / database 77 / data 43 / platform 19 / app 6 / feature 3 |
| ktlintCheck | PASS |
| detekt | PASS |
| lintDebug | PASS |
| test | PASS |
| assembleDebug | PASS |
| assembleRelease（R8 + shrinkResources） | PASS |
| Release APK | 23,533,968 bytes；`mapping.txt` 已生成、未入库 |
| 数据库迁移测试 | 2 例，模拟器通过（MigrationTestHelper） |
| 恢复演练 | 5 例：文件级 2 + 完整流程 3（成功/失败回滚/journal 中断恢复） |
| 离线冷启动 | PASS（断网后进入首页） |
| APK 权限 | 无 `INTERNET` |
| 模块依赖检查 | PASS |

复现命令（JDK 21、Android SDK 36）：

```text
./gradlew ktlintCheck detekt lintDebug test assembleDebug
./gradlew :app:assembleRelease
./gradlew :core:database:connectedDebugAndroidTest
powershell -File scripts/check-module-deps.ps1
```

## 9. 安全与恢复能力边界（G2 基线状态）

**已具备**：

- E05 归档格式（manifest/checksums/manifest.sha256）、ZIP 结构与 zip-bomb 防护；
- 恢复预检（版本过高拒绝、PARTIAL 二次确认、较旧 schema 仅允许已测试迁移路径）；
- 恢复 journal（INTENT/DONE、原子写）、文件级恢复、完整流程恢复（关闭连接、清 sidecar、
  `integrity_check`、失败回滚、中断恢复）；
- 图片原子写入与缺失降级；崩溃日志与启动 Recovery。

**G2 基线未具备（遗留）**：在线快照/checkpoint、附件随备份恢复、备份加密、活跃 WAL 场景验证、
恢复 UI 接线。这些在 G2-U 台账中登记，不得视为已完成。

## 10. 未闭合事项台账（G2-U）

| 编号 | 事项 | 状态 |
|---|---|---|
| G2-U01 | GitHub CI 远端实跑 | BLOCKED（无远端仓库/凭据，无 `gh`） |
| G2-U02 | 扫码设备级验证 | 未完成 |
| G2-U03 | SAF 设备级验证 | 未完成 |
| G2-U04 | 自动化 Compose/仪器化 E2E | 未实现 |
| G2-U05 | 恢复 UI 接线 | 未完成 |
| G2-U06 | E05 在线快照/checkpoint | 未实现、未验证 |
| G2-U07 | 图片附件完整恢复 | 未验证 |
| G2-U08 | 备份加密 | 未实现 |
| G2-U09 | 活跃 WAL 场景恢复验证 | 未验证 |
| G2-U10 | 场景 3 来源建模 | 未扩展 |
| G2-U11 | 商品映射 | 未扩展 |
| G2-U12 | 快照元数据扩展 | 未扩展 |
| G2-U13 | APK/mapping 入库归档 | 未完成 |

> 以上为 G2 基线状态。后续任何阶段是否处理，由新阶段授权与计划决定，不自动继承。

## 11. 交付物索引

| 类别 | 位置 |
|---|---|
| 阶段报告 | `docs/04-readiness/R11`–`R19` |
| 决策登记册 | `docs/00-governance/DECISION-REGISTER.md` |
| 数据库 schema | `core/database/schemas/.../1.json` |
| 备份/恢复实现 | `core/data/.../backup/` |
| CI 工作流 | `.github/workflows/ci.yml` |
| 依赖规则脚本 | `scripts/check-module-deps.ps1` |
| 构建产物 | `app/build/outputs/apk/{debug,release}/`（未入库） |

## 12. 重新打开 G2 的条件

出现以下任一情况，需项目负责人书面授权并形成新决策，方可修改 G2 基线：

1. 已验收阶段发现数据损坏、安全问题或验收证据不成立；
2. 需要修复影响 G2 基线可构建/可安装的缺陷；
3. 需要对已冻结的 schema v1 做不兼容变更；
4. 需要追溯修改阶段报告或验收结论。

## 13. 签署

| 角色 | 结论 | 日期 |
|---|---|---|
| 工程组 | G2 交付完成，按本文件归档 | 2026-11-04 |
| 项目负责人 | 待签署 | |

---

**G2：ARCHIVED**
**归档基线：`framework-0.1.0` / `2b523de`**
