# MB-S5-04 识别接口与导入确认说明

**文档编号**：MB-S5-04
**版本**：`1.0.0`
**状态**：`Review`
**日期**：2026-11-05
**范围**：`feature:scan` 的识别结果契约、`feature:products` / `feature:inventory` 的接口实现、导入确认页、幂等与补偿
**依据**：MB-D-004（扫码边界与接口原则）、MB-D-014（库存扣减模式）、MB-D-017（快照/不可变历史）

---

## 1. 职责边界（MB-D-004 §2.3 的落地）

| 模块 | 负责 | 不负责 |
|---|---|---|
| `feature:scan` | 扫码页面、CameraX、ML Kit 识别、去抖/一次性消费、确认页 UI 与编排 | 食材创建、商品主数据、库存批次/流水、任何直接写库 |
| `feature:products` | 提供 `ScanIngredientGateway`：复用/新建食材、解析默认单位、带出已映射商品 | 相机、识别、库存余额 |
| `feature:inventory` | 提供 `ScanStockGateway`：入库（批次 + 流水，同一事务，幂等键查重） | 条码识别、食材档案创建 |
| `core:domain` | 识别结果与导入契约、幂等键规则、只读查重用例 | Android/相机/数据库实现 |
| `app` | 导航注册、参数传递 | 业务规则 |

`feature:scan` 的 Gradle 依赖只有 `core:domain` / `core:common` / `core:designsystem`，
不依赖任何 feature；它通过 `core:domain` 的接口调用业务模块（依赖倒置）。
依赖检查脚本对「`feature:scan` → `feature:products`」有专门的违规注入用例。

---

## 2. 数据结构（`core:domain`）

```kotlin
// 识别结果（识别层输出，不写库）
enum class ScanSource { CAMERA, MANUAL }
data class ScanCapture(rawValue: String, format: String?, source: ScanSource)

// 交给业务模块的请求/结果
interface ScanIngredientGateway {                      // 由 feature:products 实现
    suspend fun resolve(request: ScanIngredientRequest): Outcome<ScanIngredientTarget>
}
data class ScanIngredientRequest(capture, existingIngredientId?, newIngredientName?)
data class ScanIngredientTarget(ingredient, product?, unit, created: Boolean)

interface ScanStockGateway {                           // 由 feature:inventory 实现
    suspend fun receive(request: ScanStockRequest): Outcome<ScanStockReceipt>
}
data class ScanStockRequest(capture, target, amount, operationId, note?)
data class ScanStockReceipt(entry, item, idempotentReplay: Boolean)

// 写前查重（只读）
class LookupScanImportUseCase(inventoryRepository) {
    suspend operator fun invoke(capture): Outcome<ScanPreviousImport?>
}
data class ScanPreviousImport(entry, item?)
```

幂等键规则（`ScanImportKey`，**事件级**，见 [MB-S5-08](MB-S5-08-IDEMPOTENCY-FIX.md)）：

```text
key = "scan-import:" + escape(barcode) + ":" + operation_id
escape: '%' -> '%25'，':' -> '%3A'（避免不同条码的键前缀互相包含）
```

> **一次确认 = 一个 operation id**：`operation_id` 在点击「确认导入 / 再次入库」进入提交态时生成，
> 失败重试复用、成功/取消后清空。因此同一商品可以在不同时间多次入库，而同一次确认（含重试、
> 连点、事务重放）只写一次；幂等最终由 `inventory_ledger.idempotency_key` 唯一约束保证。
> MB-S5-002 之前键只由条码决定，第二次购买同款会被永久拦截，已修正。

---

## 3. 两条链路

### 3.1 写前查重（确认页进入时）

```text
ScanConfirmViewModel.init
  └── LookupScanImportUseCase(capture)                 // read-only，按条码前缀取最近一次
        ├── InventoryRepository.ledger()               // 过滤 scan-import:<barcode>:*
        └── InventoryRepository.findItem(batchId)
  └── 命中 → isDuplicate = true → 展示「该商品此前已导入」+ 按钮「再次入库」（**不再禁用**）
```

### 3.2 确认导入（用户点「确认导入 / 再次入库」）

```text
ScanConfirmViewModel.confirm()
  ├── evaluate(): 提交中/已有结果 → 忽略；数量非法/缺目标 → 拒绝（不调用任何写接口）
  └── startSubmit()
        ├── operationId = 缓存的 id ?: idGenerator.newId()   // 首次点击生成
        ├── 先写状态（operationId + isSubmitting = true），再启动协程
        ├── ScanIngredientGateway.resolve()            // feature:products
        │     ├── 已有食材：IngredientRepository.get + InventoryRepository.findProductForIngredient
        │     └── 新建食材：SaveIngredientUseCase（含名称校验；默认单位按质量基准→计数→首行）
        └── ScanStockGateway.receive()                 // feature:inventory
              └── RecordStockReceiptUseCase(...)       // 一个事务：批次 + 流水
                    └── 幂等键 = ScanImportKey.of(capture, operationId)
```

成功 → 清空 `operationId`；失败 → 保留 `operationId` 并展示「重试」（重试复用同一键）。

事务边界：

| 步骤 | 事务 |
|---|---|
| 查重（`LookupScanImportUseCase`） | 无（只读） |
| 新建食材（`SaveIngredientUseCase`） | 自带事务 |
| 入库（`RecordStockReceiptUseCase`） | 自带事务：批次 + 流水 + 商品补建 |

`TransactionRunner` 明确**不可重入**（嵌套会死锁），因此本链路不使用"跨模块单事务"，
而是采用任务书允许的**明确补偿**：

- 若本次新建了食材（`ScanIngredientTarget.created == true`）且入库失败 →
  调用 `ArchiveIngredientUseCase` 归档该食材（软删除，不物理删除），
  错误文案追加「（已撤回本次新建的食材）」；
- 若复用已有食材后入库失败 → **不**归档（它不是本次创建的）；
- 入库成功或命中幂等重放 → 不做补偿。

---

## 4. 幂等与重复确认（MB-S5-002 修正后）

| 场景 | 行为 | 证据 |
|---|---|---|
| 同一条码再次进入确认页 | `isDuplicate = true`，提示上次导入时间与数量，按钮变「再次入库」，**允许继续** | 真机 [12](evidence/12-fix-duplicate-entry.png) |
| 同一次确认重试（失败后点「重试」） | 复用同一 `operationId` → 命中唯一键 → `idempotentReplay = true`，不新增批次/流水 | `ScanConfirmViewModelTest`、`InventoryScanStockGatewayTest` |
| 快速连续点击 | `isSubmitting` 同步置位 + `result != null` 双闸门 + 幂等键兜底，只调用一次 | `ScanConfirmViewModelTest`、真机 [14](evidence/14-fix-rapid-double-tap.png) |
| 取消 / 返回扫码 | 清空未完成的 `operationId`，不写库 | `ScanConfirmViewModelTest`、真机核对（流水不变） |
| 同一条码第二次入库（新确认） | 新的 `operationId` → 新流水、新批次，复用同一食材/商品 | 真机 [13](evidence/13-fix-second-import.png)、DB 核对 |
| 不同条码 | 独立入库，可复用同一食材/商品 | 真机 [10](evidence/10-scan-second-import.png)、DB 核对 |

真机数据库核对（MB-S5-002 验证：同一码三次确认，其中一次取消、一次连点）：

```text
ingredient = 1   product = 1   batch = 3   ledger = 3
scan-import:6901234567892:8d753fa7-…   1.0 g
scan-import:6901234567892:43468b26-…   1.0 g
scan-import:6901234567892:f648f871-…   1.0 g
barcode = 0（扫码模块不写商品主数据表）
```

> 取消步骤未产生第 4 条；快速连点只 +1 → 单次确认幂等；三个不同 `operation_id` → 允许重复入库。

---

## 5. 错误与文案

`ScanErrorMessages` 把 `CulinaryError` 映射为可执行文案（并附稳定错误码后缀）：

| 错误 | 文案 |
|---|---|
| `Validation(quantity)` | 数量必须是大于 0 的数字。 |
| `Validation(ingredient)` | 请选择已有食材，或填写新食材名称。 |
| `Validation(ingredient.name)` | 食材名称不合法，请检查长度后重试。 |
| `Validation(operationId)` | 缺少本次导入的操作标识，请返回扫码页重试。 |
| `NotFound` | 记录不存在或已被删除，请刷新后重试。 |
| `Conflict` | 本次导入已被处理，请刷新后确认结果。 |
| `Domain` | 业务规则拒绝了本次导入。 |
| `Platform` | 相机或系统能力不可用，请改用手工输入。 |
| 其他 | 导入失败，请重试。 |

---

## 6. 为什么没有把条码写进 `barcode` 表

- 任务书的允许范围是 `core:domain` 的接口/契约、`core:platform` 的封装、feature 的接口实现；
  **未**包含 `core:data`（Repository 实现层）与 `core:database`；
- 因此本阶段不做"条码 → 商品"的持久映射，匹配口径为：
  **该条码此前的导入记录（流水前缀匹配）** + **用户选择/新建食材**；
- `barcode` 表保持 0 行，符合"扫码模块不承担商品主数据管理"；
- 若后续需要"扫一次自动带出商品名"，应在授权范围内新增 `ProductRepository` 端口
  + `core:data` 实现 + 导入确认页展示，属于**新的决策点**，不在本阶段自行扩大。

---

## 7. 未决与风险

| 项 | 说明 | 建议 |
|---|---|---|
| `operationId` 仅内存缓存 | 进程被杀后重试会生成新键（已写入的那次不会重复，但用户再确认会新增一条） | 如需强保证，存 `SavedStateHandle`；需负责人决定 |
| 写前查重为全表扫描 | 按条码前缀过滤 `ledger()`，与库存流水页同量级 | 数据量增长后改为前缀索引查询（需 `core:data`/Schema 变更） |
| 跨模块未用单事务 | 采用"食材创建 + 入库 + 失败归档"的明确补偿 | 归档失败时残留一条 ACTIVE 食材；已用文案提示，可手动删除/归档 |
| 条码映射未持久化 | 见 §6 | 需新授权 |
| 新建食材默认单位 | 质量基准单位优先（`g`），计数型商品需用户改档案 | Stage 3 食材编辑器可改 |
| 已导入提示的时间格式 | `MM-dd HH:mm`（系统时区） | 与其他库存页面一致 |
