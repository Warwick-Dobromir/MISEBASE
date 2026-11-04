# MB-S5-03 扫码页面说明

**文档编号**：MB-S5-03
**版本**：`1.0.0`
**状态**：`Review`
**日期**：2026-11-05
**范围**：`feature:scan` 的扫码页、权限页、手工输入页、扫描框、手电筒与识别去抖
**依据**：MB-D-004（`feature:scan`）、MB-D-011（真机验证）、MB-D-009（N1 五入口）

---

## 1. 页面构成

```text
app（N1 导航外壳：顶栏 + 底部五入口）
└── 扫码（第 5 个一级入口，route = "scan"）
    └── ScanRoute        : 权限请求 + 相机预览 + 一次性导航
        ├── ScanScreen   : 纯 UI（相机以槽位注入，可在 JVM/Compose Test 中渲染）
        │   ├── CameraActions      : 手电筒 / 手工输入 / 重新扫描
        │   ├── ScanFrameOverlay   : 四块遮罩 + 白色扫描框
        │   ├── ScanBackButton     : 左上角返回（无上级时回一级入口）
        │   ├── PermissionPane     : 未请求/被拒绝/无相机三种说明页
        │   └── ManualEntryPane    : 手工输入兜底
        └── CameraBarcodePreview → CameraBarcodeSession（CameraX + ML Kit）
```

| 文件 | 作用 |
|---|---|
| [`ScanScreen.kt`](../../feature/scan/src/main/java/com/culinary/feature/scan/ScanScreen.kt) | 纯 UI：相机槽位、扫描框、权限页、手工输入、返回 |
| [`ScanRoute.kt`](../../feature/scan/src/main/java/com/culinary/feature/scan/ScanRoute.kt) | 组合根：ActivityResult 权限、相机预览、导航信号 |
| [`ScanViewModel.kt`](../../feature/scan/src/main/java/com/culinary/feature/scan/ScanViewModel.kt) | 状态机：权限、手电筒、手工输入、识别一次性消费 |
| [`CameraPreview.kt`](../../feature/scan/src/main/java/com/culinary/feature/scan/camera/CameraPreview.kt) | Compose ↔ CameraX 桥接与生命周期 |
| [`CameraBarcodeSession.kt`](../../feature/scan/src/main/java/com/culinary/feature/scan/camera/CameraBarcodeSession.kt) | Preview + ImageAnalysis 绑定、暂停/恢复、手电筒、释放 |
| [`MlKitBarcodeDecoder.kt`](../../feature/scan/src/main/java/com/culinary/feature/scan/recognize/MlKitBarcodeDecoder.kt) | ML Kit 单帧解码（总是关闭 ImageProxy） |
| [`ScanFrameGate.kt`](../../core/domain/src/main/kotlin/com/culinary/core/domain/barcode/ScanFrameGate.kt) | 去抖窗口 + 一次性消费（纯逻辑） |

---

## 2. 状态机（`ScanUiState` / `CameraAccessState`）

```text
CHECKING ──onPermissionChecked(true)──────────────► GRANTED ──(识别接受)──► GRANTED(scanning=false)
    │                                                 ▲
    ├──onPermissionChecked(false)──► NOT_REQUESTED ───┘ onPermissionResult(true)
    │                                    │
    │                                    └──onPermissionResult(false)──► DENIED ──重新请求──┐
    └──onCameraAvailability(false)──► UNAVAILABLE（强制手工输入）                             │
                                                                    DENIED ◄────────────────┘
```

| 状态 | 界面 | 说明 |
|---|---|---|
| `CHECKING` | 空（首帧） | 进入页面即查询既有授权 |
| `NOT_REQUESTED` | 权限说明页 | 「允许使用相机」触发系统对话框 |
| `GRANTED` | 相机预览 + 扫描框 | `showCamera = true` |
| `DENIED` | 拒绝说明页 | 重新请求 + 手工输入 + 系统设置提示 |
| `UNAVAILABLE` | 无相机说明页 | 自动切到手工输入 |

工艺约束：`showCamera = hasCamera && GRANTED && !manualEntry`；打开手工输入会暂停分析，关闭后按权限恢复。

---

## 3. 识别与去抖（一次识别即停）

1. `ImageAnalysis`（`STRATEGY_KEEP_ONLY_LATEST`）→ 单线程执行器 → ML Kit Bundled；
2. 每帧结果交给 `ScanFrameGate.offer(value, now)`：
   - 已消费 → 拒绝；
   - 与上次相同且距上次 < 2 000 ms → 拒绝（去抖窗口）；
   - 否则接受，并把闸门置为「已消费」；
3. 接受后 `ScanViewModel` 立刻 `scanning = false`（相机分析暂停）并发出一次性 `pendingCapture`；
4. `ScanRoute` 消费该信号后清空并导航到确认页；
5. 从确认页返回时 `LaunchedEffect(Unit)` 重新执行 → `onResumeScanning()`：解除消费，但保留去抖窗口，
   因此"返回后同一帧再次命中"不会立刻重复触发。

| 场景 | 行为 | 测试 |
|---|---|---|
| 连续帧同一个码 | 只触发一次 | `ScanFrameGateTest`、`ScanViewModelTest` |
| 识别后未返回 | 不再触发（一次性消费） | `ScanFrameGateTest` |
| 返回后 2 秒内同码 | 仍不触发 | `ScanFrameGateTest`、`ScanViewModelTest` |
| 返回后 > 2 秒同码 | 可再次触发 | `ScanFrameGateTest`、`ScanViewModelTest` |
| 换一个码 | 立即触发 | `ScanFrameGateTest`、`ScanViewModelTest` |

---

## 4. 相机与手电筒

- 只用后置相机（`CameraSelector.DEFAULT_BACK_CAMERA`）；
- 绑定 `Preview` + `ImageAnalysis`，生命周期交给 `LocalLifecycleOwner`；
- 页面销毁时 `release()`：清分析器、解绑、关闭 ML Kit 客户端、关闭线程池；
- 手电筒：`CameraControl.enableTorch`；无闪光灯或相机未就绪时返回 `false`，
  页面回滚开关状态并提示「此设备不支持手电筒。」——**不假装成功**。

真机证据：[01-scan-camera-granted.png](evidence/01-scan-camera-granted.png)、[02-scan-torch.png](evidence/02-scan-torch.png)。

---

## 5. 手工输入兜底

- 无相机、未授权、被拒绝三种情况下都可进入；
- 校验：非空、长度 ≤ `ScanCapture.MAX_VALUE_LENGTH`（512）；错误就地显示；
- 手工结果与相机结果走**同一条**确认页与幂等规则（来源标记 `MANUAL`，格式为 `null`）；
- 显式用户动作不进去抖闸门（由确认页幂等兜底）。

---

## 6. 视觉与文案（A 方向内）

| 元素 | 处理 |
|---|---|
| 扫描框 | 72% 宽、最大 46% 高的圆角方框 + 白色 4px 描边 + 四块 45% 黑色遮罩 |
| 底部动作区 | 垂直渐变遮罩（保证深色取景下按钮可读）；次要按钮白色描边 |
| 返回 | 左上角圆形半透明图标（避免底部动作被挤成两行——真机验证中发现） |
| 文案 | 「将条码放入框内，保持稳定」「打开/关闭手电筒」「手工输入条码」「重新扫描」 |
| 信息架构 | 不改 N1 五入口顺序；扫码仍是第五个底部一级入口 |

真实截图：[03](evidence/03-scan-permission-required.png)、[04](evidence/04-scan-permission-denied.png)、[06](evidence/06-scan-manual-entry.png)。

---

## 7. 无相机/无权限的降级

| 情况 | 页面 | 结果 |
|---|---|---|
| 无 `FEATURE_CAMERA_ANY` | 「没有可用的相机」 | 自动进入手工输入 |
| 权限被拒绝 | 「相机权限被拒绝」+ 设置提示 | 手工输入可用 |
| 相机服务异常（绑定失败） | 顶部错误条 + 可重试 | 手工输入可用 |

---

## 8. 不在本页范围

- 不写食材/库存数据（由 `feature:products` / `feature:inventory` 的接口负责，见
  [MB-S5-04](MB-S5-04-IMPORT-CONTRACT.md)）；
- 不做商品主数据编辑、不做条码库维护；
- 不引入网络/在线查询。
