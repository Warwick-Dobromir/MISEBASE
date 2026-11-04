# MB-S5-05 测试矩阵

**文档编号**：MB-S5-05
**版本**：`1.0.0`
**状态**：`Review`
**日期**：2026-11-05
**口径**：唯一 JVM 用例；执行次数包含 debug/release 重复运行（MB-D-006 仍为 `PROPOSED`，本阶段按实际数报告，不改写历史口径）

---

## 1. 汇总

| 项 | 结果 |
|---|---|
| 唯一 JVM 用例 | **503** |
| 执行次数 | **801** |
| 失败 | **0** |
| Stage 4 基线 | 416 唯一 / 660 执行 / 0 失败 |
| 新增唯一用例 | **+87**（其中扫码相关 86 + 导航外壳 1） |
| 新增执行次数 | **+141** |
| Debug APK | `assembleDebug` 成功（39,854,267 字节） |
| 质量门 | `ktlintCheck`、`detekt` 全绿 |
| 真机 instrumentation | `feature:scan` 4/4 通过（vivo V2429A / API 36） |

命令：

```powershell
.\gradlew.bat test assembleDebug ktlintCheck detekt --console=plain --no-daemon
.\gradlew.bat :feature:scan:connectedDebugAndroidTest --console=plain
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-module-deps.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\test-module-deps.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-dormant-tables.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-doc-consistency.ps1
```

---

## 2. 按模块分布（不变 + 新增）

| 模块 | 唯一用例 | 执行次数 |
|---|---:|---:|
| app | 13 | 21 |
| core:common | 9 | 9 |
| core:domain | 163 | 163 |
| core:database | 81 | 162 |
| core:data | 93 | 186 |
| core:designsystem | 9 | 18 |
| core:platform | 26 | 52 |
| feature:ingredients | 11 | 19 |
| feature:inventory | 14 | 23 |
| feature:products | 8 | 16 |
| feature:recipes | 11 | 19 |
| feature:sauces | 11 | 18 |
| feature:scan | **54** | **95** |
| **合计** | **503** | **801** |

> Stage 5 新增的 `feature:scan` 为全新模块；`core:domain`、`feature:products`、`feature:inventory`、`app` 为既有模块追加用例。

---

## 3. 新增/变更套件

| 套件 | 模块 | 唯一用例 | 覆盖 |
|---|---|---:|---|
| `ScanFrameGateTest` | core:domain | 8 | 首帧接受、连续帧抑制、一次性消费、去抖窗口、恢复后同码抑制、异码立即接受、空白值、reset |
| `ScanCaptureTest` | core:domain | 5 | 原文保留/规范化、格式标签、空白拒绝、长度校验 |
| `ScanImportKeyTest` | core:domain | 3 | 同码同键（跨来源/格式）、异码异键、字符串重载去空白 |
| `LookupScanImportUseCaseTest` | core:domain | 3 | 命中历史导入、未命中返回 null、仓储失败不被吞 |
| `ProductScanIngredientGatewayTest` | feature:products | 8 | 复用食材、经 `SaveIngredientUseCase` 新建、单位回退（质量基准→计数→首行）、缺目标/缺食材失败、商品映射、默认单位语义 |
| `InventoryScanStockGatewayTest` | feature:inventory | 5 | 幂等键=条码、重复确认不重复入库、异码独立批次、商品补建、非法数量不写库 |
| `ScanViewModelTest` | feature:scan | 16 | 权限态机、一次识别即停、连续帧不重复、手工输入校验、手电筒回滚、信号消费、错误提示 |
| `ScanConfirmViewModelTest` | feature:scan | 11 | 参数解析、已导入拦截、复用食材、新建食材、数量/目标校验、补偿归档、重放标记、防重复提交 |
| `ScanRoutesTest` | feature:scan | 6 | 路由编码（空格/斜杠/&）、参数还原、来源兜底、缺参/超长拒绝 |
| `BarcodeFormatNamesTest` | feature:scan | 3 | 一维/二维格式名、未知格式 null |
| `BarcodeValueMapperTest` | feature:scan | 5 | 原始值+格式+来源、手工来源、空值/超长丢弃、批量映射 |
| `ScanUiTest` | feature:scan | 8 | 权限说明、拒绝页、相机态动作、无相机、手工输入编辑、手电筒提示、重新扫描 |
| `ScanConfirmUiTest` | feature:scan | 5 | 识别结果展示、已导入禁用、新建食材、导入结果/重放、缺参空态 |
| `NavigationShellTest`（+1） | app | +1 | 扫码一级入口标题 + 设置动作 |
| `BarcodeDecodingInstrumentedTest`（instrumentation，不计入 JVM） | feature:scan | 4 | 真机 EAN-13 / Code128 / QR 文本 / QR URL 解码 |

---

## 4. 验收点覆盖（任务书 §11）

| 要求 | 覆盖 | 证据 |
|---|---|---|
| `feature:scan` 模块依赖规则（不依赖 products/inventory 实现） | `config/module-dependencies.json` + `check-module-deps.ps1` 违规注入（`feature:scan → feature:products` 必须报 `V-FEATURE-FEATURE` / `V-IMPORT-UNDECLARED`） | 本文 §5 |
| 识别去抖与一次性消费（连续帧不重复触发） | `ScanFrameGateTest`、`ScanViewModelTest` | §3 |
| 重复确认幂等（同码不重复入库） | `InventoryScanStockGatewayTest`、`ScanConfirmViewModelTest`、真机 D-09 + 数据库核对 | [MB-S5-02](MB-S5-02-DEVICE-VERIFICATION.md) |
| 手工输入兜底 | `ScanViewModelTest`、`ScanUiTest`、真机 D-06 | 同上 |
| 权限拒绝处理 | `ScanViewModelTest`、`ScanUiTest`、真机 D-03/D-04 | 同上 |
| 导入事务（批次+流水同一事务 / 明确补偿） | `InventoryScanStockGatewayTest`（复用 `RecordStockReceiptUseCase` 单事务）、`ScanConfirmViewModelTest`（补偿归档） | [MB-S5-04](MB-S5-04-IMPORT-CONTRACT.md) |
| Compose UI Test（扫码页、确认页关键路径） | `ScanUiTest`、`ScanConfirmUiTest` | §3 |
| ViewModel（状态与用户操作） | `ScanViewModelTest`、`ScanConfirmViewModelTest` | §3 |
| 真机验证（MB-D-011 字段） | `MB-S5-02` 13 个用例 + 10 张截图 | [MB-S5-02](MB-S5-02-DEVICE-VERIFICATION.md) |

---

## 5. 质量门与脚本

| 检查 | 结果 |
|---|---|
| `test`（debug + release 变体） | 801 次执行 / 503 唯一 / 0 失败 |
| `assembleDebug` | 成功 |
| `ktlintCheck` | PASS |
| `detekt` | PASS |
| `check-module-deps.ps1` | PASS：14 模块 / 39 条声明边 / DAG 无环 |
| `test-module-deps.ps1`（违规注入） | PASS：`V-FEATURE-FEATURE`、`V-FEATURE-IMPL`、`V-DB-REVERSE`、`V-DOMAIN-ANDROID`、`V-UI-DAO`、`V-IMPORT-UNDECLARED`、`V-MODULE-COUNT`、`V-CYCLE` 全部检出 |
| `check-dormant-tables.ps1` | PASS：11 张休眠表 0 处新业务引用 |
| `check-doc-consistency.ps1` | PASS |
| 真机 `connectedDebugAndroidTest` | PASS：4/4 |

---

## 6. 未覆盖 / 未自动化

| 项 | 原因 | 处置 |
|---|---|---|
| 相机取景下的二维码识别 | 需要人工把实物二维码举到镜头前 | 解码能力已在真机验证；取景交互留给负责人复核 |
| 无相机设备 | 本机有相机 | UI/JVM 分支覆盖 |
| 低电量、锁屏中断、快速连续 10+ 次扫码 | 不在 MB-D-011 最低要求 | 列入风险 |
| 中文食材名输入 | `adb shell input text` 不支持中文 | Stage 3 编辑器已覆盖同类路径 |
| 仪器化 `connectedAndroidTest` 纳入 CI | MB-D-010 仍 `PROPOSED` | 仅本地/真机手动执行 |
