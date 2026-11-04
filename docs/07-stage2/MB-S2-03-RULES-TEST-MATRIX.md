# MB-S2-03 业务规则与测试矩阵

**文档编号**：MB-S2-03
**版本**：`1.0.0`
**状态**：`Review`
**日期**：2026-11-04
**对应报告**：[MB-S2-01](MB-S2-01-EXECUTION-REPORT.md)

---

## 1. 覆盖总览

| 规则域 | 主要实现 | 测试类 | 用例数 |
|---|---|---|---:|
| 模型不变量 | `model/**` | `DomainModelInvariantsTest` | 15 |
| 单位换算（既有） | `UnitConverter.convert` | `UnitConverterTest` | 10 |
| 单位精度/舍入 | `UnitConverter.display/convertForDisplay` | `UnitConverterDisplayTest` | 8 |
| 配方展开（既有） | `RecipeExploder.explode` | `RecipeExploderTest` | 6 |
| 图校验（既有） | `RecipeGraphValidator` | `RecipeGraphValidatorTest` | 5 |
| 份数缩放 | `RecipeExploder.explodeForServings` | `RecipeExploderServingsTest` | 5 |
| 子配方版本固定（MB-D-013） | `RecipeVersionPinRule` | `RecipeVersionPinRuleTest` | 5 |
| 需求汇总（既有） | `RequirementAggregator` | `RequirementAggregatorTest` | 4 |
| 缺口分配（既有） | `InventoryAllocator` | `InventoryAllocatorTest` | 3 |
| 可选缺料（MB-D-015） | `ShortfallCalculator` | `ShortfallCalculatorTest` | 6 |
| 库存预览/扣减/幂等（MB-D-014） | `InventoryPreviewCalculator`、`InventoryDeductionPlanner`、`ConfirmInventoryDeductionUseCase` | `InventoryDeductionTest` | 8 |
| 采购清单/导出（MB-D-016） | `PurchaseListBuilder`、`CsvPurchaseListExporter` | `PurchaseListTest` | 6 |
| 计算记录快照（MB-D-017） | `CalculationPayloadHasher`、`CalculationRecordFactory`、`ConfirmCalculationRecordUseCase` | `CalculationRecordTest` | 7 |
| Repository 契约 | 实现 + fake | `RepositoryContractTest` | 7 |

合计新增 **67** 例（domain 60 / data 7）；全仓 **264** 例，0 失败。

## 2. 必须覆盖项对照

### 2.1 单位可换算 / 不可换算 / 精度 / 错误

| 场景 | 用例 |
|---|---|
| 同单位直接返回 | `returns the same quantity when the unit matches` |
| 同维度换算（kg↔g） | `converts kilograms to grams within the mass dimension` 等 |
| 跨维度无依据拒绝 | `rejects volume to mass without an ingredient level conversion`（`CUL-UNIT-001`） |
| 食材级显式转换（正/反向） | `applies an ingredient level conversion from cup to gram`、`applies the inverse ...` |
| COUNT 不互转 | `rejects different count units without an explicit conversion` |
| 非法因子/自映射 | `rejects a conversion with a non positive factor...` |
| 展示精度 HALF_UP | `display rounds half up to the unit precision` |
| 零位精度 | `display honours a zero decimal precision unit` |
| 换算后按目标精度舍入 | `convert for display rounds after conversion ...` |
| 近似单位打标 | `convert for display flags approximate source units` |
| 换算失败透传 | `convert for display propagates an unconvertible unit error` |
| 计算保留全精度 | `convert keeps full precision for calculation` |

### 2.2 配方份数缩放 / 递归展开 / 循环引用

| 场景 | 用例 |
|---|---|
| 根配方按倍数缩放 | `scales root ingredients by the requested multiplier` |
| 子配方按产量换算后递归 | `converts a sub recipe reference against the child yield` |
| 深层递归 | `expands 200 nodes within 500 ms` |
| 循环拒绝 | `reports a cycle instead of recursing forever`（`CUL-REP-001`） |
| 缺失版本 | `reports a missing child version`（`CUL-NF-001`） |
| 份数（4→2 / 2→3） | `scales the root recipe from four servings to two`、`scales up when more servings are requested` |
| 子配方经份数缩放 | `scales the sub recipe through the parent servings` |
| 根版本缺失 | `reports a missing root version` |

### 2.3 子配方发布时固定版本 / 已发布不跟随

| 场景 | 用例 |
|---|---|
| 草稿“当前版本”→明确版本 ID | `resolves a current version reference into an explicit version id` |
| 已固定引用原样保留 | `keeps an explicitly pinned child version` |
| 当前版本缺失 | `reports a missing current version` |
| 引用版本不存在 | `reports a referenced version that no longer exists` |
| 已发布父版本不跟随子版本新版本 | `published version keeps the old expansion after the child gets a new current version` |

### 2.4 可选配料缺料

| 场景 | 用例 |
|---|---|
| 可选行默认不入缺口、单独展示 | `optional lines stay out of the default gap and remain visible separately` |
| 显式并入缺口 | `explicitly including optional lines merges them into the gap` |
| 缺口数量计算 | `computes the uncovered quantity per requirement line` |
| 不可换算按 0 可用 | `treats an unconvertible stock unit as zero available` |
| 食材级换算参与可用量 | `applies ingredient level conversions when summing stock` |
| 汇总结果转需求行 | `maps aggregated groups into requirement lines` |

### 2.5 库存预览 / 确认流水 / 负库存 / 幂等

| 场景 | 用例 |
|---|---|
| FEFO 分配与缺口 | `preview allocates fefo and reports available and shortfall` |
| 不足时保留缺口 | `preview keeps the uncovered remainder as shortfall` |
| 批次单位换算 | `preview converts batch units into the requirement unit` |
| 不可换算批次计 0 | `preview treats an unconvertible batch as zero available` |
| 确认后写流水、无预留 | `confirmation appends ledger entries and reduces on hand without reservations` |
| 同引用重复确认不重复写 | `repeated confirmation with the same reference does not double write` |
| 负库存拒绝 | `planner rejects an allocation that would make a batch negative`（`CUL-INV-003`） |
| 预览只读 | `preview is read only` |

### 2.6 采购清单不引用 `purchase_order*`

| 场景 | 用例 |
|---|---|
| 只取非可选且有缺口行 | `builds a purchase list from uncovered required lines only` |
| 稳定排序 | `orders purchase lines deterministically by ingredient and unit` |
| 商品映射 | `attaches an optional product id per ingredient` |
| CSV 表头/行/结尾 | `exports a stable csv with a fixed header`、`exports an empty list with only the header` |
| 领域源码不出现 `purchase_order`（排除注释） | `purchase list domain sources never reference purchase order tables` |

### 2.7 计算记录不可变 / payload_hash 稳定性

| 场景 | 用例 |
|---|---|
| 同输入不同时间/ID → 同 hash | `payload hash is stable for identical inputs regardless of time and id` |
| 份数/引擎版本变化 → hash 变化 | `payload hash changes when servings or engine version change` |
| 需求行变化 → hash 变化 | `payload hash changes when requirement lines change` |
| 只落已确认记录 | `confirmation persists only a confirmed immutable record` |
| 仓库拒绝 PREVIEW | `repository rejects a preview record` |
| 同 ID 不覆盖 | `repository never overwrites an existing record` |
| 同 payload 不重复落 | `duplicate payload hash does not create a second record` |

### 2.8 Repository 契约

| 场景 | 用例 |
|---|---|
| 草稿保存/读回（Room） | `recipe repository persists a draft and reads the pinned components back` |
| 发布后仅一个当前版本（Room） | `recipe repository keeps exactly one current version after publishing` |
| 幂等追加与负库存（Room） | `inventory repository appends idempotently and rejects negative balances` |
| fake 与实现语义一致 | `recipe fake mirrors ...`、`inventory fake mirrors ...` |
| 计算记录 insert-once（fake） | `calculation record fake enforces confirmed only insert once contract` |
| 酱汁契约（fake） | `sauce fake stores a draft version and reports missing versions` |

## 3. 执行结果

```text
.\gradlew.bat test assembleDebug --console=plain --no-daemon
BUILD SUCCESSFUL
264 tests, 0 failures, 0 errors
app-debug.apk 36,856,444 bytes
```

## 4. 未覆盖与说明

| 项 | 原因 |
|---|---|
| 计算记录持久化往返 | Schema v1 无表；只测 DTO/契约/fake，需 Schema 变更授权后补 Room 测试 |
| 酱汁持久化 | 实现留待 Stage 3；本阶段只测契约与 fake |
| `SAUCE` 组成行映射 | 领域目标未建模，Mapper 返回 `CUL-REP-004`；负向路径未建数据库夹具 |
| 仪器化迁移测试 | 需设备；Stage 1 已记录，本阶段未改变 |
