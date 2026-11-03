# Hilt 多模块探针（D-012 补充证据）

**不是产品代码，不属于 `culinary` 正式工程。**

用途：为 D-012（Hilt 2.57.2 + KSP）提供可复现的工程证据，见
[`docs/04-readiness/D012-HILT-SUPPLEMENT.md`](../../docs/04-readiness/D012-HILT-SUPPLEMENT.md)。

## 结构

| 模块 | 类型 | 验证点 |
|---|---|---|
| `:app` | Android application | `@HiltAndroidApp`、`@AndroidEntryPoint` 字段注入、组合根 |
| `:core:domain` | 纯 Kotlin JVM | domain 不带 Hilt/Android 注解仍可被依赖 |
| `:core:di` | Android library | `@Module @InstallIn` 在 library 的 `di` 包绑定 |
| `:feature` | Android library | `@HiltViewModel` + `hiltViewModel()`（新坐标） |

## 结论（实测）

- 4 模块跨模块 Hilt 聚合 + KSP 注解处理：`BUILD SUCCESSFUL`；
- 模拟器安装冷启动成功，页面渲染两条注入路径：
  Activity 字段注入与 `@HiltViewModel` 构造注入；
- Hilt `2.59.2` + AGP `8.13.0` 会在插件应用阶段直接失败：
  `only compatible with Android Gradle plugin (AGP) version 9.0.0 or higher`；
- 配置缓存可存储/复用。

## 构建

需要本机 `Android SDK 36`、JBR 21；依赖走阿里云镜像。

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$gradle = "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.14.3-all\10utluxaxniiv4wxiphsi49nj\gradle-8.14.3\bin\gradle.bat"
& $gradle :app:assembleDebug --no-daemon --console=plain
```

## 清理

取证完成后可删除整个目录；不得把其中代码复制进正式工程。
