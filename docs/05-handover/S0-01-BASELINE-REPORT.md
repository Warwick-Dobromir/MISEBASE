# S0-01 工程基线核验报告

**任务编号**：MB-S0-001
**文档编号**：S0-01
**版本**：`1.0.0`
**状态**：`Review`
**日期**：2026-11-04
**检查对象**：`D:\MISEBASE`
**工作基线**：`705e0ca`（G2 收档提交）；代码对照 `2b523de`（`framework-0.1.0`）

---

## 1. 核验范围

本报告只回答一个问题：**当前工作区是不是一个可信、可构建、可回档的 MISEBASE 工程起点。**

检查项：

1. 当前分支、HEAD、工作区状态；
2. `705e0ca`、`2b523de` 是否存在；
3. G1/G2 交付内容是否完整；
4. 是否混入已撤销的 G3-1 资料；
5. 当前代码是否与交接文件描述一致；
6. 能否独立构建、测试通过；
7. 技术栈是否与已确认约束一致。

**未做**：重置、覆盖、清理工作区；修改业务代码；修改数据库；升级依赖。

---

## 2. Git 基线

### 2.1 当前状态（检查时）

| 项 | 实测值 |
|---|---|
| 工作区 | `D:\MISEBASE` |
| 当前分支 | `misebase/main` |
| 当前 HEAD | `dc72025`（`docs(misebase): add stage 0 takeover review package`） |
| 工作区状态 | clean（`git status --short` 空） |
| 工作基线 | `705e0ca`（`docs(g2): add the complete G2 archive file`） |
| 代码基线 | `2b523de`（`framework-0.1.0`） |
| 远程 | 无（已移除 clone 时的 bundle origin，防止误推） |

### 2.2 必需提交

| 提交 | 存在 | 说明 |
|---|---|---|
| `705e0ca` | **是** | G2 收档提交；工作树基于它 |
| `2b523de` | **是** | G2 最终代码基线；`framework-0.1.0` 指向它 |
| `850ea63` | **是** | G2 正式交接资料提交（历史中，未作为工作树） |

命令与结果：

```text
git cat-file -e 705e0ca^{commit}  -> exit 0
git cat-file -e 2b523de^{commit}  -> exit 0
git rev-parse --short framework-0.1.0^{} -> 2b523de
```

### 2.3 标签

- 共 **20** 个标签：19 个 G2 历史标签 + 1 个阶段 0 审查检查点；
- `framework-0.1.0` → `2b523de`；
- 9 个 `baseline/g2-stage*` 全部存在；
- 新增 `checkpoint/misebase-stage0-review-20261104` → `dc72025`（审查包，不是验收）。

### 2.4 代码差异

`git diff --name-status 2b523de 705e0ca`：

```text
M  README.md
A  docs/00-governance/A-G2-001-G2-ARCHIVE.md
M  docs/00-governance/DOCUMENT-INDEX.md
```

**结论**：`705e0ca` 与 `2b523de` 之间**没有任何业务代码、schema、依赖或测试差异**；只有 G2 收档文档。

---

## 3. 交接资料完整性

### 3.1 G1

| 项 | 结果 |
|---|---|
| 压缩包 | `CULINARY-G1-ARCHIVE-20261104.zip`，281,487 字节 |
| SHA-256 | `AB17AF9CEBD9134B50CC0EAC03CB0D877ADF3CBADF3C72188A33927270EABBE8` |
| 与 `.sha256` 对照 | 一致 |
| 内容性质 | 13 份报告 + `evidence/` + `snapshot/docs` + 依赖验证工程；**无产品源码** |
| 结论 | **PASS**（G1 是设计阶段，不是代码阶段） |

### 3.2 G2

| 项 | 结果 |
|---|---|
| 交付目录文件总数 | 296 |
| MANIFEST 条目 | 295 |
| 按 SHA-256 + 大小复算 | 缺失 0 / 多余 0 |
| Git bundle | 707,890 字节；21 refs；fsck 无错误 |
| 源码 ZIP | 380 个文件；与 `2b523de` 工作树逐字节一致，0 哈希不匹配 |
| Schema | v1，31 entities，identityHash `b75e6e1b0cb2ce5b1eb24bad59e0a987` |
| 结论 | **PASS** |

### 3.3 G3 混入检查

| 检查 | 结果 |
|---|---|
| `DatabaseSnapshotter.kt` | 工作树不存在 |
| `AttachmentRestorer.kt` | 工作树不存在 |
| `docs/05-g3/` | 不存在 |
| 历史中的 G3 提交 | 存在（`17501ea`、`30f97df` 等），但已被 `ae46185` 回退 |
| 结论 | **PASS**：未混入已撤销的 G3-1 代码/资料 |

---

## 4. 构建与测试

### 4.1 环境

| 项 | 值 |
|---|---|
| JDK | 21.0.8（`JAVA_HOME` 指向 Android Studio JBR） |
| Gradle | 8.14.3（wrapper，带 SHA-256 校验） |
| Android SDK | compileSdk 36；build-tools 36.1.0 |
| JVM target | 17 |
| minSdk / targetSdk | 26 / 36 |

### 4.2 命令与结果

```text
.\gradlew.bat test assembleDebug --console=plain --no-daemon
BUILD SUCCESSFUL in 1m 1s
833 actionable tasks: 320 executed, 513 from cache
```

| 项 | 结果 |
|---|---|
| 单元测试 | **197 例 / 0 失败** |
| 测试类 | **39 个 JVM 测试类** |
| 仪器化迁移测试 | `CulinaryDatabaseMigrationTest` 1 个类 / 2 例；**本次未运行**（需要设备） |
| Debug APK | 36,603,939 字节 |
| 包名 | `com.culinary` |
| 权限 | CAMERA / WAKE_LOCK / ACCESS_NETWORK_STATE / RECEIVE_BOOT_COMPLETED / FOREGROUND_SERVICE；**无 INTERNET**（`aapt2 dump permissions`） |
| 结论 | **PASS** |

### 4.3 测试分布

| 模块 | 测试类 | 用例 |
|---|---:|---:|
| `core:common` | 2 | 9 |
| `core:domain` | 8 | 40 |
| `core:database` | 9 | 77 |
| `core:data` | 12 | 43 |
| `core:platform` | 5 | 19 |
| `app` | 2 | 6 |
| `feature:ingredients` | 1 | 3 |
| **合计** | **39** | **197** |

> 40 个 `*Test.kt` 源文件中，`CulinaryDatabaseMigrationTest` 属于 `androidTest`，不计入 JVM 39 类。

---

## 5. 技术栈核对

| 约束 | 实际 | 结论 |
|---|---|---|
| Kotlin | 2.2.20 | 一致 |
| Jetpack Compose | BOM 2025.10.01 | 一致 |
| minSdk 26 | `CulinaryConfig.MIN_SDK = 26` | 一致 |
| compileSdk / targetSdk 36 | 36 / 36 | 一致 |
| Room | 2.8.5 | 一致 |
| Android 平台 SQLite / WAL | WAL 设置 + 单写者 | 一致（细节见 §6） |
| Hilt 2.57.2 + KSP | 2.57.2 / 2.2.20-2.0.4 | 一致 |
| CameraX 1.4.2 | 1.4.2 | 一致 |
| ML Kit 17.3.0 Bundled | 17.3.0 | 一致 |
| 不更换技术栈 | 未发现替换 | 一致 |

---

## 6. 基线差异与异常

| # | 现象 | 实际证据 | 影响 |
|---|---|---|---|
| B-01 | `G2-BASELINE.md` 写 HEAD = `a4b2237` | 实际 bundle HEAD = `850ea63`；该报告生成于最终交接提交之前 | 文档滞后，不影响代码 |
| B-02 | 历史测试数 188 | 当前实测 197；差 `core:common` 9 例 | 统计口径错误 |
| B-03 | G2 报告写 41 个测试类/XML | 实际 39 个 JVM 测试类 | 文档小错 |
| B-04 | `README.md` 写「数据库尚未创建」「188 例」「D-005/D-007 等仍 PROPOSED」 | DB v1 已建；197 例；相关决策已 ACCEPTED | **文档失真**，需 MB-D-018 授权修正 |
| B-05 | A-G2-001 写 `PRAGMA synchronous=NORMAL` | 源码未设置 synchronous，回调只设 foreign_keys/busy_timeout | 文档与实现不一致；低风险但需补验证 |
| B-06 | G2 报告写「备份一致性已实现」的边界 | 未实现 checkpoint；`AutoBackupWorker` 直接返回 success | 见 S0-04 高风险 |

> 以上异常不影响“G2 代码基线可构建”这一结论，但会影响“G2 能力已完成”的表述。

---

## 7. 基线结论

| 项 | 结论 |
|---|---|
| 工作区是否可信 | **是** |
| 是否基于 G2 正式代码 | **是**（`705e0ca` 的业务代码等价于 `2b523de`） |
| 是否可独立构建 | **是**（本机 exit 0） |
| 是否可回档 | **是**（G2 标签完整，新增审查检查点） |
| 是否混入 G3-1 | **否** |
| 是否可开始 Stage 1 代码清理 | **否**：需 MB-D-001/002/003/005/018 签收（见 S0-05） |
| 是否可继续无行为变化的工程准备 | **可以**：如补测试、补文档、补验证脚本；不得改产品行为 |

**建议工作基线**：`705e0ca` + Stage 0 文档提交。

**注意**：MB-D-001 仍是 `PROPOSED`，本报告不把它写成 `ACCEPTED`。上述“工作基线”是事实状态，不是负责人签收结论。

---

## 8. 复现命令

```powershell
git -C D:\MISEBASE status --short --branch
git -C D:\MISEBASE log --oneline -n 4
git -C D:\MISEBASE cat-file -e 705e0ca^{commit}
git -C D:\MISEBASE cat-file -e 2b523de^{commit}
git -C D:\MISEBASE rev-parse --short framework-0.1.0^{}
git -C D:\MISEBASE diff --name-status 2b523de 705e0ca
git -C D:\MISEBASE tag -l

$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat test assembleDebug --console=plain --no-daemon
& "$env:ANDROID_HOME\build-tools\36.1.0\aapt2.exe" dump permissions app\build\outputs\apk\debug\app-debug.apk
```

---

## 9. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0.0 | 2026-11-04 | 完成 Git、交接资料、构建、测试、技术栈与差异核验 | 接管工程组 |
