# MB-S5-02 扫码真机验证记录（MB-D-011）

**文档编号**：MB-S5-02
**版本**：`1.0.0`
**状态**：`Review`
**日期**：2026-11-05
**依据**：MB-D-011（真机验证设备，`ACCEPTED`）、MB-D-004（`feature:scan`，`ACCEPTED`）、MB-S5-001 任务书 §5 / §11
**证据目录**：[`docs/12-stage5/evidence/`](evidence/)

---

## 1. 结论摘要

| 项 | 结果 |
|---|---|
| 真机设备 | 1 台，vivo V2429A（Android 16 / API 36），满足 MB-D-011 的「API 26+，推荐 33+」 |
| 相机权限 | 已授予、未请求、被拒绝三种状态均验证 |
| 相机预览 / 扫描框 / 手电筒 / 返回 | 全部通过（真机截图 + UI 层级） |
| 条码识别（相机） | **通过**：真机相机识别出 EAN-13 条码 `8809184833631`，确认页显示格式 `EAN13`、来源「相机识别」 |
| 二维码识别 | **通过（解码能力）**：真机 instrumentation 用同一 ML Kit Bundled 客户端解码 QR 文本与 QR URL 各 1 例；相机取景未能稳定复现 QR 样本（见 §7 局限） |
| 连续扫码 / 重复识别 | 通过：手工输入连续导入两个不同码；同一码再次进入确认页显示「该条码已导入过」且确认按钮禁用 |
| 导入确认 | 通过：新建食材 + 入库；第二次复用已有食材；数据库核对一致 |
| 异常处理 | 通过：无 `FATAL`/`AndroidRuntime` 异常；无权限时降级为手工输入 |
| 是否可用 | **是**（真机验证通过，附 §7 限制说明） |

---

## 2. 设备与系统

| 字段 | 值 | 采集方式 |
|---|---|---|
| 厂商 / 品牌 | vivo | `adb shell getprop ro.product.brand` |
| 型号 | V2429A | `adb shell getprop ro.product.model` |
| 设备代号 | PD2429（`product:PD2429`） | `adb devices -l` |
| 序列号 | `10CEBG0DGT0008D` | `adb devices -l` |
| Android 版本 | 16 | `ro.build.version.release` |
| API Level | 36 | `ro.build.version.sdk` |
| 构建号 | `compiler260905102531` | `ro.build.version.incremental` |
| CPU ABI | arm64-v8a | `ro.product.cpu.abi` |
| 屏幕 | 物理 1260×2800，override 1080×2400 | `adb shell wm size` |
| 相机 | 后置可用，带闪光灯（手电筒实测有效） | 应用内手电筒开关 + 截图 |
| 连接方式 | USB（`adb devices` 可见，`device` 状态） | `adb devices -l` |

---

## 3. 被测应用

| 字段 | 值 |
|---|---|
| 包名 | `com.culinary` |
| versionName / versionCode | `0.1.0` / `100` |
| 构建类型 | Debug（`assembleDebug`） |
| APK 路径 | `app/build/outputs/apk/debug/app-debug.apk` |
| APK SHA-256 | `3A162036214F1A315E5201550AD8EC80C4E1ABAEDB8AAB4ED2E1C6B639E406F1` |
| APK 大小 | 39,854,267 字节 |
| 被测源码 | 本提交（Stage 5 真机验证提交）；上一功能提交 `98b8a22` + 本轮真机问题修复（见 §7） |
| 安装方式 | `adb install -r`（覆盖安装，另做过 `pm clear` 全新数据验证） |

---

## 4. 环境

| 字段 | 值 |
|---|---|
| 日期时间 | 2026-11-05 01:34–01:56（Asia/Hong_Kong） |
| 测试人 | MISEBASE AI 开发工作区（自动化执行） |
| 复核人 | 待项目负责人复核（Review 状态） |
| 测试方式 | `adb` + `uiautomator dump` + `screencap` + `connectedDebugAndroidTest` |
| 应用数据 | 验证前 `adb shell pm clear com.culinary`（全新数据库），导入后未清空以保留证据 |

---

## 5. 用例与结果

> 截图位于 [`evidence/`](evidence/)；「预期 / 实际」为人工可复核的事实描述，不是测试框架断言。

### D-01 相机权限已授予时的预览与扫描框

- **前置**：`pm grant com.culinary android.permission.CAMERA`
- **步骤**：启动应用 → 底部「扫码」→ 观察
- **预期**：真实相机预览 + 白色扫描框 + 提示文案 + 手电筒/手工输入入口
- **实际**：预览为真实取景画面，扫描框与四块遮罩正常，底部「打开手电筒 / 手工输入条码」可用，左上角返回图标可见
- **结果**：通过
- **证据**：[01-scan-camera-granted.png](evidence/01-scan-camera-granted.png)

### D-02 手电筒

- **前置**：同 D-01
- **步骤**：点击「打开手电筒」→ 观察画面与按钮文案
- **预期**：闪光灯开启、画面变亮、按钮变为「关闭手电筒」
- **实际**：画面明显变亮，按钮文案切换为「关闭手电筒」；再次点击可关闭
- **结果**：通过
- **证据**：[02-scan-torch.png](evidence/02-scan-torch.png)

### D-03 权限说明页（未请求）

- **前置**：`pm revoke` + `pm set-permission-flags ... user-fixed`（模拟"拒绝两次"后的状态）→ `pm clear` 后首次进入
- **步骤**：启动 → 底部「扫码」→ 观察
- **预期**：权限说明页（标题、说明、允许按钮、手工输入入口、返回）
- **实际**：显示「需要相机权限」与本地识别说明、「允许使用相机」「手工输入条码」「返回」
- **结果**：通过
- **证据**：[03-scan-permission-required.png](evidence/03-scan-permission-required.png)

### D-04 权限拒绝与手工输入兜底

- **前置**：权限未授予且已被永久拒绝（系统不再弹窗）
- **步骤**：点击「允许使用相机」→ 系统立即返回拒绝 → 观察
- **预期**：拒绝说明页 + 重新请求 + 手工输入 + 系统设置提示；不使用模拟器造假
- **实际**：显示「相机权限被拒绝」、原因说明、「重新请求权限」「手工输入条码」「返回」以及"已拒绝两次请到系统设置开启"的提示
- **结果**：通过
- **证据**：[04-scan-permission-denied.png](evidence/04-scan-permission-denied.png)

### D-05 相机识别（真实条码）

- **前置**：权限已授予，相机对准桌面上的实物条码
- **步骤**：进入扫码页 → 等待识别 → 观察确认页
- **预期**：识别后自动暂停分析并进入确认页，展示条码值、格式、来源
- **实际**：识别到 `8809184833631`，格式 `EAN13`，来源「相机识别」；识别后离开扫码页（分析随页面销毁）
- **结果**：通过
- **证据**：[05-scan-camera-recognition.png](evidence/05-scan-camera-recognition.png)
- **附注**：该条码校验位合法（EAN-13 校验通过），不是误识别噪声

### D-06 手工输入兜底

- **前置**：权限被拒绝（D-04 状态）
- **步骤**：「手工输入条码」→ 输入 `6901234567892` → 「使用该条码」
- **预期**：进入确认页，来源显示「手工输入」、格式「未知格式」
- **实际**：与预期一致
- **结果**：通过
- **证据**：[06-scan-manual-entry.png](evidence/06-scan-manual-entry.png)

### D-07 导入确认页

- **前置**：D-06 完成（数据库中还没有食材）
- **步骤**：观察确认页 → 选择「新建食材」→ 输入名称 `scantestsalt` → 数量 `1`
- **预期**：展示识别结果、导入目标、数量、确认按钮；无食材时提示"请直接新建"
- **实际**：与预期一致；标题栏显示「导入确认」
- **结果**：通过
- **证据**：[07-scan-confirm.png](evidence/07-scan-confirm.png)

### D-08 确认导入（新建食材 + 入库）

- **前置**：D-07 状态
- **步骤**：点击「确认导入」
- **预期**：食材创建 + 商品映射 + 批次 + 流水；展示「已入库」结果
- **实际**：显示「已入库 scantestsalt 1 g」，并出现「该条码已导入过 · 上次导入 01:44 · 1 g」提示，确认按钮置灰
- **结果**：通过
- **证据**：[08-scan-import-result.png](evidence/08-scan-import-result.png)
- **数据库核对（`adb exec-out run-as com.culinary cat databases/culinary.db`）**：
  - `ingredient` = 1，`product` = 1，`inventory_batch` = 1，`inventory_ledger` = 1
  - 流水幂等键 = `scan-import:6901234567892`（由条码决定）
  - 食材 `default_unit_id` 与商品 `stock_unit_id` = 质量基准单位 `g`
  - `barcode` 表 = 0 行（扫码模块不写业务主数据表）

### D-09 重复识别 / 重复确认（幂等）

- **前置**：D-08 已导入同一码
- **步骤**：返回扫码 → 手工输入同一条码 → 观察确认页
- **预期**：显示「该条码已导入过」，确认按钮禁用；再次确认不产生新批次/流水
- **实际**：新进入的确认页显示「该条码已导入过 · 上次导入 11-05 01:44 · 1 g；重复确认不会重复入库。」，「确认导入」置灰
- **结果**：通过
- **证据**：[09-scan-duplicate-guard.png](evidence/09-scan-duplicate-guard.png)

### D-10 连续扫码（第二个不同码、复用食材）

- **前置**：D-09 状态
- **步骤**：返回扫码 → 手工输入第二个码 `MISEBASEQRSCAN001` → 选择已有食材 `scantestsalt` → 确认导入
- **预期**：不同码独立入库，复用同一食材，不重复创建食材/商品
- **实际**：显示「已入库 scantestsalt 1 g」
- **结果**：通过
- **证据**：[10-scan-second-import.png](evidence/10-scan-second-import.png)
- **数据库核对**：`ingredient` = 1、`product` = 1、`inventory_batch` = 2、`inventory_ledger` = 2；
  两个幂等键分别为 `scan-import:6901234567892`、`scan-import:MISEBASEQRSCAN001`

### D-11 条码/二维码解码能力（真机 instrumentation）

- **前置**：`feature:scan` 的 `androidTest` 资源包含 EAN-13、Code128、QR 文本、QR URL 各 1 张
- **步骤**：`.\gradlew.bat :feature:scan:connectedDebugAndroidTest`
- **预期**：4 个用例在真机上全部通过（与相机分析路径使用同一 ML Kit 17.3.0 Bundled 客户端）
- **实际**：`Starting 4 tests on V2429A - 16` → `Tests 4/4 completed. (0 skipped) (0 failed)`
  - EAN-13 → `6901234567892`（`FORMAT_EAN_13`）
  - Code128 → `MISEBASE123`（`FORMAT_CODE_128`）
  - QR 文本 → `MISEBASE-QR-SCAN-001`（`FORMAT_QR_CODE`）
  - QR URL → `https://example.test/scan?item=soy sauce&lot=42`（`FORMAT_QR_CODE`）
- **结果**：通过
- **证据**：Gradle 输出（`feature/scan/build/reports/androidTests/connected/`）；用例源码
  [`BarcodeDecodingInstrumentedTest.kt`](../../feature/scan/src/androidTest/java/com/culinary/feature/scan/recognize/BarcodeDecodingInstrumentedTest.kt)

### D-12 扫码页「返回」

- **前置**：从「酱汁」入口进入「扫码」
- **步骤**：点击左上角返回图标
- **预期**：回到上一页（一级入口无上级时回到启动入口）
- **实际**：回到「酱汁档案」页
- **结果**：通过
- **证据**：`uiautomator dump` 文本（`酱汁档案 | 共 0 个酱汁 | ...`）

### D-13 稳定性与异常

- **前置**：整轮验证结束后
- **步骤**：`adb logcat -d` 检索 `FATAL`、`AndroidRuntime`、`Exception`
- **预期**：无崩溃、无未捕获异常
- **实际**：无匹配记录；应用在权限切换、页面往返、连续导入后仍正常响应
- **结果**：通过
- **证据**：logcat 过滤结果（本轮会话记录）

---

## 6. 权限状态矩阵

| 状态 | 进入方式 | 页面表现 | 可否扫码 |
|---|---|---|---|
| 已授予 | `pm grant` | 相机预览 + 扫描框 + 手电筒 | 可以 |
| 未请求 | `pm clear` 后首次进入 | 权限说明页 + 允许按钮 + 手工输入 | 仅手工输入 |
| 被拒绝 | 请求后系统返回拒绝（含永久拒绝） | 拒绝说明页 + 重新请求 + 手工输入 + 设置提示 | 仅手工输入 |
| 无相机 | 设备无 `FEATURE_CAMERA_ANY` | 「没有可用的相机」+ 自动进入手工输入 | 仅手工输入（JVM/UI 测试覆盖，本机无此硬件） |

---

## 7. 局限与偏差（必须如实记录）

1. **二维码的相机取景未能在真机上稳定复现**：本轮真机相机识别到的是实物 EAN-13（D-05）。二维码的**解码能力**在真机上用同一 ML Kit Bundled 客户端 + 打包图片验证通过（D-11），但"把二维码举到镜头前"这一步需要人工摆放实物，AI 工作区无法自行完成。**结论**：二维码识别能力已验证，取景交互建议在负责人复核时用实物二维码再确认一次。
2. **真机验证中发现并修复的两个问题**（均已包含在被测 APK 中）：
   - 扫码页底部动作区在深色相机画面上对比度过低 → 增加底部渐变遮罩、次要按钮改白色描边，并把「返回」移到左上角图标（原布局把「返回」挤成两行）；严重级别：中（可用性）。
   - 新建食材默认单位取「单位表第一行」，实测曾出现 `1 mg` 的荒谬入库单位 → 改为「质量基准单位 → 计数单位 → 第一行」的优先顺序；严重级别：高（数据语义）。已补 JVM 测试覆盖。
3. **手工输入的中文名称未在真机上验证**：`adb shell input text` 不支持中文，本轮新建食材名称为 ASCII（`scantestsalt`）。中文字符输入属于 Compose 文本框与系统输入法行为，Stage 3 食材编辑器已覆盖同类路径，本轮未重复验证。
4. **未验证项**：低电量/锁屏中断、连续快速扫码 10 次以上的压力场景、无相机设备（本机有相机）。这些不在 MB-D-011 的最低要求内。

---

## 8. 复现步骤（供复核）

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
# 1) 安装并清数据
& $adb install -r app\build\outputs\apk\debug\app-debug.apk
& $adb shell pm clear com.culinary
# 2) 权限说明页 / 拒绝页
& $adb shell am start -n com.culinary/.MainActivity
#    点击底部「扫码」→ 观察说明页 → 点「允许使用相机」→ 选「禁止」
# 3) 手工输入导入
#    「手工输入条码」→ 输入 6901234567892 → 「使用该条码」→「新建食材」→ 名称 → 「确认导入」
# 4) 重复识别
#    返回扫码 → 再输入 6901234567892 → 确认页应显示「该条码已导入过」且按钮禁用
# 5) 真机解码测试
.\gradlew.bat :feature:scan:connectedDebugAndroidTest --console=plain
```

---

## 9. 复核结论（待填）

| 项 | 结论 | 复核人 | 日期 |
|---|---|---|---|
| 设备信息真实性 | 待复核 | | |
| 权限三态 | 待复核 | | |
| 条码/二维码识别 | 待复核（二维码取景建议实物复测） | | |
| 连续扫码/重复识别 | 待复核 | | |
| 导入幂等 | 待复核 | | |

> 本记录为 `Review`，不代替项目负责人对 Stage 5 的签收。
