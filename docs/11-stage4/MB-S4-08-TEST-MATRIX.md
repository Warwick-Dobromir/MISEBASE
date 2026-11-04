# MB-S4-08 测试矩阵

**文档编号**：MB-S4-08
**版本**：`1.1.0`
**状态**：`CLOSED`
**日期**：2026-11-05
**口径**：唯一 JVM 用例；执行次数包含 debug/release 重复运行
**关闭依据**：MB-S4-CLOSE-001（有条件通过）

---

## 1. 汇总

| 项 | 结果 |
|---|---|
| 唯一 JVM 用例 | **416** |
| 执行次数 | **660** |
| 失败 | **0** |
| 基线 | 376 唯一 / 612 执行 / 0 失败 |
| 新增唯一用例 | **40** |

命令：

```powershell
.\gradlew.bat test assembleDebug ktlintCheck detekt --console=plain --no-daemon
```

模块依赖、休眠表、文档一致性脚本另跑，均 PASS。

---

## 2. 新增/变更套件

| 套件 | 模块 | 用例数 | 覆盖 |
|---|---|---:|---|
| `RecipeExploderSauceTest` | core:domain | 7 | 酱汁递归、嵌套、酱汁内子菜谱、环、缺失、可选传播、单位 |
| `SauceUseCasesTest`（新增 1） | core:domain | +1 | 保存子菜谱/子酱汁 target |
| `StockMovementUseCasesTest` | core:domain | 8 | 入库批次+流水、幂等、出库扣减、负库存/可用量拒绝、调整、0 差额、事务 |
| `InventoryQueriesTest` | core:domain | 3 | 总览聚合/FEFO 到期、详情关联菜谱、流水标签 |
| `RequirementPlanUseCasesTest` | core:domain | 7 | 酱汁计入汇总、可选分离、不可换算、采购记录、缺口回滚、扣减、幂等、回放 |
| `RepositoryContractTest`（新增 3） | core:data | +3 | 批次创建+入库流水、商品/默认位置、既有 fake 语义 |
| `CalculationRecordRepositoryTest`（新增 1） | core:data | +1 | 外层事务内写计算记录 |
| `SaucesViewModelTest`（新增 1） | feature:sauces | +1 | 三类组成行只读 + 保存保留 |
| `SaucesUiTest`（新增 1） | feature:sauces | +1 | 只读提示与徽标 |
| `InventoryViewModelTest` | feature:inventory | 4 | 总览过滤、流水筛选、出库预选/写入、非法数量 |
| `InventoryUiTest` | feature:inventory | 5 | 总览、详情、入库表单、流水、需求计划 |
| `NavigationShellTest`（改造 1） | app | 1 | 库存顶级页面标题/设置，替代旧占位断言 |

说明：部分套件为既有套件追加用例，上表“+1”表示新增数量，不改变既有覆盖。

---

## 3. 需求覆盖

| 验收点 | 覆盖 |
|---|---|
| 酱汁图展开 | `RecipeExploderSauceTest`、`RequirementPlanUseCasesTest` |
| 编辑器组成行 | `SauceUseCasesTest`、`SaucesViewModelTest`、`SaucesUiTest` |
| 入库/出库/调整 | `StockMovementUseCasesTest`、`RepositoryContractTest`、`InventoryViewModelTest` |
| 余额一致/流水追加 | `StockMovementUseCasesTest`、`RepositoryContractTest` |
| 负库存拒绝 | `StockMovementUseCasesTest`、`InventoryDeductionTest` |
| 幂等键 | `StockMovementUseCasesTest`、`InventoryDeductionTest`、`RepositoryContractTest` |
| 需求汇总/份数缩放 | `RequirementPlanUseCasesTest` |
| 可选配料口径 | `RequirementPlanUseCasesTest`、`ShortfallCalculatorTest` |
| 缺口/FEFO | `InventoryDeductionTest`、`InventoryQueriesTest`、`RequirementPlanUseCasesTest` |
| 采购清单/CSV | `PurchaseListTest`、`RequirementPlanUseCasesTest` |
| 计算记录不可变/hash/回放 | `CalculationRecordTest`、`CalculationRecordRepositoryTest`、`RequirementPlanUseCasesTest` |
| 库存 UI | `InventoryUiTest` |
| ViewModel | `InventoryViewModelTest` |
| 事务边界 | `StockMovementUseCasesTest`、`TransactionBoundaryTest`、`CalculationRecordRepositoryTest` |

---

## 4. 未覆盖/未执行

- 真机 CameraX/SAF 验收：MB-D-011 PROPOSED，留 Stage 6；
- 远端 CI：MB-D-010 PROPOSED，未启用；
- `androidTest` 设备测试：未执行；
- 发布签名：MB-D-012 PROPOSED，未创建。

---

## 5. 已知不稳定项

`WorkManagerBackupSchedulerTest > enqueues an immediate backup and returns its id` 在首次全量运行时出现一次既有异步 `FAILED`，单测重跑通过；该测试与备份/平台实现本阶段未改动。最终门禁以重跑结果为准。

---

## 6. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0.0 | 2026-11-05 | Stage 4 测试矩阵 | 接管工程组 |
| 1.1.0 | 2026-11-05 | MB-S4-CLOSE-001：随 Stage 4 关闭，状态改为 `CLOSED`；测试证据不变 | 接管工程组 |
