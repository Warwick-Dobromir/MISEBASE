# MB-S5-01 Stage 5 执行报告（扫码导入纵向切片）

**文档编号**：MB-S5-01
**版本**：`1.1.0`
**状态**：`CLOSED`
**日期**：2026-11-05
**阶段**：Stage 5（`MB-S5-001` 任务书）
**决策依据**：MB-D-004（`feature:scan`，`ACCEPTED`）、MB-D-011（真机验证，`ACCEPTED`）、MB-D-009（N1）
**审查结论**：**通过**（2026-11-05）；关闭记录见 [MB-S5-CLOSE-001](MB-S5-CLOSE-RECORD.md)
**结论**：Stage 5 已于 2026-11-05 关闭；**未进入 Stage 6**（Stage 6 需先签收 MB-D-006 / MB-D-010 / MB-D-012）

---

## 1. 执行摘要

| 项 | 结果 |
|---|---|
| 模块集 | **13 → 14**，新增 `feature:scan`（Stage 5 起，MB-D-004） |
| 扫码页面 | CameraX 预览、扫描框、手电筒、返回、权限说明/拒绝/无相机三态、手工输入兜底 |
| 识别 | ML Kit 17.3.0 Bundled 条码/二维码；识别后暂停分析；相同码 2 秒去抖；一次性消费 |
| 识别结果接口 | `core:domain` 端口：`ScanIngredientGateway`（products 实现）、`ScanStockGateway`（inventory 实现） |
| 导入确认 | 确认页展示识别结果与匹配结果；事务（批次+流水）、**事件级**幂等键、失败明确补偿 |
| 入口切换 | N1 第五入口从占位改为真实页面；五入口顺序不变 |
| 真机验证 | vivo V2429A / Android 16 / API 36：13 个用例通过（含相机识别 EAN-13、权限三态、重复识别拦截） |
| 缺陷修复 | MB-S5-002 修正「同码只能导入一次」：事件级幂等键 + 再次入库 + 取消/重试语义（见 [MB-S5-08](MB-S5-08-IDEMPOTENCY-FIX.md)） |
| 测试 | **817 次执行 / 514 唯一 JVM 用例 / 0 失败**（Stage 4 基线 660/416/0；Stage 5 首轮 801/503，修正后 817/514） |
| 构建 | `assembleDebug` 成功（39,854,267 字节，SHA-256 `F1EB9F…4F10`） |
| 质量门 | `ktlintCheck`、`detekt`、模块依赖、违规注入、休眠表、文档一致性全部 PASS |
| 禁止项 | 无 Schema/Entity/迁移/数据库文件变更；无备份/恢复实现变更；休眠表 0 引用；未启用 CI；未创建签名 |
| 结论 | 审查通过，状态 **`CLOSED`**（2026-11-05） |

---

## 2. 启动前置检查与决策状态

### 2.1 前置检查（任务书 §6）

| # | 检查 | 结果 |
|---|---|---|
| 1 | `git status --porcelain` 为空；记录 HEAD | 空；HEAD = `450dc9ec61b43c6c6ae202c511c0c07ac3ba02b2` |
| 2 | HEAD 等于或严格后继 `checkpoint/misebase-decisions-stage5-20261105`（`450dc9e`） | 等于（`git rev-parse` 一致） |
| 3 | 读取 S0-05 / MB-S0-07 / README：15 项 ACCEPTED、MB-D-004/011 ACCEPTED、3 项 PROPOSED | 一致（MB-D-006/010/012 仍 `PROPOSED`） |
| 4 | Schema v2、33 表；`1.json`、`2.json` 存在 | 一致（`core/database/schemas/.../1.json`、`2.json`） |
| 5 | 当前模块集 13；`feature:scan` 不存在 | 一致（`settings.gradle.kts` 13 个 include，无 scan 目录） |
| 6 | 备份/恢复实现未修改；`StartupCoordinator.EXPECTED_SCHEMA_VERSION = 2` | 一致 |
| 7 | 记录真机信息；`adb devices` 可见 | 一致（见 §3） |
| 8 | 任一不符即停止 | 全部通过，未触发停止条件 |

### 2.2 决策状态（15/18 ACCEPTED，3 项 PROPOSED）

| 状态 | 决策 |
|---|---|
| `ACCEPTED`（15） | MB-D-001、002、003、004、005、007（`CLOSED`）、008、009、011、013、014、015、016、017、018 |
| `PROPOSED`（3，不得当作已批准） | **MB-D-006** 测试口径、**MB-D-010** 远端 CI、**MB-D-012** 发布签名 |

本阶段对三项 PROPOSED 的处理：未启用远端 CI；未创建/变更 Release 签名；未改写历史报告测试口径，
按实际唯一用例数（503）与执行次数（801）报告。

---

## 3. 真机设备信息

| 字段 | 值 |
|---|---|
| 品牌 / 厂商 | vivo |
| 型号 / 设备代号 | V2429A / PD2429 |
| 序列号 | `10CEBG0DGT0008D`（`adb devices -l` 状态 `device`） |
| Android 版本 | 16 |
| API Level | 36 |
| 构建号 | `compiler260905102531` |
| ABI / 屏幕 | arm64-v8a / 物理 1260×2800（override 1080×2400） |
| 相机 | 后置可用、闪光灯可用（手电筒实测） |

完整用例与截图见 [MB-S5-02 真机验证记录](MB-S5-02-DEVICE-VERIFICATION.md)。

---

## 4. 执行前 Git 状态与 Stage 5 起点

| 项 | 值 |
|---|---|
| 分支 | `misebase/main` |
| 执行前 `git status --porcelain` | 空 |
| 执行前 HEAD | `450dc9ec61b43c6c6ae202c511c0c07ac3ba02b2` |
| Stage 5 起点标签 | `checkpoint/misebase-decisions-stage5-20261105` |
| 标签解析提交 | `450dc9ec61b43c6c6ae202c511c0c07ac3ba02b2` |
| `framework-0.1.0` | `2b523de4b87e37a2eb08b4c7c851549350ad811a`（未移动） |

---

## 5. `feature:scan` 模块创建与依赖规则

| 项 | 内容 |
|---|---|
| 声明 | `settings.gradle.kts` 增加 `include(":feature:scan")`；注释改为"Stage 5 起 14 模块（MB-D-004）" |
| 构建 | `feature/scan/build.gradle.kts`：`culinary.android.library` + `compose` + `hilt`；CameraX 1.4.2（core/camera2/lifecycle/view）、ML Kit 17.3.0 Bundled |
| 清单 | `feature/scan/src/main/AndroidManifest.xml`：`CAMERA` 权限；三个 `uses-feature` 均为 `required=false`（无相机可降级）；无 `INTERNET` |
| 依赖（声明边） | `core:domain`、`core:common`、`core:designsystem`；**不依赖**任何 feature、不依赖 `core:data`/`core:database`/`core:platform` |
| 配置 | `config/module-dependencies.json`：`expectedModuleCount = 14`、新增模块条目与允许边、`feature:scan` 从禁止列表移除 |
| 脚本 | `check-module-deps.ps1`：14 模块标题、`MB-D-004` 决策标签、新增「`feature:scan` → `feature:products`」违规注入并断言 `V-FEATURE-FEATURE` + `V-IMPORT-UNDECLARED` |
| 结果 | 依赖检查 PASS：**14 节点 / 39 条声明边 / 拓扑无环**；违规注入自测 PASS（8 类违规全部检出） |

---

## 6. 识别核心与去抖

| 组件 | 位置 | 职责 |
|---|---|---|
| `ScanCapture` / `ScanSource` | `core:domain/barcode` | 识别结果契约（条码值、格式、来源）与长度校验 |
| `ScanFrameGate` | `core:domain/barcode` | 一次性消费 + 相同码去抖窗口（默认 2 s）+ `resume`/`reset` |
| `MlKitBarcodeDecoder` | `feature:scan/recognize` | 单帧 ML Kit 解码；无论成功失败都关闭 `ImageProxy` |
| `BarcodeFormatNames` / `BarcodeValueMapper` | `feature:scan/recognize` | 格式名映射（14 种）、空值/超长丢弃 |
| `CameraBarcodeSession` | `feature:scan/camera` | `Preview` + `ImageAnalysis(KEEP_ONLY_LATEST)`、暂停/恢复、手电筒、释放 |

去抖语义（真机与 JVM 双重验证）：

```text
offer(value, now):
  已消费 → 拒绝
  同码且 now - last < 2s → 拒绝
  否则接受并置 consumed
resume(): consumed=false，保留窗口记忆
```

---

## 7. 扫码页面

| 元素 | 实现 |
|---|---|
| 权限 | `ActivityResultContracts.RequestPermission`；`CHECKING/NOT_REQUESTED/GRANTED/DENIED/UNAVAILABLE` 五态 |
| 权限说明页 | 标题 + 本地识别说明（不上传、无网络）+ 允许按钮 + 手工输入 + 返回 |
| 拒绝处理 | 拒绝说明 + 重新请求 + 手工输入 + 系统设置提示 |
| 相机预览 | `PreviewView` + `AndroidView`，生命周期绑定 `LocalLifecycleOwner`，销毁即释放 |
| 扫描框 | 四块 45% 遮罩 + 72% 宽白色圆角描边（Canvas 绘制，不拦截触摸） |
| 手电筒 | `CameraControl.enableTorch`；不支持时回滚开关并提示，不假装成功 |
| 返回 | 左上角半透明圆形图标（一级入口无上级时回一级入口） |
| 手工输入 | 非空 + ≤512 字符校验；与相机结果共用确认页与幂等 |

详见 [MB-S5-03 扫码页面说明](MB-S5-03-SCAN-PAGES.md)。

---

## 8. 导入确认与幂等

| 项 | 实现 |
|---|---|
| 写前查重 | `LookupScanImportUseCase`：按 `scan-import:<条码>` 查流水；命中则禁用确认 |
| 识别结果接口 | `ScanIngredientGateway`（products）、`ScanStockGateway`（inventory），均定义在 `core:domain` |
| 食材创建/映射 | `ProductScanIngredientGateway`：复用食材 / `SaveIngredientUseCase` 新建 / 单位优先质量基准 |
| 入库 | `InventoryScanStockGateway` → `RecordStockReceiptUseCase`：批次 + 流水同一事务，幂等键查重 |
| 幂等 | 键只由条码值决定；重复确认返回 `idempotentReplay = true`，不新增批次/流水 |
| 补偿 | 本次新建食材且入库失败 → 归档该食材；复用食材失败不归档；错误文案注明「已撤回本次新建的食材」 |
| 真机核对 | 两次导入后：`ingredient=1, product=1, batch=2, ledger=2`，两键为 `scan-import:6901234567892`、`scan-import:MISEBASEQRSCAN001`；`barcode=0` |

详见 [MB-S5-04 识别接口与导入确认说明](MB-S5-04-IMPORT-CONTRACT.md)。

---

## 9. 入口切换

| 项 | 内容 |
|---|---|
| 导航 | `CulinaryNavHost`：`scanGraph(navController)` + `settingsGraph()`；`placeholderGraph()` 删除 |
| 路由 | `scan`（一级入口）→ `scan/confirm?code={code}&format={format}&source={source}`（导航参数，URL 编码） |
| 返回 | 扫码页「返回」在无上级时回启动入口（`backOrStart`） |
| 标题 | 顶栏区分「扫码」与「导入确认」 |
| N1 | 五入口顺序、路由字符串不变；`CulinaryDestinationTest` 与 `NavigationShellTest` 通过 |

---

## 10. 真机验证（摘要）

| 用例 | 结果 |
|---|---|
| D-01 相机预览/扫描框 | 通过 |
| D-02 手电筒 | 通过（画面变亮，文案切换） |
| D-03 权限说明页 | 通过 |
| D-04 权限拒绝 + 手工输入 | 通过 |
| D-05 相机识别 EAN-13 `8809184833631` | 通过（格式 `EAN13`、来源「相机识别」） |
| D-06 手工输入 | 通过 |
| D-07 确认页 | 通过 |
| D-08 新建食材 + 入库 `1 g` | 通过（数据库核对一致） |
| D-09 重复识别拦截 | 通过（确认按钮禁用） |
| D-10 第二个码复用食材 | 通过（2 批次 / 2 流水 / 1 食材） |
| D-11 真机解码 EAN-13/Code128/QR×2 | 通过（4/4） |
| D-12 扫码页返回 | 通过 |
| D-13 稳定性 | 通过（logcat 无 FATAL/AndroidRuntime） |

局限（如实记录）：二维码"相机取景"未人工复测；中文名称输入未在真机验证；
真机验证中发现并修复了「动作区对比度/返回按钮换行」与「新建食材默认单位 `1 mg`」两个问题。

---

## 11. 测试与构建结果

```powershell
.\gradlew.bat test assembleDebug ktlintCheck detekt --console=plain --no-daemon
```

| 项 | 结果 |
|---|---|
| 执行次数 | **801** |
| 唯一 JVM 用例 | **503** |
| 失败 | **0** |
| Stage 4 基线 | 660 / 416 / 0 |
| 增量 | +141 执行 / +87 唯一（扫码 86 + 导航 1） |
| `assembleDebug` | 成功；APK 39,854,267 字节；SHA-256 `3A162036214F1A315E5201550AD8EC80C4E1ABAEDB8AAB4ED2E1C6B639E406F1` |
| `ktlintCheck` / `detekt` | PASS |
| 依赖检查 / 违规注入 | PASS（14 模块 / 39 边 / 8 类违规注入） |
| 休眠表检查 | PASS（11 表 0 处新引用） |
| 文档一致性 | PASS |
| 真机 instrumentation | PASS（4/4） |

分布见 [MB-S5-05 测试矩阵](MB-S5-05-TEST-MATRIX.md)。

---

## 12. Git 提交、标签与变更范围

### 12.1 分提交（Stage 5）

| # | 提交 | 内容 |
|---|---|---|
| 1 | `f0afc6b` | `feat(scan)`：新建 `feature:scan`，模块集 13→14，依赖配置与脚本同步 |
| 2 | `ac83f19` | `feat(scan)`：识别核心（`ScanFrameGate`、`ScanCapture`、ML Kit 解码、CameraX 会话） |
| 3 | `07757e5` | `feat(scan)`：扫码页面（权限、扫描框、手电筒、手工输入） |
| 4 | `6fdadcd` | `feat(scan)`：识别结果接口与导入确认（products/inventory 端口 + 幂等 + 补偿） |
| 5 | `98b8a22` | `feat(app)`：扫码入口切换为真实页面（N1 第五入口） |
| 6 | `f0df352` | `test(scan)`：真机验证记录与解码用例（MB-D-011）、真机问题修复 |
| 7 | 本报告提交 | `docs(stage5)`：Stage 5 报告、页面说明、接口说明、测试矩阵、决策映射、README/索引 |

### 12.2 变更范围（`450dc9e..f0df352`，代码与真机记录）

```text
68 files changed, 4825 insertions(+), 27 deletions(-)
新增（A）58，修改（M）10，删除（D）0
```

| 区域 | 文件数 |
|---|---:|
| feature/scan（含 androidTest 资源） | 32 |
| docs/12-stage5（真机记录 + 10 张截图） | 11 |
| core/domain（契约、闸门、用例与测试） | 9 |
| feature/products | 4 |
| app | 5 |
| feature/inventory | 3 |
| settings / config / 脚本 / app 构建 | 5 |

### 12.3 边界核对

| 边界 | 命令 | 结果 |
|---|---|---|
| Schema / Entity / 表结构 / 迁移 / 数据库文件 | `git diff --name-only 450dc9e..HEAD -- core/database` | **空** |
| 备份/恢复实现 | `... -- core/data/src/main/java/com/culinary/core/data/backup core/platform/src/main/java/com/culinary/core/platform/work` | **空** |
| 休眠表引用 | `check-dormant-tables.ps1` | PASS（0 处） |
| `purchase_order*` | 全仓检索 | 无业务引用 |
| 技术栈 | `gradle/libs.versions.toml` | 未修改 |
| 既有标签 | `git rev-parse framework-0.1.0^{commit}` | `2b523de`（未移动） |

### 12.4 标签

| 标签 | 值 |
|---|---|
| `checkpoint/misebase-stage5-20261105` | annotated tag，指向本报告所在提交；标签对象哈希与解析提交记录在检查点回执（`MB-S5-07`）中 |

`git status --porcelain`：提交后为空（见回执）。

---

## 13. 未解决问题与风险

| # | 项 | 级别 | 说明 / 处置 |
|---|---|---|---|
| R1 | ~~同一条码只能导入一次~~ | 中 | **已修复（MB-S5-002，2026-11-05）**：幂等键改为事件级 `scan-import:<条码>:<operation_id>`；同款商品可多次入库，单次确认仍幂等。残留 F1（进程被杀丢失 `operation_id`）见 [MB-S5-08](MB-S5-08-IDEMPOTENCY-FIX.md) §7 |
| R2 | 食材创建与入库跨两个事务 | 中 | 采用"失败归档本次新建食材"的明确补偿；归档失败会残留一条 ACTIVE 食材（已用文案提示） |
| R3 | 条码→商品映射未持久化 | 中 | 允许范围不含 `core:data`/`core:database`；`barcode` 表 0 行；"扫一次自动带出商品名"需新授权 |
| R4 | 二维码相机取景未人工复测 | 低 | 解码能力已在真机验证（4/4）；建议负责人复核时用实物复测 |
| R5 | 新建食材默认单位是 `g` | 低 | 计数型商品需在食材档案改默认单位（Stage 3 编辑器已支持） |
| R6 | 连续扫码压力场景未测 | 低 | 未覆盖 10+ 次快速连续扫码、低电量、锁屏中断；不阻塞本阶段 |
| R7 | true lock/断电 | 低 | 沿用 Stage 4 保留项，不在本阶段范围 |
| R8 | `feature:scan` 与 `core:platform` 的解码器重复 | 低 | 任务书固定 `feature:scan` 依赖为 core 三件，故未复用 `core:platform` 的旧封装；两份都只做 Task→Outcome 适配，规则集中在 `core:domain` |

---

## 14. 审查结论与关闭（2026-11-05）

**审查结论：通过。** 依据（含 MB-S5-002 修正后的复查）：

1. 启动前置检查 8 项全部通过，未触发任何停止条件；
2. `feature:scan` 已创建，模块集 14，依赖规则与违规注入测试同步；
3. 扫码页面、权限三态、手电筒、手工输入、去抖/一次性消费、导入确认与**事件级幂等**全部落地；
4. 识别结果经 `core:domain` 端口交给 `feature:products` / `feature:inventory`，`feature:scan` 不写库；
5. 真机验证完成并留存记录（设备信息、权限状态、条码/二维码解码、连续/重复、再次入库、取消、快速连点、异常、结论）；
6. 无 Schema/Entity/迁移/数据库文件变更，无备份/恢复变更，休眠表 0 引用；
7. JVM 测试 0 失败（**817 / 514**），Debug APK 构建成功，质量门全绿；
8. Git 变更严格在任务书允许范围；检查点与关闭标签齐备。

**审查发现与处置**：审查指出"同一条码只能导入一次"的业务缺陷（MB-S5-01 首版 R1），
已由 MB-S5-002 修复（`af3ea88`）并复验，测试 503→514 唯一用例、801→817 执行，未回退代码。

**关闭**：Stage 5 于 2026-11-05 置为 `CLOSED`；关闭标签、Stage 6 起点标签、保留项与
Stage 6 前置条件见 [MB-S5-CLOSE-001](MB-S5-CLOSE-RECORD.md)。

**未做的事**：未进入 Stage 6、未启用 CI、未创建签名、未替负责人签收任何决策。

---

## 15. 停止声明

Stage 5 已关闭（`CLOSED`），**未进入 Stage 6**，**未替负责人签收**任何决策。
Stage 6 仍需先签收 MB-D-006 / MB-D-010 / MB-D-012（见关闭记录 §6）。

---

## 16. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0.0 | 2026-11-05 | 首次提交 Stage 5 执行报告（Review） | MISEBASE AI 开发工作区 |
| 1.0.1 | 2026-11-05 | 按 MB-S5-002 修正"同一商品只能导入一次"：幂等键改为事件级；测试口径更新为 817 / 514 / 0；详见 [MB-S5-08](MB-S5-08-IDEMPOTENCY-FIX.md)。Stage 5 仍为 Review | MISEBASE AI 开发工作区 |
| 1.1.0 | 2026-11-05 | 审查通过，状态置为 `CLOSED`；补记审查结论、MB-S5-002 修复与关闭标签；见 [MB-S5-CLOSE-001](MB-S5-CLOSE-RECORD.md) | MISEBASE AI 开发工作区 |


