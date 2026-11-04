# MB-S5-06 决策映射（MB-D-004 / MB-D-011）

**文档编号**：MB-S5-06
**版本**：`1.0.0`
**状态**：`Review`
**日期**：2026-11-05
**范围**：Stage 5 对 MB-D-004、MB-D-011 的落地映射，以及与 MB-D-003/006/007/008/009/010/012/014 的一致性
**原则**：只映射已 `ACCEPTED` 的决策；`PROPOSED` 的三项（MB-D-006 / MB-D-010 / MB-D-012）**不得**写成已签收

---

## 1. MB-D-004 扫码 UI 归属（ACCEPTED，2026-11-05）

| 决策要点 | 落地 | 证据 |
|---|---|---|
| 新建独立 `feature:scan` | 目录、构建脚本、manifest、源码、测试齐全 | [`feature/scan/`](../../feature/scan/) |
| Stage 5 起模块集 **14** | `settings.gradle.kts` 增加 `include(":feature:scan")`；`config/module-dependencies.json` `expectedModuleCount = 14` | 依赖检查：14 模块 / 39 条边 |
| Stage 1–4 仍为 13 | 历史文档未改写；`framework-0.1.0` 未移动 | 本文件 §6 |
| 扫码 UI、CameraX 生命周期、ML Kit 识别归 `feature:scan` | `ScanScreen` / `ScanRoute` / `CameraBarcodeSession` / `MlKitBarcodeDecoder` | [MB-S5-03](MB-S5-03-SCAN-PAGES.md) |
| 识别结果通过**明确接口**交给业务模块 | `core:domain` 的 `ScanIngredientGateway` / `ScanStockGateway` + `ScanCapture` / `ScanStockReceipt` | [MB-S5-04](MB-S5-04-IMPORT-CONTRACT.md) |
| 食材创建/导入归 `feature:products` | `ProductScanIngredientGateway`（实现端口，`@Binds` 绑定） | 同上 §2 |
| 库存操作归 `feature:inventory` | `InventoryScanStockGateway`（复用 `RecordStockReceiptUseCase`） | 同上 §2 |
| `feature:scan` 不直接依赖其他 feature | 构建依赖只有 core 三件；依赖检查含 `feature:scan → feature:products` 违规注入 | `check-module-deps.ps1` self-test |
| 扫码模块不直接写业务表 | 确认页只调用两个端口；真机 `barcode` 表 0 行、无 schema/entity 变更 | [MB-S5-02](MB-S5-02-DEVICE-VERIFICATION.md) §5 D-08 |
| N1 五入口不变，扫码仍为第五个底部入口 | 路由 `scan` 保留在 `CulinaryDestination` 第五位；仅把占位换成真实页面 | `CulinaryDestinationTest`、`NavigationShellTest` |
| 允许调图标/文案/视觉，不改信息架构 | 扫描框、底部遮罩、左上返回图标属视觉表现 | [MB-S5-03](MB-S5-03-SCAN-PAGES.md) §6 |

> MB-D-004 §5 明确"本次签收不构成 Stage 5 开发授权"。本轮开发由 **MB-S5-001 任务书**授权；
> 本文件只记录 Stage 5 实现与 MB-D-004 的对应关系，不改变决策状态。

---

## 2. MB-D-011 真机验证（ACCEPTED，2026-11-05）

| 决策要点 | 落地 | 证据 |
|---|---|---|
| 至少一台真实 Android 设备 | vivo V2429A，`adb devices` = `device` | [MB-S5-02](MB-S5-02-DEVICE-VERIFICATION.md) §2 |
| API 26+（推荐 33+） | Android 16 / API 36 | 同上 |
| 模拟器不可替代真机 | 全部真机用例在 V2429A 上执行；JVM/Robolectric 测试只作为补充 | 同上 |
| 必须验证相机权限 | 已授予 / 未请求 / 被拒绝 三态 | 同上 §5 D-01/D-03/D-04、§6 矩阵 |
| 必须验证扫码识别 | 相机识别 EAN-13 `8809184833631`（格式 `EAN13`、来源「相机识别」） | 同上 D-05 |
| 必须验证页面交互 | 手电筒、扫描框、返回、手工输入 | 同上 D-02/D-06/D-12 |
| 必须验证应用稳定性 | logcat 无 `FATAL`/`AndroidRuntime`；连续导入后仍正常 | 同上 D-13 |
| 必须保留测试记录（设备/系统/权限/条码/二维码/连续/重复/异常/结论） | MB-S5-02 含 13 个用例、10 张截图、数据库核对与局限说明 | 同上 |
| 记录字段（设备、系统、应用哈希、用例、结果、证据、环境、问题） | 按 MB-D-011 §3.2 字段组织 | 同上 §2/§3/§4/§5/§7 |

**偏差（如实记录）**：二维码的**解码**已在真机验证（instrumentation 4/4），但"把实物二维码举到镜头前"
这一步 AI 工作区无法自行完成，留待负责人复核时复测；见 MB-S5-02 §7。

---

## 3. 与 MB-D-003（13 模块为 Stage 1–4 目标）

- 13 与 14 不冲突：MB-D-003 的 13 是 Stage 1–4 目标；MB-D-004 在其上增加第 14 个模块；
- 本轮未把 13 写成"历史错误"，也未在文档里把 14 写成 Stage 1–4 的事实；
- `framework-0.1.0` 仍固定在 `2b523de`，未移动。

---

## 4. 与 MB-D-006 / MB-D-010 / MB-D-012（仍 PROPOSED）

| 决策 | 本阶段做法 | 是否越界 |
|---|---|---|
| MB-D-006 验收与测试口径 | 不改写历史报告口径；Stage 5 按**实际唯一用例数**（503）报告，并同时给出执行次数（801） | 未越界 |
| MB-D-010 远端 CI | **未启用**任何远端 CI；真机 instrumentation 只在本地手动执行 | 未越界 |
| MB-D-012 发布签名 | **未创建/未变更** Release 签名；只产出 Debug APK | 未越界 |

---

## 5. 与其他已接受决策的一致性

| 决策 | 一致性 |
|---|---|
| MB-D-007（备份一致性，CLOSED） | 未修改 `AutoBackupWorker`、`BackupArchiveWriter`、`RestoreExecutor`、写屏障、WAL checkpoint；扫码产生的食材/商品/库存在既有 Repository 与备份范围内（`git diff` 可查） |
| MB-D-008（UI 方向 A） | 扫码页使用既有主题、`SectionHeader`/`ErrorBanner`/`StatusBadge`/`FilterChip`；暖中性 + Orange 强调不变 |
| MB-D-009（导航 N1） | 五入口顺序与路由字符串不变；设置仍在顶栏；只替换扫码占位 |
| MB-D-013（子配方版本固定） | 未触及 |
| MB-D-014（库存扣减模式） | 扫码入库复用 `RecordStockReceiptUseCase`：先写批次再写流水，`appendIfAbsent` 原子查重；确认页写前查重、确认后才写 |
| MB-D-015/016/017 | 未触及（可选配料、采购清单、计算记录快照） |
| 技术栈锁定（D-013 / D-002 / D-012） | CameraX 1.4.2、ML Kit 17.3.0 Bundled 未升级；Kotlin 2.2.20、AGP 8.13.0、Gradle 8.14.3、Hilt 2.57.2、Room 2.8.5、minSdk 26 / compile+target 36 未变 |

---

## 6. Schema / 备份 / 休眠表（禁止项自查）

| 禁止项 | 结果 |
|---|---|
| Schema 版本、Entity 字段、表结构、迁移、数据库文件 | **无变更**（`core:database` 无 diff；schema 仍 v2 / 33 表，`1.json`、`2.json` 未动） |
| 备份/恢复实现 | **无变更**（`core:data/backup/**`、`core:platform/work/AutoBackupWorker.kt` 无 diff） |
| 11 张休眠表 | 无删除/迁移/引用；`check-dormant-tables.ps1` PASS |
| `purchase_order*` 表 | 未使用 |
| 技术栈升级/更换 | 未发生 |
| CI / 签名 | 未启用 / 未创建 |
| 既有 checkpoint 标签 | 未移动（`git tag -l` 与 `rev-parse` 见检查点回执） |
| G3-1 | 未恢复、未纳入 |
| PROPOSED 写成 ACCEPTED | 未发生 |
| 用模拟器替代真机 | 未发生（真机记录见 MB-S5-02） |

---

## 7. 结论

MB-D-004 与 MB-D-011 的已签收内容在 Stage 5 均有可检查的落地证据；
MB-D-006 / MB-D-010 / MB-D-012 保持 `PROPOSED`，本阶段未启用 CI、未创建签名、未改写历史测试口径。
本文件不构成任何决策的签收或关闭。
