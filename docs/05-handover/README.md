# MISEBASE 阶段 0 接管资料包

**文档编号**：MB-S0-00
**版本**：`0.2.0`
**状态**：`Review`（Stage 0 工程核验已完成；Stage 1 待负责人签收）
**日期**：2026-11-04
**适用基线**：G2 `framework-0.1.0` = `2b523de`；接管工作树 = `705e0ca`（G2 收档提交）
**项目**：MISEBASE / Culinary Records and Inventory System

---

## 1. 本资料包是什么

这是 MISEBASE 新工作区启动后的**接管审查与准备资料**，对应总任务书第 29 节要求的 9 项启动工作。

本资料包只做四件事：

1. 核验 G1/G2 交接资料的真实性、完整性与可复现性；
2. 把总任务书的声明与仓库实际逐项对照，标出差异；
3. 建立接管基线、回档制度、模块审查表、UI 设计书骨架与决策登记册；
4. 把必须由项目负责人决定的事项集中列出。

**本资料包不修改业务代码、不修改数据库、不删除历史代码、不恢复 G3、不升级依赖、不开始五项业务功能开发。**

---

## 2. 一句话结论

> G1/G2 资料真实、可校验，G2 代码基线可独立构建；但 **G2 是「框架 + 食材 CRUD」基线，不是可用产品**。五项业务功能中只有食材库有界面，酱汁/菜谱只有数据库结构，库存只有算法，扫码只有平台代码，设置只有占位。接管核验已完成，但**范围冻结、模块缩减、数据库残留处置、备份一致性、UI 方向**必须先由项目负责人签字，Stage 1 才能启动。

---

## 3. 已核验的关键事实

| 项 | 结果 | 证据 |
|---|---|---|
| G1 压缩包 | SHA-256 与随附校验文件一致 | `AB17AF9C…ABBE8` |
| G2 资料文件 | 295 个条目按 SHA-256 + 大小全部匹配，0 缺失 / 0 多余 | `G2-SHA256-MANIFEST.txt` |
| Git bundle | 21 个 refs；`framework-0.1.0` = `2b523de`；HEAD = `850ea63` | `git bundle list-heads` |
| 源码 ZIP | 380 个文件与 `2b523de` 工作树逐字节一致 | 0 哈希不匹配 |
| 模块 | 15 个 Gradle 模块；依赖检查 PASS（15 节点 / 43 边） | `settings.gradle.kts`、检查脚本 |
| 数据库 | 31 张表；schema v1；identityHash `b75e6e1b0cb2ce5b1eb24bad59e0a987` | schema `1.json` |
| 测试 | 197 例 / 0 失败；39 个测试类 | 构建产物 + XML |
| Debug APK | 36,603,939 字节；无 `INTERNET` | 构建 + 交接证据 |
| 独立构建 | `test assembleDebug` exit 0 | 2026-11-04 本机 |

---

## 4. 必须先解决的问题

| # | 问题 | 性质 | 需要谁决定 |
|---|---|---|---|
| 1 | P01 仍把「商品 / 库存 / 制作计划 / 采购」作为四个执行模块，并把采购单、部分到货列为 Must；新任务书只保留五项功能，采购清单归入库存 | 产品范围冲突 | 项目负责人 |
| 2 | G2 的 D-004 锁定 15 模块；新任务书要求缩减 Planning、Procurement、Products | 架构决策冲突 | 项目负责人 |
| 3 | G2 schema v1 中有 11 张表服务于规划/采购/供应商，新范围没有对应入口 | 数据库残留处置 | 项目负责人 + 架构 |
| 4 | G2 备份未实现「备份前 checkpoint」，WAL 下备份一致性无保证（G2-U06） | 数据安全高风险 | 项目负责人 + 架构 |
| 5 | G2 只有食材一个界面；设计系统只有 2 个主题文件 | UI/设计系统缺口 | 项目负责人（视觉方向） |
| 6 | R11/R12 仍为 Review；登记册无 G2-S1；「R11–R19 均 ACCEPTED」不成立 | 历史验收口径 | 项目负责人 |
| 7 | README 仍写「数据库尚未创建」「188 例」「D-005/D-007 仍 PROPOSED」 | 文档失真 | 本阶段修正 |
| 8 | 包名仍为 `com.culinary`，仓库无 MISEBASE 品牌记录 | 品牌/包名 | 项目负责人（D-017 OPEN） |

---

## 5. 文档清单

### 5.1 Stage 0 正式交付（MB-S0-001）

| 文档 | 内容 |
|---|---|
| [S0-01 工程基线核验](S0-01-BASELINE-REPORT.md) | Git、交接资料、源码、构建、测试、技术栈 |
| [S0-02 项目结构与架构](S0-02-ARCHITECTURE-REPORT.md) | 15 模块 vs 13 目标、依赖、31 表、导航、页面 |
| [S0-03 决策状态审查](S0-03-DECISION-REVIEW.md) | D-001–D-025 继承 + MB-D-001–018 状态 |
| [S0-04 风险登记](S0-04-RISK-REGISTER.md) | 15 项工程风险（条件/影响/状态/措施/验证） |
| [S0-04A 文档一致性](S0-04A-DOCUMENT-CONSISTENCY.md) | README/P01/A03A/A-G2-001 修订方案 |
| [S0-05 负责人签收](S0-05-OWNER-SIGNOFF.md) | 18 项决策签收表与 Stage 1 放行条件 |
| [S0-06 Stage 0 报告](S0-06-STAGE-REPORT.md) | 完成结论、证据、阻塞与下一步 |

### 5.2 支撑材料

| 文档 | 内容 |
|---|---|
| [MB-S0-01 接管审查报告](MB-S0-01-INTAKE-AUDIT.md) | 完整性、事实核对、差异、风险、接管结论 |
| [MB-S0-02 范围冻结建议](MB-S0-02-SCOPE-FREEZE.md) | 五项功能的边界、排除项、与 P01 的冲突 |
| [MB-S0-03 资料与决策需求](MB-S0-03-MATERIAL-REQUESTS.md) | 已提供/仍缺失的资料、待负责人决定的事项 |
| [MB-S0-04 接管检查清单](MB-S0-04-TAKEOVER-CHECKLIST.md) | 逐项 PASS/PARTIAL/BLOCKED 与验收条件 |
| [MB-S0-05 模块职责审查表](MB-S0-05-MODULE-RESPONSIBILITY.md) | 15 模块现状、处置建议、目标模块集、数据库归属 |
| [MB-S0-06 代码安全与回档规范](MB-S0-06-SAFETY-ROLLBACK.md) | 基线/标签、变更范围、迁移、锁、备份、回滚手册 |
| [MB-S0-07 决策登记册](MB-S0-07-DECISION-REGISTER.md) | 继承决策、待决事项、建议默认值、签收表 |
| [MB-S0-08 项目文件索引](MB-S0-08-FILE-INDEX.md) | 交接资料、仓库文档、新文档的路径与权威性 |
| [MISEBASE-UI-DESIGN-SPEC](../design/MISEBASE-UI-DESIGN-SPEC.md) | 22 节 UI 设计书骨架（视觉方向待确认） |

---

## 6. 当前阶段状态

| 项 | 状态 |
|---|---|
| 资料完整性 | **PASS** |
| 接管仓库与标签 | **PASS**（`misebase/main` @ `705e0ca`，19 个 G2 标签保留） |
| 独立构建 | **PASS**（本机 `test assembleDebug`） |
| 范围冻结 | **BLOCKED**（待负责人签字） |
| 模块缩减 | **BLOCKED**（待负责人签字） |
| 数据库残留处置 | **BLOCKED**（待负责人签字） |
| 备份一致性 | **BLOCKED**（待 MB-D-007 签字） |
| UI 设计方向 | **PENDING**（待视觉参考与确认） |
| Stage 0 工程核验 | **PASS**（S0-01–S0-06 已提交） |
| Stage 1 启动 | **未授权**（需 5 项签字：MB-D-001/002/003/005/018） |

---

## 7. 复现命令

```powershell
# 资料完整性
Get-FileHash -Algorithm SHA256 'D:\A-1\CULINARY项目\07-交接包\CULINARY-G1-ARCHIVE-20261104.zip'
git bundle list-heads 'D:\A-1\CULINARY项目\07-交接包\G2-交接资料\02-Git基线与历史\G2-完整仓库.bundle'

# 接管仓库
git -C D:\MISEBASE status --short --branch
git -C D:\MISEBASE rev-parse --short framework-0.1.0
git -C D:\MISEBASE tag -l

# 构建
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat test assembleDebug
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-module-deps.ps1
```

---

## 8. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 0.1.0 | 2026-11-04 | 建立阶段 0 接管资料包；完成资料核验、事实对照、风险与待决事项 | 接管工程组 |
| 0.2.0 | 2026-11-04 | 增加 MB-S0-001 六份正式交付与文档一致性报告；更新 Stage 0/Stage 1 状态 | 接管工程组 |
