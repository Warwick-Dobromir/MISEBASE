# G2 阶段 9（质量门禁与基线）完成报告

**文档编号**：R19
**版本**：`1.0.0`
**状态**：`Review`（待项目负责人验收）
**日期**：2026-11-04
**实施依据**：[R01 0.2.2](R01-FRAMEWORK-SEQUENCE.md) §12、R09、E05 §13、E06
**回档点**：`baseline/g2-stage8-20261104`（`18a5aa8`）

---

## 1. 任务目标

质量门禁：CI、lint、迁移测试、备份/恢复演练、离线冒烟、Release/R8、依赖规则、基线与文档。

## 2. 交付内容

| 项 | 内容 |
|---|---|
| CI | `.github/workflows/ci.yml`：ktlint、detekt、lint、test、assembleDebug + assembleRelease |
| 迁移测试 | `CulinaryDatabaseMigrationTest`（MigrationTestHelper，读取导出的 `1.json`），模拟器 2 例 |
| Release/R8 | `release` 开启 `isMinifyEnabled`/`isShrinkResources`，产出 APK + `mapping.txt` |
| 恢复 | `DatabaseRestorer`：SQLite 头校验 → 回滚备份 → 清 sidecar → 临时文件 + fsync + 原子替换 → 失败回滚 |
| 演练 | `DatabaseRestoreDrillTest`：真实 Room 库 → 归档 → 校验 → 取 DB → 恢复 → 只读计数 |
| 离线 | 断网冷启动冒烟；APK 无 `INTERNET` |
| 依赖规则 | `scripts/check-module-deps.ps1` 通过（15 模块、43 边、无 `core:model`） |

## 3. 退出条件核对（R01 §12.2）

| # | 条件 | 状态 | 证据 |
|---|---|---|---|
| 1 | CI 全绿 | **本地 PASS / GitHub 未运行** | `ktlintCheck detekt lintDebug test assembleDebug` → BUILD SUCCESSFUL；workflow 已提交但无远端运行记录 |
| 2 | Release/R8 构建成功 | **PASS** | `:app:assembleRelease` 成功；APK 23,533,968 bytes；`mapping.txt` 35.5 MB |
| 3 | 迁移测试全绿 | **PASS** | 模拟器 `Starting 2 tests` → `BUILD SUCCESSFUL` |
| 4 | 备份恢复演练通过 | **PASS（文件级）** | `DatabaseRestoreDrillTest` 2 例：数据完整恢复、非 SQLite 负载不破坏现有库 |
| 5 | 真机/模拟器安装通过 | **PASS** | `adb install -r` + 冷启动 |
| 6 | 基线标签与产物归档 | **PASS（标签）** | `baseline/g2-stage9-20261104`、`framework-0.1.0`；产物在 `app/build/outputs/`（未入库） |
| 7 | 框架启动报告更新为“框架已就绪” | **PASS** | R04 §9 追加 G2 后记 |

## 4. 实测记录

```text
.\gradlew.bat ktlintCheck detekt lintDebug test assembleDebug
BUILD SUCCESSFUL

.\gradlew.bat :app:assembleRelease        # R8 + resource shrink
BUILD SUCCESSFUL

.\gradlew.bat :core:database:connectedDebugAndroidTest
Starting 2 tests on Pixel_5(AVD) - 11
Finished 2 tests on Pixel_5(AVD) - 11
BUILD SUCCESSFUL

powershell -File scripts\check-module-deps.ps1
PASS: module count, module set and dependency directions match A03A.
```

离线冒烟：`svc wifi disable` + `svc data disable` 后 `ping 8.8.8.8` 返回
`Network is unreachable`，App 冷启动仍进入首页。

## 5. 未闭合项（不伪装为完成）

| 项 | 说明 |
|---|---|
| GitHub CI 实跑 | workflow 已提交，但本机环境没有远端执行记录；只证明本地等价命令全绿 |
| 恢复执行器接线 | `DatabaseRestorer` 是文件级实现，尚未接到设置页/恢复 UI；当前无法从界面发起恢复 |
| 扫码/SAF 设备验证 | 阶段 5 延后项；阶段 7 未接扫描/SAF UI，因此**仍未做设备级验证** |
| 自动化 UI/E2E | 阶段 7 已标注；本轮未补 |
| Lint 警告 | 0 error；app 仍有 18 条 warning（未使用资源、缺图标等），未列入门禁阻断 |
| 场景 3 来源、商品映射、快照元数据 | 阶段 8 遗留，未在本阶段扩展 |

## 6. Git

| 项 | 值 |
|---|---|
| 阶段 8 基线 | `baseline/g2-stage8-20261104`（`18a5aa8`） |
| 阶段 9 提交 | `b9e1af9`（CI/迁移/R8）、`c5bc8f9`（恢复演练）、本报告提交 |
| 阶段 9 基线 | `baseline/g2-stage9-20261104` |
| 框架基线 | `framework-0.1.0` |

## 7. 结论

R01 §12 的七项退出条件中六项有实测证据；“CI 全绿”仅本地等价验证，未在 GitHub 实跑，
已在 §5 明确标注。恢复执行器未接 UI、扫码/SAF 设备验证仍未完成，均列入未闭合项。

**请项目负责人验收阶段 9（G2 收口）。**

## 8. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0.0 | 2026-11-04 | 记录质量门禁、迁移/恢复/离线证据、基线与未闭合项 | 工程组 |
