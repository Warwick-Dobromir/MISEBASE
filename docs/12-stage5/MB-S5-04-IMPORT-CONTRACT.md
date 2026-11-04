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
data class ScanStockRequest(capture, target, amount, note?)
data class ScanStockReceipt(entry, item, idempotentReplay: Boolean)

// 写前查重（只读）
class LookupScanImportUseCase(inventoryRepository) {
    suspend operator fun invoke(capture): Outcome<ScanPreviousImport?>
}
data class ScanPreviousImport(entry, item?)
```

幂等键规则（`ScanImportKey`）：

```text
key = "scan-import:" + capture.normalizedValue      // 只由条码值决定，不含时间/随机数
```

> 只由条码决定是刻意设计：包含时间或随机数会让"同一条码重复确认"绕过幂等。
> 代价是同一码永久视为"已导入"；若将来需要"同码多次入库"，需要新的显式决策（见 §7）。

---

## 3. 两条链路

### 3.1 写前查重（确认页进入时）

```text
ScanConfirmViewModel.init
  └── LookupScanImportUseCase(capture)                 // read-only
        ├── InventoryRepository.findByIdempotencyKey("scan-import:<code>")
        └── InventoryRepository.findItem(batchId)
  └── 命中 → previous != null → 展示「该条码已导入过」+ 禁用「确认导入」
```

### 3.2 确认导入（用户点「确认导入」）

```text
ScanConfirmViewModel.confirm()
  ├── evaluate(): 已导入/保存中/数量非法/缺目标 → 拒绝（不调用任何写接口）
  └── importCapture()
        ├── ScanIngredientGateway.resolve()            // feature:products
        │     ├── 已有食材：IngredientRepository.get + InventoryRepository.findProductForIngredient
        │     └── 新建食材：SaveIngredientUseCase（含名称校验；默认单位按质量基准→计数→首行）
        └── ScanStockGateway.receive()                 // feature:inventory
              └── RecordStockReceiptUseCase(...)       // 一个事务：批次 + 流水
                    └── 幂等键 = ScanImportKey.of(capture)
```

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

## 4. 幂等与重复确认

| 场景 | 行为 | 证据 |
|---|---|---|
| 同一条码第二次进入确认页 | `previous != null`，确认按钮禁用，提示上次导入时间与数量 | 真机 [09](evidence/09-scan-duplicate-guard.png) |
| 绕过 UI 直接调用入库接口（同码） | `RecordStockReceiptUseCase` 命中幂等键 → 返回 `idempotentReplay = true`，不新增批次/流水 | `InventoryScanStockGatewayTest` |
| 不同条码 | 独立入库，可复用同一食材/商品 | 真机 [10](evidence/10-scan-second-import.png)、DB 核对 |
| 并发/重复点击 | `saving` 与 `result != null` 双闸门 + 幂等键兜底 | `ScanConfirmViewModelTest` |

真机数据库核对（`pm clear` 后两次导入）：

```text
ingredient = 1   product = 1   inventory_batch = 2   inventory_ledger = 2
ledger keys: scan-import:6901234567892, scan-import:MISEBASEQRSCAN001
barcode = 0（扫码模块不写商品主数据表）
```

---

## 5. 错误与文案

`ScanErrorMessages` 把 `CulinaryError` 映射为可执行文案（并附稳定错误码后缀）：

| 错误 | 文案 |
|---|---|
| `Validation(quantity)` | 数量必须是大于 0 的数字。 |
| `Validation(ingredient)` | 请选择已有食材，或填写新食材名称。 |
| `Validation(ingredient.name)` | 食材名称不合法，请检查长度后重试。 |
| `NotFound` | 记录不存在或已被删除，请刷新后重试。 |
| `Conflict` | 该条码已有导入记录，请勿重复提交。 |
| `Domain` | 业务规则拒绝了本次导入。 |
| `Platform` | 相机或系统能力不可用，请改用手工输入。 |
| 其他 | 导入失败，请重试。 |

---

## 6. 为什么没有把条码写进 `barcode` 表

- 任务书的允许范围是 `core:domain` 的接口/契约、`core:platform` 的封装、feature 的接口实现；
  **未**包含 `core:data`（Repository 实现层）与 `core:database`；
- 因此本阶段不做"条码 → 商品"的持久映射，匹配口径为：
  **该条码是否已导入过（幂等键命中）** + **用户选择/新建食材**；
- `barcode` 表保持 0 行，符合"扫码模块不承担商品主数据管理"；
- 若后续需要"扫一次自动带出商品名"，应在授权范围内新增 `ProductRepository` 端口
  + `core:data` 实现 + 导入确认页展示，属于**新的决策点**，不在本阶段自行扩大。

---

## 7. 未决与风险

| 项 | 说明 | 建议 |
|---|---|---|
| 同码只允许导入一次 | 由幂等键决定；第二次扫同一商品无法再入库 | 如需"按次入库"，需负责人决策（例如键加入确认序号，就失去跨会话幂等） |
| 跨模块未用单事务 | 采用"食材创建 + 入库 + 失败归档"的明确补偿 | 归档失败时残留一条 ACTIVE 食材；已用文案提示，可手动删除/归档 |
| 条码映射未持久化 | 见 §6 | 需新授权 |
| 新建食材默认单位 | 质量基准单位优先（`g`），计数型商品需用户改档案 | Stage 3 食材编辑器可改 |
| 已导入提示的时间格式 | `MM-dd HH:mm`（系统时区） | 与其他库存页面一致 |
