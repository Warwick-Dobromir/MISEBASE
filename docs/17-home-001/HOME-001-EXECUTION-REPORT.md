# HOME-001 首页改造与 APK 交付报告

- 任务：HOME-001 首页独立开发
- 日期：2026-11-08
- 工作目录：`D:\MISEBASE`
- 分支：`restore-v7`
- 交付状态：**已完成，等待验收**
- 范围：仅首页 UI、首页本地模拟数据/交互、首页二级入口占位与底部导航可见文案；未修改 Room 数据库结构，未引入新依赖。

## 1. 交付物

| 项目 | 值 |
|---|---|
| APK 路径 | `D:\MISEBASE\dist\HOME-001\MISEBASE-1.0.0-home001-20261108-release.apk` |
| APK 大小 | 24,455,540 字节 |
| SHA-256 | `8ba1ed2381891079203cc26d4ec71c3ac58ea5bada2b5bc6ce3ce3052fd66b45` |
| SHA-256 文件 | `D:\MISEBASE\dist\HOME-001\MISEBASE-1.0.0-home001-20261108-release.apk.sha256` |
| 包名 | `com.culinary` |
| versionName / versionCode | `1.0.0` / `1` |
| minSdk / targetSdk | 26 / 36 |
| 签名校验 | `apksigner verify` 通过，APK Signature Scheme v2 |
| 签名证书 SHA-256 | `5ca41959d9f450cdc851dcb9f052ac9130aaeab71c64062173a699355056af61` |

安装命令：

```powershell
adb install "D:\MISEBASE\dist\HOME-001\MISEBASE-1.0.0-home001-20261108-release.apk"
```

## 2. 编译与测试结果

### 2.1 构建

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleRelease
```

结果：`BUILD SUCCESSFUL`，Release APK 已生成。

### 2.2 单元测试

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

结果：**31 个测试，0 失败，0 错误**。

覆盖首页本地状态、计划完成/取消、计数/进度、短屏滚动、入口回调、导航五项、既有库页与崩溃日志模块。

### 2.3 静态检查

```powershell
.\gradlew.bat :app:ktlintCheck :app:detekt
```

结果：`BUILD SUCCESSFUL`。

## 3. 安装与启动验收

最终包已从 `dist/HOME-001/` 重新安装到 Android 模拟器：

- `adb install`：`Success`
- 包信息：`com.culinary`，versionCode 1，versionName 1.0.0，minSdk 26，targetSdk 36
- 冷启动：`am start -W` 返回 `Status: ok`，`LaunchState: COLD`，`TotalTime: 587ms`
- 前台 Activity：`com.culinary/.MainActivity`
- Logcat：无 `FATAL EXCEPTION`，无应用 ANR
- 首页截图：见 [01-home.png](evidence/01-home.png)

测试设备：

- Android 16 / API 36.1 x86_64 模拟器
- 显示覆盖为 `1260 × 2800 @ 453dpi`，用于近似 vivo S20 的屏幕尺寸与密度
- 当前环境未连接 vivo S20 真机；最终视觉签收建议在真机补做一次截图确认

## 4. 已完成功能

### 4.1 顶部软件标识区

- 左侧 `MISEBASE` 与 `Culinary Records and Inventory System`，小字号、低对比副标题。
- 右侧计时器图标与三点菜单。
- 顶部区域下方 1dp 横向细线。
- 计时器进入明确占位页；三点菜单进入现有设置页。

### 4.2 今日计划

- 标题 `今日计划` + `Today’s Preparation`。
- 右侧动态完成数 `00/04`，完成/取消后实时变化。
- 2dp 进度条，浅灰轨道、香槟金完成段，随完成比例变化。
- 默认四条计划，圆圈与文字严格对齐，整行可点击。
- 点击圆圈或文字完成，再次点击取消完成。
- 已完成项目使用次级文字颜色适度弱化，不过度灰暗。
- 底部左侧 `查看全部 →`、右侧香槟金 `＋ 添加计划`。

实机勾选证据：[02-plan-completed.png](evidence/02-plan-completed.png)

### 4.3 采购单

- 标题 `采购单` + `Purchase Records`。
- 右侧 `＋ 新建`，文字 + 小加号，不制作大型按钮。
- 三条本地模拟记录：名称、创建日期、创建时间、`待采购` 与右侧箭头。
- 每条记录使用 1dp 横线分隔，无卡片背景。
- 点击记录进入带采购单名称的详情占位页。
- 底部 `查看完整采购单 →` 与“以上为本地演示数据，未写入正式数据库。”说明。

### 4.4 底部导航

保留五项结构：`首页 / 知识库 / 扫码 / 库存 / 我的`。

- 首页当前选中，香槟金下划线。
- 第五项按任务书显示为“我的”，路由仍复用原有 `settings`，设置页内容与功能不变。
- 其余入口均可点击，已逐一验证知识库、库存、我的、扫码。
- 底栏使用 `navigationBarsPadding()`，不因首页内容增加而压缩图标。

### 4.5 滚动与屏幕适配

- 顶部字标区固定；今日计划与采购单主体独立纵向滚动。
- 采购单尾部、`查看完整采购单` 和演示数据说明均可滚动到达，不被底栏遮挡。
- 滚动证据：[03-home-scrolled.png](evidence/03-home-scrolled.png)
- 短屏自动化测试覆盖 `w411dp-h640dp`。

### 4.6 数据隔离

- 首页计划与采购单来自 `HomeMockData`，是纯内存 UI 状态。
- 不读取、不写入 Room；未修改任何 Entity、DAO、Database、Migration 或 schema。
- 计划勾选只修改 `HomeUiState` 中的列表副本，进程结束后恢复默认模拟值。
- 未新增第三方依赖。

## 5. 暂时占位项目

以下入口已接通且可点击，但目标页面按任务边界未提前开发，均显示“尚未在本次首页任务中开发”的明确占位：

| 入口 | 当前行为 |
|---|---|
| 顶部计时器 | 计时器占位页 |
| 查看全部 | 全部今日计划占位页 |
| ＋ 添加计划 | 添加计划占位页 |
| ＋ 新建 | 新建采购单占位页 |
| 查看完整采购单 | 完整采购单占位页 |
| 点击采购记录 | 对应采购单名称的详情占位页 |

已接通的真实页面：顶部三点菜单 → 设置页；底部知识库 / 扫码 / 库存 / 我的 → 原有功能页。

## 6. 修改文件清单

### 新增

- `app/src/main/java/com/culinary/home/HomePlaceholderSection.kt`
- `docs/17-home-001/HOME-001-EXECUTION-REPORT.md`
- `docs/17-home-001/evidence/*`
- `dist/HOME-001/MISEBASE-1.0.0-home001-20261108-release.apk`
- `dist/HOME-001/MISEBASE-1.0.0-home001-20261108-release.apk.sha256`

### 修改

- `app/src/main/java/com/culinary/home/HomeScreen.kt`：首页视觉与交互重写
- `app/src/main/java/com/culinary/home/HomeUiState.kt`：首页本地状态与模拟数据
- `app/src/main/java/com/culinary/home/HomeViewModel.kt`：计划完成/取消
- `app/src/main/java/com/culinary/navigation/CulinaryNavHost.kt`：首页入口与占位路由
- `app/src/main/java/com/culinary/navigation/CulinaryDestination.kt`：第五项可见文案/图标
- `app/src/main/res/values/strings.xml`：首页与占位页文案
- `core/designsystem/src/main/java/com/culinary/core/designsystem/icon/CulinaryIcons.kt`
- `core/designsystem/src/main/java/com/culinary/core/designsystem/icon/MaterialSymbolPaths.kt`
- `tools/icon_pipeline/convert_symbols.py`
- `app/src/test/java/com/culinary/home/HomeViewModelTest.kt`
- `app/src/testDebug/java/com/culinary/home/HomeScreenTest.kt`
- `app/src/testDebug/java/com/culinary/navigation/NavigationShellTest.kt`

### 删除

- `app/src/main/java/com/culinary/home/HomeSections.kt`：原首页旧区块实现，已由新首页替换，无其他引用。

## 7. 12 项验收检查

| 检查项 | 结果 |
|---|---|
| 1. 项目成功编译 | 通过 |
| 2. APK 成功生成 | 通过 |
| 3. APK 能正常安装 | 通过 |
| 4. 应用能正常启动 | 通过，冷启动 587ms |
| 5. 首页完整显示 | 通过 |
| 6. 页面可上下滚动 | 通过 |
| 7. 计划勾选正常 | 通过 |
| 8. 完成数量与进度条同步 | 通过，`00/04 → 01/04 → 00/04` |
| 9. 添加计划入口响应 | 通过，进入明确占位 |
| 10. 采购记录可点击 | 通过，进入名称匹配的占位详情 |
| 11. 底部导航正常 | 通过，五项均响应 |
| 12. 无明显错位/遮挡/崩溃 | 通过；滚动后底部入口不被遮挡，无 FATAL |

## 8. 已知边界

- 当前只完成首页；计划全量列表、计划新增、采购单全量/新建/详情、计时器均为占位，未伪装成已完成功能。
- 采购单为本地演示数据，不随 Room 数据变化；这是本阶段有意隔离，不是真实采购业务。
- 底部第五项按任务书显示“我的”，但底层仍是原设置路由与设置页，未新建“我的”模块。
- 未连接 vivo S20 真机，当前使用 1260×2800 / 453dpi 模拟器做尺寸近似验收；真机视觉签收仍需补做。

## 9. 证据文件

- [安装与冷启动记录](evidence/00-install-start.txt)
- [首屏](evidence/01-home.png)
- [完成一条计划](evidence/02-plan-completed.png)
- [滚动到采购单底部](evidence/03-home-scrolled.png)
- [首页 UI 层级](evidence/04-home-ui.xml)
- [计划完成 UI 层级（含 checked=true）](evidence/05-plan-completed-ui.xml)
- [滚动后 UI 层级（含完整采购单与底栏）](evidence/06-home-scrolled-ui.xml)
- [添加计划占位](evidence/07-add-plan-placeholder.xml)
- [计时器占位](evidence/08-timer-placeholder.xml)
- [设置入口](evidence/09-settings-entry.xml)
- [知识库入口](evidence/10-library-entry.xml)
- [采购详情占位](evidence/11-purchase-detail-placeholder.png)
- [完整采购单占位](evidence/13-full-purchases-placeholder.xml)
- [取消完成 UI 层级](evidence/12-plan-cancelled-ui.xml)
