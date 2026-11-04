# MB-S4-01 Stage 4 库存与自动采购清单执行报告

**文档编号**：MB-S4-01
**版本**：`1.0.0`
**状态**：`Review`
**日期**：2026-11-05
**范围**：库存总览/详情/入库/出库/调整/流水、需求汇总与缺口、采购清单、计算记录业务接线、酱汁图展开、酱汁编辑器组成行保护、N1 库存入口切换
**阶段结论**：提交负责人审查；**不关闭 Stage 4，不进入 Stage 5**。

---

## 1. 执行摘要

Stage 4 在 Schema v2（33 表）、13 模块、三大档案库与 MB-D-007 备份一致性已关闭的基础上，完成 Inventory 纵向切片：

- 库存总览按商品聚合批次，展示在手、预留、可用、位置与最近到期；支持搜索与“仅看缺货”；
- 商品详情展示全部批次、位置、数量、追加式流水与关联菜谱；
- 入库（商品或食材）、出库（领用/损耗/其他）、盘点调整全部经 `TransactionRunner.write` 写入余额与流水；
- 负库存与超过可用量的出库被拒绝；同一幂等键不会重复过账；
- 需求汇总按目标份数展开菜谱，递归展开酱汁图，汇总食材需求并对比可用库存；
- 可选配料按 MB-D-015 默认不进入缺口与采购清单，在详情中单独展示；
- 采购清单按 MB-D-016 按需计算、展示、导出 CSV，不写任何 `purchase_order*` 表；
- 用户确认扣减或生成清单时写入 `calculation_record` / `calculation_record_line`；确认后不可变，可按记录回放；
- Stage 3 遗留 `CUL-REP-004` 已消除；酱汁编辑器对子菜谱/子酱汁组成行提供只读保护，保存时原样回写；
- N1 库存底部入口切换为真实页面；扫码仍为占位。

验证结果：**660 次 JVM 测试执行 / 416 个唯一用例 / 0 失败**；`assembleDebug` 成功；`ktlintCheck`、`detekt` 全仓通过。未修改 Schema、Entity、表结构、迁移、数据库文件、备份/恢复实现。

---

## 2. 启动前置检查与决策状态

### 2.1 启动前置检查

| # | 检查项 | 结果 |
|---:|---|---|
| 1 | `git status --porcelain` 为空；记录 HEAD | **PASS**：空；HEAD `01b2060ae444fd30262b6db0dfba9379e17a2bff` |
| 2 | HEAD 等于或严格后继于 Stage 4 起点 | **PASS**：`checkpoint/misebase-stage4-go-20261104` 解析为 `e76c452911da5141e7ab6d1b76b1246ade96d05d`，为 HEAD 祖先 |
| 3 | S0-05 / MB-S0-07 / README 决策状态 | **PASS**：13 项 ACCEPTED；MB-D-007 为 CLOSED |
| 4 | Schema v2 / 33 表 / `servings` / 计算记录 | **PASS**：`2.json` version=2、33 实体；`recipe_version.servings`、`calculation_record`、`calculation_record_line` 存在；`1.json`/`2.json` 均存在 |
| 5 | 模块集 13；无 `feature:scan` | **PASS**：app + 6 core + 6 feature；`feature/scan` 不存在 |
| 6 | 备份/恢复未修改；启动期望版本 | **PASS**：`StartupCoordinator.EXPECTED_SCHEMA_VERSION = 2`；备份/恢复目录无改动 |
| 7 | 任一不符即停止 | 未触发 |

### 2.2 决策状态（2026-11-05）

| 状态 | 决策 |
|---|---|
| `ACCEPTED`（13） | MB-D-001、002、003、005、007（另为 CLOSED）、008、009、013、014、015、016、017、018 |
| `PROPOSED`（5） | MB-D-004、006、010、011、012 |

本阶段未把任何 PROPOSED 写成 ACCEPTED，未替负责人签收。

---

## 3. 执行前 Git 状态与起点标签

| 项 | 值 |
|---|---|
| 分支 | `misebase/main` |
| 执行前 HEAD | `01b2060ae444fd30262b6db0dfba9379e17a2bff` |
| 工作树 | 空 |
| Stage 4 起点标签 | `checkpoint/misebase-stage4-go-20261104` |
| 起点解析提交 | `e76c452911da5141e7ab6d1b76b1246ade96d05d` |
| `framework-0.1.0` | `2b523de4b87e37a2eb08b4c7c851549350ad811a`，未移动 |
| 既有 checkpoint | 未移动 |

---

## 4. 酱汁图展开

- `RecipeGraphSource` 增加默认酱汁版本查询；`RecipeExploder` 递归处理 `ComponentTarget.Sauce`；
- 子酱汁按“父级引用量 ÷ 酱汁产量”换算后递归；
- 支持 配方→酱汁→配方/酱汁 组合，循环返回 `CUL-REP-001`；
- 缺失酱汁版本返回 `CUL-NF-001`，不静默少算；
- 可选标记沿路径传播，默认排除，`includeOptional=true` 时保留并标记；
- `ExpandRecipeUseCase` 预取配方 + 酱汁组合图；
- 新增 `RecipeExploderSauceTest` 7 例（递归、嵌套、酱汁内子菜谱、循环、缺失、可选、不可换算）。

详见 [MB-S4-06](MB-S4-06-SAUCE-EXPANSION.md)。

---

## 5. 编辑器组成行限制处理

- `SauceDraftLineInput` 从“仅食材”扩展为 `ComponentTarget`；
- 酱汁编辑器显示子菜谱/子酱汁行，标记“只读保留”，不提供数量/可选/删除操作；
- 保存时按原 target 回写，导入数据不再被静默丢弃；
- `GetSauceDetailUseCase` 解析子菜谱/子酱汁名称与版本号；
- 测试覆盖 Domain 保存、ViewModel 载入/保存、Compose 只读提示。

详见 [MB-S4-07](MB-S4-07-EDITOR-COMPONENT-LIMITS.md)。

---

## 6. 库存规则与 Repository

- 新增 `Product` / `StorageLocation` / `InventoryBatchSummary` / `InventoryOverviewRow` / `InventoryProductDetail` / `InventoryLedgerRow` 等领域视图；
- `InventoryRepository` 增补商品/位置读取、批次创建、批次流水查询；写端口仍只有 `appendIfAbsent`，不提供直接改余额或删除流水；
- 入库、出库、调整均在 Use Case 的 `TransactionRunner.write` 内执行；
- `appendIfAbsent` 计算变动后余额/预留，校验可用量，再原子更新批次、追加流水；
- 出库不足返回 `CUL-INV-003`，不写流水；
- 盘点差额为 0 被拒绝；调整不得低于已预留量；
- 幂等键查重先于任何写入；重复确认返回 `idempotentReplay=true`；
- 默认位置“默认储藏”按需创建，首次入库无需预先配置。

---

## 7. 需求汇总与缺口预览

`BuildRequirementPlanUseCase`：

1. 按目标份数调用 `ExpandRecipeUseCase`（含酱汁图）；
2. `RequirementAggregator` 按食材汇总可合并单位；
3. 按食材读取批次可用量，使用食材级换算转换到需求单位；不可换算按 0 可用；
4. `ShortfallCalculator` 生成必需/可选缺口；
5. `PurchaseListBuilder` 生成采购清单；
6. `CalculationRecordFactory` 生成带稳定 `payload_hash` 的确认前记录。

详见 [MB-S4-03](MB-S4-03-REQUIREMENT-GAP.md)。

---

## 8. 采购清单

- 采购清单是即时计算结果，**不落** `purchase_order*` 表；
- 只包含“必需 + 有缺口”的行，按食材 ID、单位稳定排序；
- 页面展示需求、可用、采购量，并可导出固定表头 CSV；
- CSV 通过系统文档选择器写入用户选择的位置，不新增权限。

详见 [MB-S4-04](MB-S4-04-PURCHASE-LIST.md)。

---

## 9. 计算记录业务接线

- 生成采购清单：构建计划 → `confirm(now)` → `saveConfirmed`，同一事务；
- 确认扣减：构建计划 → 缺口检查 → 保存记录 → 生成扣减流水 → 追加流水，同一事务；任一失败整体回滚；
- 记录只接受 `CONFIRMED`；同 ID 不覆盖；同 `payload_hash` 返回既有记录；
- `CalculationEngine.VERSION` 由 `stage2-1` 升至 `stage4-1`（酱汁图算法变更）；
- 列表按菜谱版本读取，回放按记录 ID 读取；页面展示 payload hash 与需求行。

详见 [MB-S4-05](MB-S4-05-CALCULATION-RECORD.md)。

---

## 10. 库存 UI

- 总览：搜索、仅看缺货、入库/流水/需求计划入口、商品行；
- 详情：商品/食材、批次、位置、到期、数量、流水、关联菜谱、出库/调整；
- 入库：商品/食材切换、数量、单位、批次号、到期日、位置；
- 出库：批次、数量、单位、原因、备注、可用量提示；
- 调整：批次、实盘数量、单位、备注；
- 流水：类型筛选、搜索、变化量、前后余额、来源；
- 需求计划：菜谱、份数、缺口、可选明细、采购清单、CSV、确认扣减、计算记录回放。

详见 [MB-S4-02](MB-S4-02-INVENTORY-PAGES.md)。

---

## 11. 导航切换

- `CulinaryDestination.INVENTORY` 仍为 N1 第四个一级入口；
- 该入口指向真实 `InventoryRoute`；
- 新增 `inventory/product/{productId}`、`inventory/ledger`、`inventory/movement/{mode}?batchId=`、`inventory/plan`；
- 扫码入口仍为 `PlaceholderScreen`（MB-D-004 PROPOSED）；
- N1 五入口顺序、设置顶栏未变。

---

## 12. 测试与构建结果

| 项 | 结果 |
|---|---|
| 唯一 JVM 用例 | **416**（基线 376，新增 40） |
| 执行次数 | **660**（基线 612，新增 48） |
| 失败 | **0** |
| `assembleDebug` | **成功**；`app/build/outputs/apk/debug/app-debug.apk` |
| `ktlintCheck` | 通过 |
| `detekt` | 通过 |
| 模块依赖检查 | PASS：13 模块，DAG，无违规 |
| 休眠表扫描 | PASS：11 张表，0 处允许范围外引用 |
| 文档一致性 | PASS |

新增分布（主要套件）：酱汁图 7、编辑器 3、库存规则/Repository 11、需求/采购/计算记录 7、UI/ViewModel 9；以最终唯一用例总数 416 为准。

---

## 13. Git 提交 / 标签 / 变更范围

### 13.1 分提交

| 顺序 | 提交 | 内容 |
|---:|---|---|
| 1 | `490ebbc` | `feat(inventory): expand sauce graph recursively` |
| 2 | `7ce4d35` | `fix(sauces): preserve imported component rows with read-only protection` |
| 3 | `d91ff4c` | `feat(inventory): add stock ledger rules, repository and stock use cases` |
| 4 | `1084ecb` | `feat(inventory): build requirement plans with stock gap preview` |
| 5 | `c3077c8` | `feat(inventory): generate purchase lists on demand and save the calculation snapshot` |
| 6 | `757baba` | `feat(inventory): wire confirmed deductions and calculation record replay` |
| 7 | `6a24d4d` | `feat(inventory): implement inventory overview, detail, stock forms, ledger and planning UI` |
| 8 | `62a1b4f` | `feat(app): switch the inventory bottom entry to the real inventory flow` |
| 9 | `09c58ed` | `fix(inventory): show on-hand and reserved balances on overview rows` |
| 10 | `786250e` | `test(data): cover calculation snapshot writes inside an outer transaction` |

文档提交与本报告在同一检查点前提交；检查点回执见 [MB-S4-10](MB-S4-10-CHECKPOINT-RECEIPT.md)。

### 13.2 范围边界

- Schema/Entity/表结构/迁移/数据库文件：**无变更**；
- 备份/恢复实现：**无变更**；
- 模块：仍 13 个；无 `feature:scan`；
- 休眠表：0 处新业务引用；无 `purchase_order*` 使用；
- 技术栈：未升级/更换；
- 扫码：无识别、导入、CameraX 页面；入口仍占位；
- 计算记录：只新增业务接线，未改表结构。

`git diff --name-status e76c452..HEAD` 摘要见检查点回执。

---

## 14. 未解决问题与风险

| # | 项 | 影响 | 处置 |
|---:|---|---|---|
| 1 | 需求汇总按“食材 + 可合并单位”生成多行；同一食材若存在不可合并单位，可用库存会按行分别计算，可能高估总可用 | 极端单位组合下缺口偏小 | 当前按“不可强行合并”原则保留分行；后续可增加按食材的全局分配 |
| 2 | 资产扣减默认映射到该食材的第一个启用商品 | 多商品/多包装时扣减目标可能不是用户预期 | 当前 UI 展示商品名；后续商品选择/批次选择再细化 |
| 3 | 库存商品在首次入库时按食材自动创建 | 商品主数据较简化 | 不新增商品管理功能；在页面说明中标注 |
| 4 | 计算记录回放按菜谱版本列历史，未做跨版本搜索 | 旧记录发现成本较高 | 记录保存 payload hash 与行；后续可扩展检索 |
| 5 | 未做真机验收（MB-D-011 PROPOSED） | 相机/SAF/真实设备行为未验证 | 留 Stage 6；本阶段仅本地门禁 |
| 6 | 未启用远端 CI（MB-D-010 PROPOSED） | 仅本地验证 | 遵守边界 |
| 7 | 发布签名未创建（MB-D-012 PROPOSED） | 无 Release 签名 | 遵守边界 |
| 8 | `WorkManagerBackupSchedulerTest` 在首次全量运行出现一次既有异步失败，单测重跑通过 | 门禁偶发不稳定 | 未修改备份/平台实现；最终门禁重跑通过 |

---

## 15. 是否具备 Stage 4 审查条件

**具备审查条件（Review）**：

- 启动前置检查全通过；
- 库存九个业务页面/能力完整接入；
- 酱汁图展开与编辑器组成行限制已处理；
- 计算记录确认后不可变、可回放；
- 库存入口为真实页面，扫码仍为占位；
- 无 Schema/Entity/迁移/数据库文件、备份/恢复实现变更；
- JVM 测试 0 失败，Debug APK 构建成功；
- Git 变更在允许范围内。

阶段状态保持 `Review`，等待负责人审查；本报告不自动把 Stage 4 置为 CLOSED。

---

## 16. 停止声明

**未进入 Stage 5。** 未启用远端 CI、未做真机验收、未创建/变更发布签名、未签收任何 PROPOSED 决策。

---

## 17. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0.0 | 2026-11-05 | Stage 4 执行报告，状态 Review | 接管工程组 |
