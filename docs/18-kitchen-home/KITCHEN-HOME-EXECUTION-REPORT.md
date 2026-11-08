# MISEBASE 厨房首页改造与 Debug APK 交付报告

- 日期：2026-11-08
- 工作目录：`D:\MISEBASE`
- 任务类型：现有页面增量改造
- 目标页面：厨房首页
- 交付状态：**已完成，等待验收**
- 构建类型：**Debug**
- 数据层变更：**无 Room schema / Entity / DAO / Migration 变更**

## 1. 交付物

| 项目 | 值 |
|---|---|
| APK 路径 | `D:\MISEBASE\dist\KITCHEN-HOME-001\MISEBASE-1.0.0-kitchen-home-20261108-debug.apk` |
| APK 大小 | 39,116,272 字节 |
| SHA-256 | `4d1ad79ee5973fb417c167b2bf4f751c2ca2f245d2d8bd258a28228c6a754f00` |
| SHA-256 文件 | `D:\MISEBASE\dist\KITCHEN-HOME-001\MISEBASE-1.0.0-kitchen-home-20261108-debug.apk.sha256` |
| 包名 | `com.culinary` |
| 构建版本 | `1.0.0`（versionCode `1`，Debug） |
| minSdk / targetSdk | 26 / 36 |
| 签名 | Debug 签名；`apksigner verify` 通过（APK Signature Scheme v2） |
| 安装说明 | 见 [交付 README](../../dist/KITCHEN-HOME-001/README.md) |

> 若设备上已有 Release 签名版本，需先卸载再安装本 Debug 包，否则会因签名不同拒绝覆盖。

## 2. 构建与自动化测试

### 构建

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

结果：`BUILD SUCCESSFUL`。

### 单元测试

- 测试套件：10 个
- 测试数量：**38**
- 失败：**0**
- 错误：**0**

新增覆盖：厨房首页渲染、六类入口回调、数量来源隔离、加载/失败状态、Flow 更新、五项等宽导航、原有导航与档案页回归。

### 静态检查

```powershell
.\gradlew.bat :app:ktlintCheck :app:detekt
```

结果：`BUILD SUCCESSFUL`。

## 3. 安装与启动

- 设备：Android 16 / API 36.1 x86_64 模拟器
- 主测试分辨率：1260 × 2800 @453dpi（近似 vivo S20）
- 补充测试：1080 × 1920 @480dpi（360dp 窄屏、640dp 矮屏）
- 安装：`adb install -r ...` → `Success`
- 冷启动：`Status: ok`，`LaunchState: COLD`，`TotalTime: 2581ms`
- 前台 Activity：`com.culinary/.MainActivity`
- 日志：无 FATAL EXCEPTION，无应用 ANR

原始记录：[00-install-start.txt](evidence/00-install-start.txt)

## 4. 已实现功能

### 4.1 顶部区域

- 左上角小号 `MISEBASE`。
- 下方 `厨房` 标题。
- 标题下方 `CULINARY` 英文副标题。
- 标题区域最右侧圆形问号入口，进入厨房模块说明页。
- 顶部区域下方 1dp 横向分隔线。

### 4.2 分类分组

第一组：

- 烹饪资料 / CULINARY REFERENCES
- 酱汁 / SAUCES
- 食材 / INGREDIENTS
- 菜谱 / RECIPES
- 烹饪技术 / COOKING TECHNIQUES

第二组：

- 工作标准 / WORKING STANDARDS
- 制作标准 / PRODUCTION STANDARDS
- 储存标准 / STORAGE STANDARDS

分类行统一采用左侧双行文字、右侧 `N 项 >` 的布局；数量与箭头整体垂直居中。行间使用内缩 1dp 细线，不覆盖文字。

### 4.3 真实数量与自动更新

新增 `KitchenCountsReader`，通过现有 Use Case 的 Flow 同时观察：

- 酱汁 `observeActive()`
- 食材 `observeActive()`
- 菜谱 `observeActive()`

数量不缓存、不写死；新增或归档记录后自动更新。数量来源按分类独立计算，不混合统计。

设备实测：

| 操作 | 酱汁 | 食材 | 菜谱 | 证据 |
|---|---:|---:|---:|---|
| 初始空库 | 0 | 0 | 0 | [02-kitchen-empty.png](evidence/02-kitchen-empty.png) |
| 新增酱汁后 | 1 | 0 | 0 | [04-kitchen-counts-after-add.png](evidence/04-kitchen-counts-after-add.png) |
| 新增酱汁 + 食材后 | 1 | 1 | 0 | 同一次验证的 UI 层级 |
| 归档食材后 | 1 | 0 | 0 | [05-kitchen-count-after-delete.png](evidence/05-kitchen-count-after-delete.png) |
| 再新增菜谱后 | 1 | 0 | 1 | [01-kitchen-home.png](evidence/01-kitchen-home.png) |

### 4.4 分类入口

| 分类 | 行为 |
|---|---|
| 酱汁 | 进入现有酱汁列表 |
| 食材 | 进入现有食材列表 |
| 菜谱 | 进入现有菜谱列表 |
| 烹饪技术 | 进入明确占位页 |
| 制作标准 | 进入明确占位页 |
| 储存标准 | 进入明确占位页 |
| 问号 | 进入厨房模块说明 |

酱汁/食材/菜谱复用已有 `烹饪资料` 列表页，底部“厨房”保持选中，并提供返回按钮。

### 4.5 底部导航

固定五项：

`首页 / 厨房 / 扫码 / 库存 / 我的`

- 五项等宽、等图标尺寸、同文字样式。
- 扫码不再使用独立凸起圆形按钮。
- 当前厨房显示普通选中下划线。
- `导航栏固定在页面底部；页面主体在顶栏下方独立滚动。`
- 五项切换均使用确定性根页导航，避免恢复子 Tab 状态导致错页。
- “我的”沿用现有设置页路由和内容，未新建业务模块。

### 4.6 滚动与适配

- 顶部字标/厨房标题固定。
- 分类列表可纵向滚动。
- 360dp 窄屏下分类文字、数量、箭头仍保持左/右对齐。
- 滚动到底部时底栏保持固定，无内容被底栏遮挡。

证据：[08-small-screen.png](evidence/08-small-screen.png)、[09-small-screen-scrolled.png](evidence/09-small-screen-scrolled.png)

## 5. 尚未实现或未验证项目

| 项目 | 状态 | 说明 |
|---|---|---|
| 烹饪技术列表 | **未实现** | 当前无数据表与列表页；数量 0 项，点击进入明确占位页 |
| 制作标准列表 | **未实现** | 当前无数据表与列表页；数量 0 项，点击进入明确占位页 |
| 储存标准列表 | **未实现** | 当前无数据表与列表页；数量 0 项，点击进入明确占位页 |
| “我的”独立页面 | **未新建** | 保留现有设置页作为“我的”入口内容 |
| vivo S20 真机验证 | **未执行** | 当前无真机连接，使用 1260×2800 @453dpi 模拟器近似；真机视觉签收需补做 |
| 菜谱删除后的数量变化 | **未单独设备实测** | 菜谱新增已实测；删除/归档后的自动更新由食材归档实测及同一 Flow 机制覆盖 |

说明：烹饪技术、制作标准、储存标准没有对应 schema，数量 0 是“当前没有数据源”的真实结果，不是从其他分类混算或伪造的记录。

## 6. 回归检查

| 检查项 | 结果 |
|---|---|
| 首页打开与原有内容 | 通过，[12-regression-home.png](evidence/12-regression-home.png) |
| 厨房首页打开 | 通过 |
| 六分类入口响应 | 通过 |
| 五项导航切换 | 通过 |
| 分类数量与真实数据一致 | 酱汁/食材/菜谱已实测 |
| 空数据 0 项 | 通过 |
| 新增/删除后数量更新 | 酱汁、食材、菜谱新增；食材归档已实测 |
| 页面滚动、底栏固定 | 通过 |
| 不同屏幕尺寸布局 | 1260×2800 与 1080×1920 通过 |
| 库存既有功能 | 通过，[11-regression-inventory.png](evidence/11-regression-inventory.png) |
| 扫码既有功能 | 通过，进入原有相机权限/扫码页，[10-regression-scan.png](evidence/10-regression-scan.png) |
| 构建与局部测试 | 通过 |

## 7. 修改文件清单

### 新增

- `core/data/src/main/java/com/culinary/core/data/kitchen/KitchenCountsReader.kt`
- `core/data/src/main/java/com/culinary/core/data/kitchen/DefaultKitchenCountsReader.kt`
- `core/data/src/main/java/com/culinary/core/data/di/KitchenModule.kt`
- `app/src/main/java/com/culinary/kitchen/KitchenCategory.kt`
- `app/src/main/java/com/culinary/kitchen/KitchenRoutes.kt`
- `app/src/main/java/com/culinary/kitchen/KitchenViewModel.kt`
- `app/src/main/java/com/culinary/kitchen/KitchenScreen.kt`
- `app/src/test/java/com/culinary/kitchen/KitchenViewModelTest.kt`
- `app/src/testDebug/java/com/culinary/kitchen/KitchenScreenTest.kt`
- `docs/18-kitchen-home/*`
- `dist/KITCHEN-HOME-001/*`

### 修改

- `app/src/main/java/com/culinary/navigation/CulinaryBottomBar.kt`：五项等规格，移除凸起扫码
- `app/src/main/java/com/culinary/navigation/CulinaryDestination.kt`：第二项改为“厨房”
- `app/src/main/java/com/culinary/navigation/CulinaryNavHost.kt`：厨房根页、说明/占位路由、确定性一级导航
- `app/src/main/java/com/culinary/library/LibraryScreen.kt`：复用为“烹饪资料”列表页
- `app/src/main/res/values/strings.xml`：厨房与占位文案
- `app/src/test/java/com/culinary/navigation/CulinaryDestinationTest.kt`
- `app/src/testDebug/java/com/culinary/navigation/NavigationShellTest.kt`

### 数据库

- 未修改数据库结构。
- 未新增 Entity、DAO、Migration。
- 未修改现有数据表。
- 未新增第三方依赖。

### 工作区说明

工作区中仍保留上一任务 HOME-001 的未提交改动（首页、图标、测试等）。本次厨房任务在此基础上增量开发，未回退或重写这些已有改动。

## 8. 验收证据

- [安装与冷启动记录](evidence/00-install-start.txt)
- [构建、测试与 APK 信息汇总](evidence/15-build-test-summary.txt)
- [最终厨房首页（酱汁 1 / 食材 0 / 菜谱 1）](evidence/01-kitchen-home.png)
- [空库全 0 状态](evidence/02-kitchen-empty.png)
- [酱汁列表](evidence/03-sauces-list.png)
- [新增后数量](evidence/04-kitchen-counts-after-add.png)
- [归档食材后数量](evidence/05-kitchen-count-after-delete.png)
- [厨房模块说明](evidence/06-kitchen-help.png)
- [烹饪技术占位](evidence/07-techniques-placeholder.png)
- [窄屏 360dp](evidence/08-small-screen.png)
- [窄屏滚动后底栏固定](evidence/09-small-screen-scrolled.png)
- [扫码回归](evidence/10-regression-scan.png)
- [库存回归](evidence/11-regression-inventory.png)
- [首页回归](evidence/12-regression-home.png)
