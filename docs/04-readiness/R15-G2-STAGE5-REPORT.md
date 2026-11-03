# G2 阶段 5（平台能力）完成报告

**文档编号**：R15
**版本**：`1.1.0`
**状态**：`Review`（待项目负责人验收；设备级验证按批准延后到阶段 7）
**日期**：2026-11-04
**实施依据**：[R01 0.2.2](R01-FRAMEWORK-SEQUENCE.md) §8、A03A §4/§6.4/§8.1、D-011、D-013、V01
**回档点**：`baseline/g2-stage4-20261104`

---

## 1. 任务目标

平台能力层：扫码、SAF、权限、WorkManager 的接口与最小实现，结果统一映射为 `Outcome`，
并保持离线（不申请 `INTERNET`）。

## 2. 交付内容

### 2.1 WorkManager 自动备份（`BackupScheduler`）

- 端口在 `core:domain`，实现 `WorkManagerBackupScheduler`；
- 唯一周期任务 `culinary-auto-backup`（24h、电量不低、`KEEP` 不叠加）；
- 立即备份返回 WorkRequest ID；
- `AutoBackupWorker` 是**明确标注的骨架**：只验证调度链路，真实 E05 编排在阶段 9。

### 2.2 相机权限状态机

- `GRANTED` / `DENIED_CAN_ASK` / `DENIED_PERMANENTLY`；
- 依据"是否已授权 + 是否显示 rationale + 是否请求过"判定，供 UI 决定再次请求、跳设置或转手工输入。

### 2.3 SAF 存储适配（`BackupStoragePort`）

- `SafBackupStorage`：`createDocument`/`pickDocument` 返回不透明 `StorageHandle`，`Uri` 不离开 `core:platform`；
- 流读写走 `ContentResolver`，写入使用 `"wt"` 截断；
- 取消选择/权限/IO 错误映射为 `CUL-FILE-001`；
- Activity Result 注册由 `DocumentLauncher` 抽象，UI 在阶段 7 实现。

### 2.4 扫码（D-013）

- 领域：`BarcodeResult`、`BarcodeScanner`；
- 手工兜底：`BarcodeParser`（规范化、长度 4–64、拒绝控制字符、`MANUAL` 格式）；
- 本地解码：`MlKitBarcodeDecoder`（bundled ML Kit，`ImageProxy` → 识别结果，失败 `CUL-SCAN-002`）；
- 格式映射：`BarcodeFormats`，未知格式返回 `null`，不猜；
- **相机接入**：`CameraBarcodeScanner` 实现 `BarcodeScanner`：`ProcessCameraProvider` →
  `bindToLifecycle(DEFAULT_BACK_CAMERA)` → `ImageAnalysis`（`KEEP_ONLY_LATEST`）→ 单线程分析器 →
  `MlKitBarcodeDecoder`；首个条码或 15s 超时后解绑，权限拒绝映射 `CUL-SCAN-001`；
- 清单：`core:platform` 声明 `CAMERA` 权限（`required=false` 的相机特性），不申请 `INTERNET`。

> `CameraBarcodeScanner` 当前只经过**编译与静态检查**；真机/模拟器扫码属已批准的设备级延后项，
> 不在本阶段宣称通过。

### 2.5 依赖与产物证据

| 证据 | 实测 |
|---|---|
| CameraX | `androidx.camera:camera-core:1.4.2`（`camera-camera2`、`camera-lifecycle` 同版本，来自 `libs.versions.toml`） |
| ML Kit | `com.google.mlkit:barcode-scanning:17.3.0` |
| 模型是否随包 | APK 内存在 `assets/mlkit_barcode_models/*.tflite`（oned_auto_regressor、oned_feature_extractor、ssd_mobilenet 等）→ **bundled，离线识别** |
| 权限 | APK 含 `CAMERA`；**不含 `INTERNET`**（`aapt2 dump permissions`） |
| 其他合并权限 | `WAKE_LOCK`、`ACCESS_NETWORK_STATE`、`RECEIVE_BOOT_COMPLETED`、`FOREGROUND_SERVICE`（WorkManager/ML Kit） |

## 3. 测试与验证

| 测试类 | 数量 | 覆盖 |
|---|---:|---|
| `CameraPermissionTest` | 4 | 授权/首次/可再问/永久拒绝 |
| `WorkManagerBackupSchedulerTest` | 4 | 唯一周期、不叠加、取消、立即任务 ID |
| `SafBackupStorageTest` | 4 | 句柄写读、取消映射、空句柄拒绝 |
| `BarcodeParserTest` | 5 | 规范化、字母数字、过短/过长/控制字符 |
| `BarcodeFormatsTest` | 2 | 已知格式与未知格式 |

**平台测试：19 例 / 0 失败**；工程总测试 **154 例**。

权限实测（`aapt2 dump permissions`）：

```text
WAKE_LOCK
ACCESS_NETWORK_STATE
RECEIVE_BOOT_COMPLETED
FOREGROUND_SERVICE
```

- **无 `INTERNET`**；
- `ACCESS_NETWORK_STATE` 由 WorkManager/ML Kit 合并带入，只允许查询网络状态、不授予网络访问；
  V01 §5.3 已确认保留它不破坏离线承诺。

## 4. 退出条件核对（含已批准的延后）

| # | 条件（R01 §8.2） | 状态 |
|---|---|---|
| 1 | 模拟器/真机能扫码（或手工输入兜底） | 平台实现（CameraX 会话 + ML Kit 解码 + 手工兜底）已存在并通过编译/单测；**设备级验证延后到阶段 7** |
| 2 | 权限拒绝有可恢复路径 | 逻辑就绪（三态 + 手工兜底）；UI 接线在阶段 7 |
| 3 | SAF 选择/写入成功 | 适配层与流读写已测（file URI）；**真实 SAF 文档选择器延后到阶段 7 设备验证** |
| 4 | WorkManager 任务可入队/取消 | **PASS**（Robolectric） |
| 5 | 不申请 `INTERNET` | **PASS**（aapt2） |

延后项已由项目负责人于 2026-11-04 明确同意（决策登记册 0.15.0），
**阶段 5 不宣称设备级验证已通过**。

## 5. 平台实现 / 延后验证 / 尚未实现（三分）

| 类别 | 内容 |
|---|---|
| **已完成的平台能力** | WorkManager 调度；相机权限三态；SAF 适配与流读写；扫码领域契约、手工兜底、ML Kit 解码、CameraX 会话（`CameraBarcodeScanner`）；`CAMERA` 权限声明 |
| **已批准延后的设备级验证** | 真实 SAF 文档选择器在设备上的选择/写入；真机或模拟器实际扫码；权限弹窗与永久拒绝的 UI 路径 |
| **尚未实现（不属于本次延后授权）** | 相机预览 UI（`PreviewView`）与扫码页面；`AutoBackupWorker` 的真实 E05 备份编排（阶段 9）；备份在线快照与恢复执行器（阶段 5+/9） |

- CameraX 平台代码已接入，但只有编译/静态检查证据；**没有**设备端运行证据；
- `CAMERA` 已声明；APK 仍无 `INTERNET`，ML Kit 模型已随包（离线）。

## 6. Git

| 项 | 值 |
|---|---|
| 阶段 4 基线 | `baseline/g2-stage4-20261104`（`1620ef2`） |
| 阶段 5 提交 | `61177d0`（调度/权限）、`af0a26a`（SAF）、`63b7270`（扫码） |
| 阶段 5 基线标签 | 验收通过后打 `baseline/g2-stage5-20261104` |

## 7. 结论

平台层契约、实现与错误映射完成，19 例平台测试通过，离线约束保持。
两项设备级验证按批准延后到阶段 7，并在本文与决策登记册中明确记录，未伪装为已完成。

**请项目负责人验收阶段 5。** 验收通过后进入阶段 6（应用外壳：Hilt 根、导航、主题、启动/恢复）。

## 8. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0.0 | 2026-11-04 | 记录平台能力实现、19 例测试与延后项 | 工程组 |
| 1.1.0 | 2026-11-04 | 核实 CameraX/ML Kit 依赖与 bundled 模型；补 `CameraBarcodeScanner` 平台实现与 `CAMERA` 权限；按"已实现/延后验证/尚未实现"三分重述范围 | 工程组 |
