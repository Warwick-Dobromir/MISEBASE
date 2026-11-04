# MISEBASE 阶段 0 接管审查报告

**文档编号**：MB-S0-01
**版本**：`0.1.0`
**状态**：`Review`
**日期**：2026-11-04
**审查对象**：G1 归档 `CULINARY-G1-ARCHIVE-20261104.zip`；G2 交接资料 `G2-交接资料`；G2 Git bundle；MISEBASE 总任务书
**审查基线**：G2 `framework-0.1.0` = `2b523de`；接管工作树 `705e0ca`

---

## 1. 审查范围与方法

### 1.1 已阅读的正式材料

- G1：`G1-BASELINE`、`G1-MODULES`、`G1-TO-G2`、`G1-MISSING`、`G1-DECISIONS`、`G1-ISSUES`、`G1-TESTS`、`G1-DATABASE`、`G1-ACCEPTANCE` 及归档清单。
- G2：全部 15 份正式交接报告；决策登记册 0.20.0；A-G2-001；R11–R19；P01；A03A；A07；E02；E04；R19；E05 复核材料。
- G2 源码与构建：`settings.gradle.kts`、`libs.versions.toml`、schema `1.json`、`CulinarySchemaExtras.kt`、`core:domain`、`core:data`、`core:platform`、`feature:ingredients`、导航与主题。
- 交付证据：测试 XML、APK、mapping、构建日志、完整性清单。

### 1.2 核验等级

| 等级 | 含义 |
|---|---|
| **L1 字节级** | 实际计算哈希/逐文件比对，结论可复现 |
| **L2 可执行** | 实际运行构建、测试或检查脚本 |
| **L3 文档级** | 只读取报告/登记册，未独立复算 |
| **未核实** | 仓库无证据，明确标注 |

本报告不会把 L3 写成 L1/L2。

---

## 2. 完整性核验（L1/L2）

### 2.1 G1 压缩包

| 项 | 结果 |
|---|---|
| 文件 | `CULINARY-G1-ARCHIVE-20261104.zip`，281,487 字节 |
| SHA-256 | `AB17AF9CEBD9134B50CC0EAC03CB0D877ADF3CBADF3C72188A33927270EABBE8` |
| 随附校验文件 | 内容一致（大小写不敏感比对通过） |
| 解压结构 | 13 份 G1 报告 + `evidence/` + `snapshot/`（docs/tools） |
| 快照性质 | `git archive 76d6272` 的文档/验证工程副本，**不含产品源码** |
| 结论 | **PASS** |

### 2.2 G2 文件完整性

| 项 | 结果 |
|---|---|
| MANIFEST 条目 | 295（不含 MANIFEST 自身） |
| 交付目录文件总数 | 296 |
| 核验方式 | 对每个文件计算 SHA-256，与 MANIFEST 的「哈希 + 大小」多重集合比对 |
| 缺失 | **0** |
| 多余 | **0** |
| 结论 | **PASS** |

> 备注：MANIFEST 中文文件名在 PowerShell 5.1 默认编码下显示为乱码；按 UTF-8 解码后名称正常，且内容哈希全部匹配。不是文件损坏。

### 2.3 Git bundle

| 项 | 结果 |
|---|---|
| 文件大小 | 707,890 字节 |
| refs | **21**：`main`、`HEAD`、`framework-0.1.0`、9 个 `baseline/g2-stage*`、9 个 `checkpoint/*` |
| `framework-0.1.0` | `2b523de4b87e37a2eb08b4c7c851549350ad811a` |
| HEAD | `850ea63`（G2 正式交接资料提交） |
| `git fsck --full` | 无错误输出 |
| 关键提交 `705e0ca` | 存在（G2 收档提交） |
| 关键提交 `2b523de` | 存在（G2 最终代码基线） |
| 结论 | **PASS** |

### 2.4 源码 ZIP 与基线工作树

| 项 | 结果 |
|---|---|
| 源码 ZIP | `G2-正式基线源码.zip`，640,555 字节 |
| ZIP 内文件数 | 380（不含目录条目） |
| 与 `2b523de` 工作树比对 | 文件集合一致；逐文件 SHA-256 **0 不匹配** |
| 结论 | **PASS**：ZIP 确实是 `2b523de` 的完整工作树 |

### 2.5 独立构建（L2）

| 项 | 结果 |
|---|---|
| 环境 | JDK 21.0.8（`JAVA_HOME`）、Gradle 8.14.3、Android SDK 36、build-tools 36.1.0 |
| 命令 | `.\gradlew.bat test assembleDebug --console=plain --no-daemon` |
| 结果 | **exit code 0** |
| 测试 | 197 例，0 失败，39 个测试类 |
| Debug APK | 36,603,939 字节（与交接记录一致） |
| 权限实测 | `aapt2 dump permissions`：CAMERA / WAKE_LOCK / ACCESS_NETWORK_STATE / RECEIVE_BOOT_COMPLETED / FOREGROUND_SERVICE；**无 INTERNET** |
| 包名 | `com.culinary` |
| 结论 | **PASS**：G2 基线可在本机独立构建 |

### 2.6 数据库与依赖检查（L1/L2）

| 项 | 结果 |
|---|---|
| 模块 | `settings.gradle.kts` 15 个 `include` |
| 依赖检查 | `scripts/check-module-deps.ps1` → **PASS**（15 模块 / 43 边） |
| Schema | version `1`，**31 entities**，identityHash `b75e6e1b0cb2ce5b1eb24bad59e0a987` |
| 部分唯一索引 | 源码中 9 条 `CREATE UNIQUE INDEX`（与报告一致） |
| 触发器 | 报告称 69 条；源码以循环方式生成，**本次未逐条展开复算（L3）** |
| 结论 | 模块/表/索引 **PASS**；触发器数量 **报告值待复核** |

---

## 3. 总任务书声明与实际对照

| # | 任务书/历史声明 | 仓库实际 | 结论 |
|---|---|---|---|
| 1 | G1 是历史基础建设阶段 | G1 有 63 个文件，只有架构/产品/工程文档 + `tools/dependency-spike`；**没有产品源码、31 表、产品测试、APK** | **部分成立**：是设计阶段，不是实现阶段 |
| 2 | G2 最终基线 `2b523de` | 标签 `framework-0.1.0` 指向 `2b523de` | **成立（L1）** |
| 3 | G2 收档 commit `705e0ca` | 存在，且含 `A-G2-001` | **成立** |
| 4 | G2 交接包 HEAD | 实际 `850ea63`；`G2-BASELINE.md` 写 `a4b2237`（旧值） | **文档滞后**：以 bundle 实际为准 |
| 5 | Stage 1–9 已验收 | 登记册有 **G2-S2–S9**；**无 G2-S1**；R11/R12 文件头仍是 `Review` | **部分成立**：S1 无直接验收证据 |
| 6 | R11–R19 均为 ACCEPTED | R11/R12 = `Review`；R13–R19 = `Approved`；A-G2-001 称全部 ACCEPTED | **不成立**：状态口径冲突 |
| 7 | 决策登记册 v0.20.0 | 文件版本 0.20.0，状态仍是 `Review` | **版本成立，状态需说明** |
| 8 | Room 2.8.5 | `libs.versions.toml` = 2.8.5 | **成立** |
| 9 | Schema v1，31 表 | 31 entities，v1 | **成立（L1）** |
| 10 | 15 个 Gradle 模块 | 15 个 `include` | **成立（L1）** |
| 11 | 历史测试 188 项，0 失败 | 本次实测 **197**，0 失败；188 = 漏计 `core:common` 9 例 | **数字需修正**：当前应以 197 为准 |
| 12 | 测试报告 41 个 XML/测试类 | 实际 **39 个测试类**（29 个 Android debug 变体类 + 2 个 common 类 + 8 个 domain 类）；用例 197 = 148 + 9 + 40 | **文档小错**：用例数 197 正确，类数应为 39 |
| 13 | CameraX 1.4.2 + ML Kit 17.3.0 Bundled | 版本目录一致；APK 含 bundled 模型；无 INTERNET | **技术选型成立** |
| 14 | 扫码功能 | 平台代码存在；**无扫码 UI、无设备验证、无仪器化测试** | **未完成** |
| 15 | D-006 不可变配方版本 | schema/触发器/版本表存在；**无酱汁/菜谱 UI 与发布流程** | **DB 层成立，业务层未完成** |
| 16 | D-007 计划项固定版本 + 快照 | schema 与算法存在；**无计划模块 UI/业务** | **仅 schema/算法** |
| 17 | D-011 备份与恢复 | 归档/校验/预检/journal/恢复执行器存在；**在线快照/checkpoint 未实现** | **部分成立，WAL 一致性有缺口** |
| 18 | D-015 图片私有目录 | `LocalImageStorage` + `image_asset` 存在 | **成立（代码/测试）** |
| 19 | 项目已更名 MISEBASE | 仓库仍 `com.culinary`，无 MISEBASE 品牌/包名记录 | **未执行**：D-017 OPEN |
| 20 | G3 不得恢复 | `2b523de`/`705e0ca` 工作树不含 G3-1 代码；G3 只在历史提交中 | **成立** |

---

## 4. 五项业务功能现状

> 判定标准：**用户可见、可完成闭环、有验证证据**。仅有表/算法/平台代码不算完成。

| 功能 | G2 实际 | 缺口 | 对应阶段 |
|---|---|---|---|
| **酱汁库 Sauces** | 3 张表（`sauce`、`sauce_version`、`sauce_component`）；无领域模型、无 Repository、无 UI | 领域模型、版本发布、组成编辑、SOP、详情/列表、子配方引用 | 阶段 2/3 |
| **食材库 Ingredients** | 唯一有完整纵向切片：列表/新建/编辑/归档；3 个 ViewModel 测试 | 详情页、别名/换算 UI、图片、与酱汁/菜谱关联展示 | 阶段 3 完善 |
| **菜谱库 Recipes** | 3 张表 + `Recipe`/`RecipeVersion`/`RecipeComponent` 领域模型 + 展开/汇总/缺口算法；无 UI | 版本编辑/发布、SOP、成品标准、缩放界面、子配方固定版本 | 阶段 2/3 |
| **库存 Inventory** | 3 张库存表 + 预留/流水约束；`RequirementAggregator`/`InventoryAllocator` 纯 JVM 算法；无 Repository、无 UI、无入库/出库 | 库存台账、入库/出库/调整、流水页、需求计算、扣减、缺料清单 | 阶段 2/4 |
| **扫码导入 Scan** | 权限三态、条码解析/格式映射、ML Kit 解码器、CameraX 会话；**无扫码页面/无设备验证** | 扫码 UI、商品匹配/创建、包装规格、映射、确认入库、真机验证 | 阶段 5 |
| **设置 Settings** | 占位模块；无备份/恢复/诊断入口 | 备份/恢复 UI、诊断、偏好 | 阶段 4/6 |

**结论**：G2 完成后，五项功能中**只有食材库达到“可用切片”**；其余四项均未达到用户可用状态。这不是对 G2 的否定——G2 的目标本来就是框架基线——但接管方不能把它当成产品完成度。

---

## 5. 关键冲突与差异

### 5.1 产品范围冲突（最高优先级）

`P01-PRODUCT-BASELINE.md`（0.1.0 / Review）仍定义四个执行管理模块：

- 商品管理（商品、品牌、包装、条码、供应商）；
- 库存管理（批次、位置、预留、流水）；
- 制作计划（计划、明细、配方版本、需求、缺口）；
- 采购管理（采购需求、采购单、部分到货、入库批次）。

并把「采购单、部分到货」列为 **Must**。

新总任务书只保留五项功能，并明确：

- 不做独立复杂规划体验；
- 采购清单是库存模块内部能力，不是第六个业务模块；
- 不做复杂供应商管理、自动采购下单、供应链平台。

**两者不能同时作为有效需求。** 必须由项目负责人明确：新任务书取代 P01 的执行管理部分，P01 相应章节标记 `Superseded`。

### 5.2 模块数量冲突

G2 的 D-004 锁定 15 模块，README 写「不得增删改」。新任务书要求：

- Planning 审查并缩减，不保留独立复杂规划体验；
- Procurement 的必要缺料清单能力归入 Inventory；
- Products 缩减为扫码商品、包装规格及食材映射；
- Scan 的模块归属经架构审查后确定。

这意味着 **D-004 必须被新决策修订**，`scripts/check-module-deps.ps1` 的 15 模块白名单也必须重写。

### 5.3 数据库残留

31 张表中，至少 11 张服务于新范围没有入口的领域：

- `supplier`；
- `production_plan`、`production_plan_item`、`production_plan_snapshot`、`material_requirement`、`material_requirement_source`；
- `procurement_requirement`、`purchase_order`、`purchase_order_item`、`goods_receipt`、`goods_receipt_item`。

不能直接删除（任务书禁止未审计删除、禁止 destructive migration）。需要逐表定性：保留为 dormant、转为计算快照、还是后续迁移删除。

### 5.4 备份一致性缺口

`E02-DATABASE-CONCURRENCY.md` 明确要求：备份前获取写锁并 `PRAGMA wal_checkpoint(TRUNCATE)`。但 G2 基线：

- 有 `BackupArchiveWriter` / `RestoreExecutor` / journal；
- **没有在线快照/checkpoint**（G2-U06，明确“未实现、未验证”）；
- 现有恢复测试使用 `TRUNCATE` journal mode，生产 WAL 只验证了 sidecar 删除。

结论：**“备份文件可校验”不等于“WAL 下备份一致”**。这是阶段 0/1 最高优先级的数据安全风险，不能因为 G2-S9 已验收就忽略。

### 5.5 文档失真

`README.md`（`705e0ca`）仍写：

- 「数据库：尚未创建（阶段 3；D-005 主键策略待确认）」——实际 DB v1 已建，D-005 已接受；
- 「188 例测试」——实际 197；
- 「仍为 PROPOSED：D-005、D-007、D-013、D-015」——四项均已 ACCEPTED；
- 「决策登记册 0.8.0」——实际 0.20.0。

这些是**首次上手就会误判**的错误，必须在阶段 0 修正（只改文档，不改业务代码）。

### 5.6 验收口径不一致

- R11/R12 头部仍 `Review`，结论写“请负责人验收”，无后续更新；
- 登记册无 `G2-S1`；
- A-G2-001 与任务书称 Stage 1–9 全部 ACCEPTED。

处理原则：**不篡改历史报告**。由负责人选择：补签记录、出具说明、或在 MISEBASE 接管文件中声明“以登记册 G2-S2–S9 + A-G2-001 为验收依据，S1 以 A-G2-001 追认”。本资料包不替负责人选择。

---

## 6. 风险登记

| ID | 风险 | 等级 | 影响 | 建议措施 |
|---|---|---|---|---|
| R-01 | WAL 下备份不一致（U06） | **高** | 备份可能缺少已提交事务，恢复后数据回退 | 阶段 1 前补 checkpoint + 写屏障测试；备份前强制校验 |
| R-02 | P01 与新任务书范围冲突 | **高** | 开发按旧文档做出被排除的采购/计划功能 | 负责人签署范围冻结；P01 标记 Superseded |
| R-03 | 模块缩减破坏 D-004/依赖检查 | 中 | 构建失败、边界失守 | 新决策 + 重写依赖检查（按模块集/源码 import） |
| R-04 | 11 张残留表被误用或误删 | **高** | 数据丢失或范围回流 | 逐表定性；冻结 v1；不做 destructive migration |
| R-05 | 扫码/SAF 无设备验证 | **高** | 阶段 5 无法对外宣称可用 | 真机验证列为阶段 5 退出条件 |
| R-06 | Design System 仅 2 个主题文件 | 中 | 页面各自造轮子、视觉不一致 | 先定视觉方向，再建 token/组件 |
| R-07 | README/文档失真 | 中 | 新成员误判现状 | 阶段 0 文档修正提交 |
| R-08 | 无远端 CI（U01） | 中 | 证据不可远端复现 | 提供仓库/凭据；本地门禁不替代 |
| R-09 | 无 Compose/仪器化 E2E | **高** | UI 回归无保护 | 阶段 3 起建立关键路径 UI 测试 |
| R-10 | 包名/品牌未定（D-017） | 低 | 发布前返工 | 发布前确认；数据不受影响 |
| R-11 | Stage 1/2 验收证据缺口 | 中 | 追溯性弱 | 负责人补签或出具说明 |
| R-12 | 默认 PATH 的 JDK 17 与基线 JDK 21 不一致 | 低 | 构建工具版本错误 | 固定 `JAVA_HOME`；文档写明 |

---

## 7. 接管结论

### 7.1 可以继承

- 15 模块的**分层边界与依赖方向**（feature 零依赖、domain 纯 JVM、database 不依赖 domain、data 不依赖 SAF）；
- 31 表 schema v1、外键/WAL/busy_timeout、部分唯一索引、只追加触发器、单位种子；
- `core:common` 的 Outcome/错误码/Clock/IdGenerator/Logger；
- `core:domain` 的单位换算、配方图校验/展开、需求汇总、库存分配、快照 payload；
- `core:data` 的 Repository/Mapper、单写者事务、BUSY 重试、图片原子写、E05 归档/校验/预检/journal/恢复执行器；
- `core:platform` 的权限三态、SAF 适配、CameraX/ML Kit 解码、WorkManager 调度；
- 构建/质量门禁：Gradle 8.14.3、AGP 8.13.0、Kotlin 2.2.20、Hilt 2.57.2、Room 2.8.5、R8；
- 197 例测试与 0 失败证据。

### 7.2 不能继承为“已完成”

- 酱汁、菜谱、库存、扫码、设置的用户可用闭环；
- 扫码/SAF 的真机验证；
- 备份在 WAL 下的一致性；
- 备份/恢复 UI、附件恢复、备份加密；
- Compose/仪器化 E2E；
- 远端 CI；
- MISEBASE 品牌与包名。

### 7.3 接管成立的条件

1. 负责人签署 **范围冻结**（MB-S0-02）与 **模块处置**（MB-S0-05）；
2. 负责人对 **11 张残留表**给出保留/迁移决定；
3. 负责人确认 **备份一致性优先于功能开发**（R-01）；
4. 负责人确认 **197 为当前测试口径**，并处理 R11/R12/S1 的验收说明；
5. 负责人提供/确认 **UI 视觉方向**；
6. 外部审查通过。

在上述条件满足前，阶段 0 保持 **未关闭**，不进入阶段 1 的代码清理。

---

## 8. 证据索引

| 证据 | 路径/命令 |
|---|---|
| G1 校验 | `Get-FileHash CULINARY-G1-ARCHIVE-20261104.zip` |
| G2 MANIFEST | `G2-交接资料/G2-SHA256-MANIFEST.txt` |
| Bundle | `02-Git基线与历史/G2-完整仓库.bundle` |
| 源码 ZIP | `04-G2正式基线源码/G2-正式基线源码.zip` |
| Schema | `core/database/schemas/com.culinary.core.database.CulinaryDatabase/1.json` |
| 决策 | `docs/00-governance/DECISION-REGISTER.md` |
| 收档 | `docs/00-governance/A-G2-001-G2-ARCHIVE.md` |
| 构建 | `.\gradlew.bat test assembleDebug` |
| 权限 | `aapt2 dump permissions app-debug.apk` |

---

## 9. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 0.1.0 | 2026-11-04 | 完成 G1/G2 完整性、源码、构建、文档对照与风险审查 | 接管工程组 |
