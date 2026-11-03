# D-012 补充材料：Hilt 2.57.2 + KSP 兼容性与工程可行性证据

**文档编号**：D012-SUP-01
**版本**：`0.1.0`
**状态**：`Review`（待项目负责人复核；**D-012 仍为 PROPOSED**）
**日期**：2026-11-04
**关联**：D-012、ADR-008、A01、A03A、R01、R02、V01
**证据工程**：`tools/hilt-multimodule-spike/`（探针，**不是产品代码**）

---

## 1. 本材料的边界

1. 本材料**不改变** D-012 的状态。D-012 仍是 `PROPOSED`，只有项目负责人书面接受后才可改为 `ACCEPTED`。
2. 本材料不创建正式工程，不进入阶段 1；仅回答"D-012 所定方案在本机、在当前版本组合下是否真的可行"。
3. 所有结论都附可复现命令与实测输出；未实测的内容明确标注为"未验证"。
4. 探针工程只用于取证，验证后可删除或保留为只读证据；其代码不得复制进正式工程（V01 §8 同规则）。

## 2. 待确认的决策原文

> **D-012**：依赖注入采用 Hilt `2.57.2` + KSP；`@HiltAndroidApp` 仅在 `:app`；绑定放 `core:data` / `core:platform` 的 `di` 包；`core:domain` 不出现 Hilt 注解；使用 Hilt Navigation Compose `1.3.0`；**Hilt 2.59+ 要求 AGP 9，禁止升级**。

## 3. 证据总览

| # | 证据 | 结果 | 取证方式 |
|---|---|---|---|
| E1 | 4 模块（app / core:di / core:domain / feature）Hilt+KSP 跨模块聚合构建 | **PASS** | 真实 Gradle 构建 |
| E2 | 探针 APK 模拟器安装、冷启动、运行期注入 | **PASS** | `adb install` + `am start -W` + `uiautomator` |
| E3 | 依赖坐标与仓库路由（Hilt 在 Maven Central，AndroidX Hilt 在 Google） | **PASS** | 依赖树 + POM 探测 |
| E4 | Hilt 2.59.2 + AGP 8.13.0 不兼容边界复现 | **已复现** | 版本临时切换构建，失败后已还原 |
| E5 | `hiltViewModel()` 1.3.0 坐标迁移 | **PASS** | 新坐标构建无弃用警告 |
| E6 | Gradle 配置缓存与 Hilt/KSP 共存 | **PASS** | `--configuration-cache` 两次构建 |
| E7 | 与 Room KSP 同模块共存 | **PASS**（复用 V01 证据 + 本次复验） | `dependency-spike` 构建 |

环境：Windows 11 `10.0.26300`、JBR `21.0.8`、Gradle `8.14.3`、AGP `8.13.0`、Kotlin `2.2.20`、KSP `2.2.20-2.0.4`、JDK target 17。

## 4. E1：多模块 Hilt/KSP 聚合构建

探针结构（对齐正式工程的分层方式）：

```text
app            (com.android.application, Compose, Hilt, KSP)
core:domain    (org.jetbrains.kotlin.jvm, 纯 JVM, 无 Hilt/Android)
core:di        (com.android.library, Hilt 绑定模块, KSP)
feature        (com.android.library, Compose, @HiltViewModel, Hilt, KSP)
```

- `core:domain` 只有纯 Kotlin 接口 `Greeter`，不带任何 Hilt/Android 注解 → 证明"domain 纯度"在构建层面可行。
- `core:di` 用 `@Module @InstallIn(SingletonComponent::class)` 绑定接口实现 → 验证绑定放在 library 模块。
- `feature` 用 `@HiltViewModel` + `@Inject constructor(Greeter)` → 验证 feature 模块的 ViewModel 注入。
- `app` 用 `@HiltAndroidApp` + `@AndroidEntryPoint` 字段注入 → 验证根组件与聚合。

实测输出（节选）：

```text
> Task :core:domain:compileKotlin
> Task :core:di:kspDebugKotlin
> Task :feature:kspDebugKotlin
> Task :app:kspDebugKotlin
> Task :app:hiltAggregateDepsDebug
> Task :app:hiltJavaCompileDebug
> Task :core:di:transformDebugClassesWithAsm
> Task :feature:transformDebugClassesWithAsm
> Task :app:packageDebug
> Task :app:assembleDebug

BUILD SUCCESSFUL in 1m 11s
92 actionable tasks: 79 executed, 13 from cache
```

结论：**跨 4 个 Gradle 模块的 Hilt 组件聚合、KSP 分模块注解处理、纯 JVM domain 模块共存，均可构建。** 这是 15 模块骨架最关键的未知项。

边界说明：本探针是 4 模块，不是完整 15 模块；15 模块的编译时间与聚合规模要到阶段 1 才能测量。

## 5. E2：运行期验证（不只是"能编译"）

```text
Performing Streamed Install
Success
Starting: Intent { cmp=com.culinary.probe/.MainActivity }
Status: ok
LaunchState: COLD
Activity: com.culinary.probe/.MainActivity
TotalTime: 1860

mResumedActivity: ActivityRecord{... com.culinary.probe/.MainActivity t24}
pidof com.culinary.probe -> 4605
crash buffer: 无 culinary / FATAL 记录
```

界面文本由两条不同注入路径产生，`uiautomator` 抓到 **2 次** `CULINARY Hilt probe OK`：

1. Activity 字段注入（`@AndroidEntryPoint` + `@Inject lateinit var greeter`）；
2. `@HiltViewModel` 构造注入（`hiltViewModel()` 创建）。

结论：**Hilt 图不是"编译期通过、运行期崩溃"**；Activity 组件与 ViewModel 组件都能解析绑定。

## 6. E3：依赖坐标与仓库路由

`gradle :app:dependencies --configuration debugRuntimeClasspath` 解析结果（节选）：

```text
+--- com.google.dagger:hilt-android:2.57.2
|    +--- com.google.dagger:dagger:2.57.2
|    \--- com.google.dagger:hilt-core:2.57.2
+--- androidx.hilt:hilt-lifecycle-viewmodel-compose:1.3.0
     +--- androidx.hilt:hilt-lifecycle-viewmodel:1.3.0
     \--- com.google.dagger:hilt-android:2.54 -> 2.57.2
+--- androidx.hilt:hilt-navigation-compose:1.3.0
```

要点：

- `androidx.hilt:*:1.3.0` 传递声明 Dagger `2.54`，被我们的直接依赖**升级并收敛到 2.57.2**，无重复类冲突。
- **仓库路由不同**：`hilt-android` 的 POM 只在 Maven Central（阿里云 `public` 镜像 200），在 Google Maven 镜像上是 404；`androidx.hilt:*` 在 Google Maven 镜像上 200。因此 `settings.gradle.kts` 必须**同时**配置 Google 与 Maven Central（含镜像），只配 Google 会解析不到 Hilt。
- 版本存在性已核验：`hilt-android` `2.57.2`、`2.59.2` 均在 Maven Central 镜像可用。

## 7. E4：不兼容边界的复现

把探针 4 个构建文件的 Hilt 版本临时改为 `2.59.2` 后构建，失败原文：

```text
* What went wrong:
An exception occurred applying plugin request [id: 'com.google.dagger.hilt.android']
> Failed to apply plugin 'com.google.dagger.hilt.android'.
   > The Hilt Android Gradle plugin is only compatible with Android Gradle plugin (AGP) version 9.0.0 or higher (found
Android Gradle Plugin version 8.13.0).
```

之后已把 4 个文件**全部还原为 2.57.2**，并重新构建成功（E6 的两次构建即为还原后的结果）。

结论：V01 §5.1 的结论在本机、本次环境可复现。**2.59+ 与 AGP 8.13 不是"可能有问题"，而是插件启动即拒绝。**

## 8. E5：`hiltViewModel()` 在 1.3.0 的坐标迁移

首次构建出现弃用警告：

```text
'fun <reified VM : ViewModel> hiltViewModel(...)' is deprecated.
Moved to package: androidx.hilt.lifecycle.viewmodel.compose.
```

改用 `androidx.hilt:hilt-lifecycle-viewmodel-compose:1.3.0` 与
`androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel` 后，构建成功且**无弃用警告**。

对正式工程的影响：

- 阶段 1 骨架只需要 `hilt-android` + `hilt-compiler`（KSP），不必提前引入 ViewModel 相关构件；
- 到阶段 4/6 首次出现 `@HiltViewModel` 时，按新坐标引入，避免一开始就写弃用 API；
- 若后续需要导航作用域的 ViewModel（`NavBackStackEntry`），再评估 `hilt-navigation-compose` 1.3.0 的必要性，不预先堆依赖。

## 9. E6：配置缓存与 Hilt/KSP 共存

```text
第一次：BUILD SUCCESSFUL in 14s / Configuration cache entry stored.
第二次：BUILD SUCCESSFUL in 8s  / Configuration cache entry reused.
```

结论：R02 建议的 `org.gradle.configuration-cache=true` 在当前 Hilt 2.57.2 + KSP 组合下可用，阶段 1 可以开启；若某模块引入不兼容插件，再按 R02 §2.5 单独排查，不全局禁用。

## 10. E7：与 Room KSP 同模块共存

`tools/dependency-spike` 的 `:app` 同时应用 `room-compiler` 与 `hilt-compiler`（均经 KSP），本次复验构建：

```text
> Task :app:kspDebugKotlin FROM-CACHE
> Task :app:hiltJavaCompileDebug FROM-CACHE
BUILD SUCCESSFUL in 17s
```

结论：Room 与 Hilt 共用同一 KSP 版本时无冲突。正式工程中二者分别落在 `core:database`（Room）与 `core:data`/`core:platform`/feature（Hilt），互不干扰。

## 11. 对 15 模块骨架的插件落点建议

| 模块 | AGP | Kotlin | Compose | Hilt 插件 | KSP | 说明 |
|---|---|---|---|---|---|---|
| `:app` | application | android | 是 | **是** | Hilt | `@HiltAndroidApp`、导航、组合根 |
| `:core:common` | 无（JVM） | jvm | 否 | 否 | 否 | 纯 Kotlin；Timber 只是实现细节 |
| `:core:domain` | 无（JVM） | jvm | 否 | 否 | 否 | 模型/端口/用例/规则，保持纯净 |
| `:core:database` | library | android | 否 | 否 | **Room** | Room 实体/DAO/迁移 |
| `:core:data` | library | android | 否 | **是** | Hilt | Repository 实现、事务、备份 |
| `:core:designsystem` | library | android | 是 | 否 | 否 | 主题/组件 |
| `:core:platform` | library | android | 否 | **是** | Hilt | SAF/相机/WorkManager 实现 |
| `:feature:*`（8 个） | library | android | 是 | **是** | Hilt | `@HiltViewModel` |

该落点只是**建议**，需随 D-012 一并确认；它不改变 D-004 的 15 模块与依赖方向。

## 12. 替代方案对比（为什么不是 Koin / 手写 DI）

| 方案 | 编译期图校验 | 多模块聚合 | 运行期失败风险 | 与 AGP 耦合 | 结论 |
|---|---|---|---|---|---|
| **Hilt 2.57.2 + KSP** | 有（`hiltJavaCompileDebug`） | 有（`hiltAggregateDepsDebug`） | 低 | 是（2.59+ 需 AGP 9） | 推荐 |
| Koin | 无 | 手工 | 高：15 模块下缺绑定只在运行时暴露 | 否 | 不采用 |
| 手写 DI | 无 | 手写 Composition Root | 中：15 模块装配量大人为出错 | 否 | 不采用 |
| 纯 Dagger（不用 Hilt） | 有 | 有 | 低 | 否 | 样板代码显著增加，收益不足 |

说明：本表是工程判断，不替代负责人决策；替代方案的实测对比未做（未引入未批准依赖），仅记录否决理由。

## 13. 风险与约束

1. **Hilt 版本锁死**：2.57.2 是 AGP 8.13 的上限；升级 AGP 9 必须与 Hilt ≥ 2.59 成组升级，属于独立任务 + 全量回归，不得在功能开发中顺手升级。
2. **KSP 版本必须与 Kotlin 完全匹配**（`2.2.20-2.0.4`）；Kotlin 升级时 KSP 同步升级。
3. **仓库必须同时覆盖 Google 与 Maven Central**，否则 Hilt 解析失败（E3）。
4. **`hiltViewModel` 坐标已迁移**（E5），不要在新代码中使用弃用导入。
5. **构建时间**：4 模块探针首次全量约 1 分 11 秒；15 模块的实际耗时要到阶段 1 测量，不能拿本数据外推。
6. **未验证项**：15 模块规模下的聚合与增量构建、`@HiltWorker`（WorkManager 集成）、测试替换（`@TestInstallIn`）、R8/Release 构建。这些要到对应阶段验证，不作为 D-012 的当前阻塞。

## 14. 复现步骤

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$gradle = "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.14.3-all\10utluxaxniiv4wxiphsi49nj\gradle-8.14.3\bin\gradle.bat"

# E1 / E6
& $gradle :app:assembleDebug --no-daemon --console=plain
& $gradle :app:assembleDebug --configuration-cache --no-daemon --console=plain

# E3
& $gradle :app:dependencies --configuration debugRuntimeClasspath --no-daemon --console=plain

# E2（需已启动模拟器）
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
& "$env:ANDROID_HOME\platform-tools\adb.exe" shell am start -W -n com.culinary.probe/.MainActivity
& "$env:ANDROID_HOME\platform-tools\adb.exe" shell uiautomator dump /sdcard/window.xml
```

E4 的复现需要把 4 个构建文件中的 `2.57.2` 改为 `2.59.2` 后构建，**完成后必须还原**；本次复现已还原并二次构建验证。

## 15. 结论与待确认项

**结论：D-012 所定方案在"4 模块 + 模拟器运行期"范围内可行，且不兼容边界已被复现和锁定。** 没有发现需要在阶段 1 之前解决的技术阻塞。

请项目负责人确认以下三点（确认后 D-012 方可由 `PROPOSED` 改为 `ACCEPTED`）：

1. 接受 Hilt `2.57.2` + KSP `2.2.20-2.0.4`，并接受"2.59+ 禁止、AGP 9 时成组升级"的约束；
2. 接受 §11 的插件落点（app / core:data / core:platform / feature:* 应用 Hilt；common、domain、database、designsystem 不应用）；
3. 接受 `hiltViewModel` 使用 `androidx.hilt:hilt-lifecycle-viewmodel-compose:1.3.0` 新坐标，不再使用 `androidx.hilt.navigation.compose` 中的弃用版本。

## 16. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 0.1.0 | 2026-11-04 | 建立 D-012 补充证据：多模块聚合、运行期注入、依赖路由、不兼容边界、坐标迁移、配置缓存 | 工程组 |
