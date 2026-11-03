# G2 阶段 1（工程骨架）完成报告

**文档编号**：R11
**版本**：`1.0.0`
**状态**：`Review`（待项目负责人验收）
**日期**：2026-11-04
**实施依据**：[R01 0.2.2](../04-readiness/R01-FRAMEWORK-SEQUENCE.md) §4、D-004、D-012、[R10 授权记录](R10-G2-START-AUTHORIZATION.md)
**回档点**：`checkpoint/g2-d012-accepted-20261104`

---

## 1. 任务目标

按 R01 0.2.2 阶段 1 创建 **D-004 已接受的 15 模块**真实 Android 工程：

1. 15 个 Gradle 模块真实存在，依赖方向符合 A03A；
2. `build-logic/convention` 提供 6 个约定插件；
3. `gradle/libs.versions.toml` 为唯一版本源；
4. 最小 Application/Activity 可安装、可启动；
5. `projects`、`assembleDebug`、`ktlintCheck`、`detekt` 全绿。

**不在本阶段**：领域模型、Room、Repository、平台能力、导航、业务 UI（阶段 2 起）。

## 2. 工程结构

```text
culinary/
├─ app/                       # 组合根：CulinaryApplication、MainActivity
├─ core/
│  ├─ common/                 # 纯 JVM
│  ├─ domain/                 # 纯 JVM（api common）
│  ├─ database/               # Android library + Room 约定
│  ├─ data/                   # Android library + Hilt
│  ├─ designsystem/           # Android library + Compose（CulinaryTheme）
│  └─ platform/               # Android library + Hilt
├─ feature/                   # 8 个：Android library + Compose + Hilt
├─ build-logic/convention/    # 6 个约定插件 + 2 个辅助文件
├─ config/detekt/detekt.yml
├─ gradle/libs.versions.toml  # 唯一版本源
├─ gradle/wrapper/            # Gradle 8.14.3 + SHA-256 校验
├─ scripts/check-module-deps.ps1
├─ gradlew / gradlew.bat
├─ settings.gradle.kts
└─ build.gradle.kts
```

`:core` 与 `:feature` 是 Gradle 的结构容器项目（无构建脚本），不计入 15 个模块。

## 3. 实施内容

### 3.1 版本目录（唯一版本源）

| 类别 | 锁定值 |
|---|---|
| AGP / Gradle / JDK | 8.13.0 / 8.14.3 / JBR 21.0.8（编译目标 JVM 17） |
| Kotlin / KSP | 2.2.20 / 2.2.20-2.0.4 |
| Hilt / AndroidX Hilt | 2.57.2（禁止 2.59+）/ 1.3.0 |
| Compose BOM / Material3 | 2025.10.01 |
| Room | 2.8.5 |
| SDK | min 26 / compile & target 36 |
| 质量 | detekt 1.23.8 / ktlint-gradle 14.2.0 |
| 平台（阶段 5 预留） | CameraX 1.4.2、ML Kit 17.3.0、WorkManager 2.10.5、Coil 3.3.0 |

模块脚本中**没有**硬编码依赖版本（已用正则检查，0 命中）。

### 3.2 约定插件（6 个，R01 §4.1 全部落地）

| 插件 ID | 作用 |
|---|---|
| `culinary.android.application` | application + Kotlin Android；SDK/版本号/包名/JVM 17/打包排除 |
| `culinary.android.library` | library + Kotlin Android；namespace 由模块路径推导 |
| `culinary.android.compose` | Compose 编译器插件 + BOM/UI/Material3/tooling |
| `culinary.android.hilt` | KSP + Hilt 插件 + `hilt-android`/编译器 |
| `culinary.android.room` | KSP + Room 运行时/编译器 + schema 导出目录 |
| `culinary.jvm.library` | 纯 Kotlin/JVM；供 common/domain |

所有约定插件统一挂载 `ktlintCheck` 与 `detekt`（`CulinaryQuality.kt`），避免模块漏挂。

### 3.3 15 模块的插件落点

| 模块 | 约定插件 |
|---|---|
| `:app` | application + compose + hilt |
| `:core:common` | jvm |
| `:core:domain` | jvm |
| `:core:database` | library + room |
| `:core:data` | library + hilt |
| `:core:designsystem` | library + compose |
| `:core:platform` | library + hilt |
| `:feature:*`（8） | library + compose + hilt |

与 D-012 复核确认的落点一致（`common`/`domain`/`database`/`designsystem` 不应用 Hilt）。

### 3.4 最小应用

- `CulinaryApplication`：`@HiltAndroidApp`，无业务逻辑。
- `MainActivity`：`@AndroidEntryPoint` + 单 Activity，渲染 `CulinaryTheme` 下的
  “CULINARY framework”。
- `designsystem`：`CulinaryTheme` + Orange 强调色（浅/深色两套 `ColorScheme`）。
- `AndroidManifest`：`INTERNET` 以 `tools:node="remove"` 移除；`allowBackup=false`。

### 3.5 依赖规则检查

`scripts/check-module-deps.ps1` 从构建脚本解析 `project("...")`，对照 A03A 允许边集合，
并检查模块数量/集合/禁止 `:core:model`/禁止依赖 `:app`。

## 4. 测试要求与实际结果

| # | 验收项（R01 §4.2） | 命令 | 实际结果 |
|---|---|---|---|
| 1 | 15 模块 | `.\gradlew.bat projects` | **PASS**：6 core + 8 feature + app |
| 2 | 可构建 | `.\gradlew.bat :app:assembleDebug` | **PASS**：`BUILD SUCCESSFUL in 1m 36s`，328 tasks |
| 3 | 格式与静态分析 | `.\gradlew.bat ktlintCheck detekt` | **PASS**：99 tasks，`BUILD SUCCESSFUL in 27s` |
| 4 | 版本全部来自目录 | 正则扫描 15 个模块构建文件 | **PASS**：0 处硬编码版本 |
| 5 | 依赖符合 A03A | `check-module-deps.ps1` | **PASS**：15 模块 / **43 条边**，与 G1 记录一致 |
| 6 | 安装后能打开 | `adb install` + `am start -W` | **PASS**：COLD 启动，`mResumedActivity=com.culinary/.MainActivity`，无崩溃 |
| 7 | 界面渲染 | `uiautomator dump` | **PASS**：`CULINARY framework` 渲染 1 次 |
| 8 | 离线权限 | `aapt2 dump permissions` | **PASS**：仅 `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`，**无 INTERNET** |
| 9 | 配置缓存（R02 建议） | `--configuration-cache` 两次 | **PASS**：`entry stored` → `entry reused`（29s → 10s） |
| 10 | Android Lint | `.\gradlew.bat :app:lintDebug` | **PASS**：`0 errors, 16 warnings` |

Lint 的 16 条警告分类：`NewerVersionAvailable` 13 条（版本锁定决策的必然结果）、
`MissingApplicationIcon` 1 条、`DataExtractionRules` 1 条、`AndroidGradlePluginVersion` 1 条。
均为已知、非阻塞项，图标与备份规则在阶段 6/9 处理。

## 5. 失败与偏差记录

| 项 | 现象 | 处理 |
|---|---|---|
| ktlint 构件不可解析 | `Could not find org.jlleitschuh.gradle:ktlint-gradle:14.2.0`（镜像/public/central 均无） | `build-logic` 增加 `https://plugins.gradle.org/m2`；已注释说明原因。**只影响构建逻辑仓库，不改变依赖版本校验** |
| Compose 插件无法解析 | 预编译脚本内 `id("org.jetbrains.kotlin.plugin.compose")` 无版本 | 将 `compose-compiler-gradle-plugin` 加入约定插件 classpath |
| build-logic JVM 目标不一致 | `compileJava(17)` vs `compileKotlin(21)` 警告 | 为 `build-logic` 显式设置 `jvmTarget=17` |
| PowerShell 脚本执行策略 | 本机禁止运行未签名脚本 | 文档与命令统一使用 `-ExecutionPolicy Bypass`；脚本保存为 UTF-8 **带 BOM**（Windows PowerShell 5.1 需要） |
| ktlint 首次失败 | `FunctionNaming`（Composable）与 `multiline-expression-wrapping` | `.editorconfig` 增加 `ktlint_function_naming_ignore_when_annotated_with = Composable`；调整主题代码换行 |

## 6. 遗留与后续

1. **无单元测试**：阶段 1 不写业务逻辑，测试从阶段 2 开始；`testDebugUnitTest` 目前为空。
2. **无 CI**：`.github/workflows` 与完整质量门禁在阶段 9；本阶段只保证本地命令可重复。
3. **占位类型**：13 个 `<Module>Module` 占位对象将在阶段 2-7 被真实契约替换；
   这是 R01 §4.1 明确要求的骨架占位，不是业务代码。
4. **Lint 警告**：见第 4 节，不阻塞阶段 1。
5. **`:core` / `:feature` 容器项目**：Gradle 结构产物，避免误认为第 16/17 个模块。
6. **未验证**：真机、Release/R8、导航、数据库（均属后续阶段）。

## 7. Git 基线

| 项 | 值 |
|---|---|
| 开工基线 | `checkpoint/g2-d012-accepted-20261104` |
| 阶段 1 提交 | `feat(build): create 15-module Android skeleton`（见 `git log`） |
| 阶段基线标签 | `baseline/g2-stage1-20261104` |

## 8. 结论

R01 §4.2 的 6 项退出条件**全部满足**，且附带了依赖边、权限、配置缓存的额外观测。
工程骨架真实存在、可构建、可安装、可启动、可检查；未提前进入任何后续阶段。

**请项目负责人验收阶段 1。** 验收通过后，下一阶段为 R01 阶段 2（核心契约），
其中不会触碰 D-005（主键）——它仍须在阶段 3（DB v1）前确认。

## 9. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0.0 | 2026-11-04 | 记录阶段 1 实施、实测结果、偏差与遗留 | 工程组 |
