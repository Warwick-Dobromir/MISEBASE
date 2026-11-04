# MB-S5-08 扫码重复入库修正报告（MB-S5-002）

**文档编号**：MB-S5-08
**版本**：`1.0.0`
**状态**：`Review`
**日期**：2026-11-05
**任务书**：MB-S5-002（决策 B：允许同款商品多次入库，同时保证单次确认幂等）
**修复提交**：`af3ea8841e65547f172ddc3174c53a97cfb0cf42`
**结论**：缺陷已修复并通过 JVM/UI/真机验证；Stage 5 保持 **Review**，未关闭、未进入 Stage 6

---

## 1. 缺陷与根因

| 项 | 内容 |
|---|---|
| 现象 | 同一条码第二次扫码进入确认页时，「确认导入」被永久禁用，提示"该条码已导入过"，无法为第二件同款商品入库 |
| 位置 | `core:domain` 的 `ScanImportKey` + `LookupScanImportUseCase` + `feature:scan` 的确认页状态机 |
| 根因 | 幂等键 `scan-import:<条码>` 只由条码决定 → 同一条码的所有确认共用同一个键 → 数据库幂等查重把"第二次购买"误判为"重复提交" |
| 影响 | 业务可用性缺陷：扫码导入只能对每个条码生效一次 |

修复原则（任务书 §2）：

- **一次确认 = 一个事件键**：`scan-import:<barcode>:<operation_id>`；
- `operation_id` 在点击「确认导入 / 再次入库」进入提交态的那一刻生成，**页面加载时不生成**；
- 失败重试**复用**同一个 `operation_id`；成功、取消、重新进入确认页后清空；
- 幂等最终由数据库唯一键保证，UI 置灰只是体验优化，**不是**幂等手段。

---

## 2. 幂等机制说明

### 2.1 键结构与转义

```text
scan-import:<escaped(barcode)>:<operation_id>
```

- `escape`：`%` → `%25`，`:` → `%3A`（最小转义）。
  原因：若条码含 `:`，`scan-import:<A>:` 会成为 `scan-import:<A:B>:…` 的前缀，
  导致不同商品互相误判。转义后前缀匹配无歧义（`ScanImportKeyTest` 覆盖）。
- `operation_id` 由 `IdGenerator`（生产实现 = UUID v4）生成，非空校验。
- 旧键 `scan-import:<barcode>` 仍被识别为"该条码导入过"，**仅用于提示**，不会再产生新数据（迁移友好）。

### 2.2 状态机

```text
进入确认页
  └── LookupScanImportUseCase（按条码前缀在流水中取最近一次）
        ├── 命中 → isDuplicate = true，显示「该商品此前已导入（上次 …）」+ 按钮「再次入库」
        └── 未命中 → 按钮「确认导入」

点击按钮
  ├── isSubmitting / result != null → 忽略（连点防护）
  ├── 数量非法 / 未选目标 → 校验错误
  └── 否则：currentOperationId ?: 生成新 id
        ├── 先写状态（id + isSubmitting=true）再启动协程 → 同帧第二次点击被忽略
        ├── 调 ScanIngredientGateway（products）→ 调 ScanStockGateway（inventory，事件键）
        ├── 成功：清空 operationId，展示「已入库」，isDuplicate = true
        └── 失败：保留 operationId，展示 lastError + 「重试」；重试复用同一 id
```

### 2.3 与数据库唯一约束的关系

- `feature:inventory` 的 `InventoryScanStockGateway` 把事件键写入
  `inventory_ledger.idempotency_key`（唯一），`RecordStockReceiptUseCase` 先查键再写批次 + 流水（同一事务）；
- 同一次确认重试 → 命中唯一键 → 返回 `idempotentReplay = true`，不新增批次/流水；
- 不同次确认 → 不同键 → 正常新增第二条流水；
- 因此"快速连点""失败重试""进程内重复调用"都不会产生重复记录，不依赖 UI 防抖。

---

## 3. 修改文件清单（14 个文件，+686 / −269）

| 文件 | 变更 |
|---|---|
| `core/domain/.../scan/ScanImportKey.kt` | 事件级键、条码转义、前缀判定、旧键兼容 |
| `core/domain/.../scan/ScanImportContracts.kt` | `ScanStockRequest` 增加必填 `operationId`；注释同步 |
| `core/domain/.../usecase/scan/LookupScanImportUseCase.kt` | 改为按条码前缀在流水中取最近一次导入（只读、只提示） |
| `feature/inventory/.../InventoryScanStockGateway.kt` | 用事件键查重；拒绝空 `operationId`；注释同步 |
| `feature/scan/.../ScanConfirmUiState.kt` | 新增 `isDuplicate` / `isSubmitting` / `currentOperationId` / `lastError` / `canRetry` |
| `feature/scan/.../ScanConfirmViewModel.kt` | 提交态、`operationId` 缓存与复用、成功清空、取消清空、补偿保留 |
| `feature/scan/.../ScanConfirmScreen.kt` | 重复警告文案、按钮文案（确认导入 / 再次入库 / 重试 / 正在导入）、失败重试入口 |
| `feature/scan/.../ScanConfirmRoute.kt` | 「返回扫码」= 取消：清空未完成的 `operationId` |
| `feature/scan/src/main/res/values/strings.xml` | 新增「再次入库」「重试」「该商品此前已导入」等文案 |
| `core/domain` 测试（2 个文件） | 键 7 例、查询 4 例 |
| `feature/inventory` 测试（1 个文件） | 网关 7 例（含同码不同事件键写入两次、空 id 拒绝） |
| `feature/scan` 测试（2 个文件） | ViewModel 14 例、确认页 UI 6 例 |

> 未修改：Schema/Entity/迁移/数据库文件、备份/恢复实现、其他业务模块核心逻辑、模块集、技术栈、CI、签名。
> `feature:inventory` 只改了扫码网关的键拼装（接口适配），未改库存规则。

---

## 4. 自动化测试结果

命令与结果：

```powershell
.\gradlew.bat test assembleDebug ktlintCheck detekt --console=plain --no-daemon
# BUILD SUCCESSFUL（最终重跑）
```

| 项 | 修复前（Stage 5） | 修复后 | 变化 |
|---|---:|---:|---:|
| 执行次数 | 801 | **817** | +16 |
| 唯一 JVM 用例 | 503 | **514** | +11 |
| 失败 | 0 | **0** | — |

新增/更新套件：

| 套件 | 修复前 | 修复后 | 覆盖 |
|---|---:|---:|---|
| `ScanImportKeyTest` | 3 | 7 | 事件键、转义与前缀、操作 id 去空白/空值拒绝、旧键兼容 |
| `LookupScanImportUseCaseTest` | 3 | 4 | 取最近一次、忽略其他条码、未命中、失败传播 |
| `InventoryScanStockGatewayTest` | 5 | 7 | 事件键落库、同键重放、**同码不同键写入两次**、空 id 拒绝 |
| `ScanConfirmViewModelTest` | 11 | 14 | 见下表 7 场景 |
| `ScanConfirmUiTest` | 5 | 6 | 重复警告 + 「再次入库」、失败错误 + 「重试」 |

任务书 §5 的 7 个场景（`ScanConfirmViewModelTest`）：

| # | 场景 | 用例 |
|---|---|---|
| 1 | 首次导入直接成功 | `first import succeeds when there is no history` |
| 2 | 重复提示 + 按钮文案 | `duplicate scan sets the warning state without blocking`（VM）+ `ScanConfirmUiTest.duplicate scan warns and offers the import again action` |
| 3 | 取消不写库 | `cancel does not call the stock gateway and writes nothing` |
| 4 | 再次入库生成新键并多一条流水 | `confirming again creates a new operation id and one more ledger row` |
| 5 | 快速连点只调用一次 | `rapid double click submits only once` |
| 6 | 失败重试复用同一 id | `retry after a failure reuses the same operation id`（断言两次收到的 id 相同、流水只有 1 条） |
| 7 | 两次独立确认两个 id | `two independent confirmations produce two different operation ids` |

`ktlintCheck`、`detekt`：PASS。

---

## 5. 重复扫码真机验证（vivo V2429A / Android 16 / API 36）

被测 APK：`app/build/outputs/apk/debug/app-debug.apk`，39,854,267 字节，
SHA-256 `F1EB9F09F07FFAE535921F38EBFE7B7F5444814F069A30F0B146B46978BB4F10`。
数据起点：卸载重装（全新数据库）；相机权限设为永久拒绝以固定走手工输入路径。

| # | 步骤 | 预期 | 实际 | 结果 |
|---|---|---|---|---|
| R-1 | 手工输入 `6901234567892` → 新建食材 `scantestsalt` → 确认导入 | 首次入库成功 | 显示「已入库 scantestsalt 1 g」 | 通过 |
| R-2 | 返回扫码 → 再次输入同码 | 显示"此前已导入"且允许再次入库 | 「该商品此前已导入 · 上次导入 11-05 02:30 · 1 g；如需再入库一次，请点「再次入库」」，按钮为「再次入库」且可点 | 通过 |
| R-3 | 选中已有食材 → 点「再次入库」 | 新增一条独立入库 | 显示「已入库 scantestsalt 1 g」，上次导入时间更新为 02:31 | 通过 |
| R-4 | 重复页面点「返回扫码」（取消） | 不写库 | 流水仍为 2 条 | 通过 |
| R-5 | 再次进入 → **快速连点两次**「再次入库」 | 只写一条 | 流水 2 → 3（仅 +1），第三次 id 独立 | 通过 |

截图：[12-fix-duplicate-entry.png](evidence/12-fix-duplicate-entry.png)、
[13-fix-second-import.png](evidence/13-fix-second-import.png)、
[14-fix-rapid-double-tap.png](evidence/14-fix-rapid-double-tap.png)。

数据库核对（`adb exec-out run-as com.culinary cat databases/culinary.db`）：

```text
ingredient = 1   product = 1   batch = 3   ledger = 3
scan-import:6901234567892:8d753fa7-ecbb-404e-b4d6-42dbb6002f82   1.0 g
scan-import:6901234567892:43468b26-09fc-4658-a329-be570846b5f7   1.0 g
scan-import:6901234567892:f648f871-0390-4122-9015-8c61066ad78a   1.0 g
```

三条流水属于同一条码、同一食材、同一商品，但 `operation_id` 互不相同 → **"允许重复入库"成立**；
取消步骤未产生第四条 → **"取消不写库"成立**；快速连点只 +1 → **"单次确认幂等"成立**。

---

## 6. 回归测试报告

| 项 | 结果 |
|---|---|
| 全量 `test assembleDebug ktlintCheck detekt` | BUILD SUCCESSFUL（最终重跑） |
| 唯一/执行/失败 | 514 / 817 / 0 |
| 模块依赖检查（14 模块 / 39 边） | PASS |
| 违规注入自测（8 类违规） | PASS |
| 休眠表检查（11 表 0 引用） | PASS |
| 文档一致性 | PASS |
| Debug APK | 构建成功，哈希见 §5 |
| 真机 instrumentation（条码/二维码解码 4 例） | 未改动，沿用 Stage 5 结果 |

### 6.1 已知偶发：`WorkManagerBackupSchedulerTest`

- 现象：`enqueues an immediate backup and returns its id` 偶发断言失败（`WorkInfo = FAILED`）；
- 证据：本任务期间出现 3 次（单模块 `--rerun-tasks` 连续 3 次均失败），随后全量重跑通过；
  **在 Stage 5 检查点 `0184a14` 上（stash 掉本任务改动）同样可复现**，与本修复无关；
- 既有登记：Stage 4 已记录为已知偶发（[MB-S4-01](../11-stage4/MB-S4-01-EXECUTION-REPORT.md) 第 244 行 R8、
  [MB-S4-08](../11-stage4/MB-S4-08-TEST-MATRIX.md) 第 85 行、[MB-S4-CLOSE-RECORD](../11-stage4/MB-S4-CLOSE-RECORD.md) 第 94 行）；
- 根因（本次定位）：该用例在 Robolectric 下入队一次性任务，`AutoBackupWorker` 需要 Hilt 注入的
  `AutoBackupRunner`，测试未安装 fake runner，worker 以 `runner_unavailable` 返回 `Result.failure`；
  断言又接受 ENQUEUED/RUNNING/SUCCEEDED，因此**结果取决于 worker 是否在断言前完成**（典型竞态）；
- 处置：**未修改**备份/平台实现与测试（超出本任务允许范围），按既有风险保留，建议由负责人单独立项
  （最小修复：在该用例中按 `SchedulerWorkerIntegrationTest` 的做法用
  `AutoBackupWorkerDependencies.installForTest(...)` 注入 fake runner）。

---

## 7. 未解决问题与风险

| # | 项 | 级别 | 说明 |
|---|---|---|---|
| F1 | `operation_id` 只在内存中缓存（ViewModel），进程被系统杀死后重试会生成新键 | 低 | 已被杀死的提交若其实已写库，则用户再次确认会新增一条（等价于"用户又买了一件"）。如需强保证，应把未完成 id 存 `SavedStateHandle`；需要负责人决定是否扩大范围 |
| F2 | 同码多次入库后，旧的"上次导入"只展示最近一次 | 低 | 流水页可见全部记录；确认页只做提示 |
| F3 | `LookupScanImportUseCase` 全表扫描流水并取最新 | 低 | 与库存流水页同量级；数据量增长后应改为按前缀索引查询（需 `core:data`/Schema 变更，另立任务） |
| F4 | `WorkManagerBackupSchedulerTest` 既有偶发（见 §6.1） | 中 | 不在允许范围，未修复；需立项 |
| F5 | 二维码相机取景仍未人工复测 | 低 | 沿用 Stage 5 局限 |

---

## 8. Git 提交与工作树状态

| 项 | 值 |
|---|---|
| 修复提交 | `af3ea8841e65547f172ddc3174c53a97cfb0cf42`（14 文件，+686 / −269） |
| 所在分支 | `misebase/main` |
| Stage 5 检查点 | `checkpoint/misebase-stage5-20261105` → `0184a14`（**未移动**） |
| `framework-0.1.0` | `2b523de`（**未移动**） |
| 工作树 | 本报告提交后 `git status --porcelain` 为空 |

停止声明：**未关闭 Stage 5、未进入 Stage 6、未替负责人签收任何决策**；MB-D-006 / MB-D-010 / MB-D-012 仍为 `PROPOSED`。

---

## 9. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0.0 | 2026-11-05 | 记录事件级幂等修复、修改文件、测试与真机验证、既有偶发说明与风险 | MISEBASE AI 开发工作区 |
