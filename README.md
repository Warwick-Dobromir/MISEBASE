# CULINARY

**专业个人烹饪档案与研发管理系统 — Android 原生应用**

| 项目 | 内容 |
|---|---|
| 架构基线 | `0.4.1`（G1 已正式签收） |
| 当前阶段 | **G2 阶段 9（质量门禁）已完成**，待负责人验收（G2 收口） |
| 工程状态 | 15 模块；Room v1 31 表；食材 CRUD；算法验证；迁移/恢复演练；R8 构建；**185 例测试** |
| 数据库 | 尚未创建（阶段 3；D-005 主键策略待确认） |
| 构建基线 | AGP 8.13.0 / Kotlin 2.2.20 / Gradle 8.14.3 / JDK 21 / minSdk 26 / compile & target 36 |
| 依赖注入 | Hilt 2.57.2 + KSP（D-012 `ACCEPTED`，禁止 2.59+） |
| 离线承诺 | 无 `INTERNET` 权限（manifest 移除 + `aapt2` 已验证） |

## 快速开始

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"

.\gradlew.bat :app:assembleDebug
.\gradlew.bat ktlintCheck detekt
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-module-deps.ps1

adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.culinary/.MainActivity
```

Android Studio 首次打开需要 `local.properties`（不提交 Git）：

```properties
sdk.dir=C\:\\Users\\<user>\\AppData\\Local\\Android\\Sdk
```

依赖仓库为镜像优先、官方回退（R02 §6.3）；`build-logic` 额外需要 Gradle Plugin Portal
（ktlint-gradle 未同步到镜像）。

## 15 模块（D-004 已接受，不得增删改）

```text
app
core/
  common        Outcome、错误模型、Clock、IdGenerator、Dispatcher、日志门面
  domain        领域模型（model 包）、Repository 端口、Use Case、配方/单位规则
  database      Room 实体/DAO/迁移/Schema
  data          Repository 实现、Mapper、事务协调、文件与备份
  designsystem  主题、颜色、通用组件
  platform      SAF、权限、CameraX、WorkManager
feature/
  ingredients sauces recipes products inventory planning procurement settings
```

`:core` 与 `:feature` 是 Gradle 结构容器（无构建脚本），不计入 15 个模块。
模块依赖检查实测 **15 节点 / 43 条边**，与 G1 记录的 DAG 一致。

## 架构边界

- `feature` 之间零依赖；跨 feature 通过 `core:domain` 端口与 `app` 导航协作。
- `core:domain` 是纯 JVM 模块，不依赖 Android/Room/Compose/Hilt。
- `core:database` 不依赖 `core:domain`；`core:data` 不直接依赖 Android SAF/`Uri`。
- 不创建 `:core:model`；领域模型统一放 `core:domain` 的 `model` 包。

## 数据安全约定

- 已发布菜谱/酱汁版本不可修改；新编辑产生新版本。
- 计划快照不可修改、不可删除；只有**草稿**计划允许删除，已确认计划通过 `CANCELLED` 状态保留历史。
- 库存只能通过追加流水改变，`inventory_ledger` 与 `change_log` 禁止 UPDATE/DELETE。
- 备份/恢复遵守 E05；恢复失败不清理现场。

## 工程治理

- 构建逻辑集中在 `build-logic/convention` 的 6 个约定插件。
- 依赖版本只来自 `gradle/libs.versions.toml`；模块脚本不得硬编码版本。
- 质量门禁：`ktlintCheck`、`detekt`、`check-module-deps.ps1`。
- Git：每个阶段打 `checkpoint/*` 与 `baseline/*` 标签，见 [E04](docs/03-engineering/E04-GIT-ROLLBACK.md)。

## 当前状态与未决

- **G2 阶段 1** 已完成：15 模块、版本目录、约定插件、最小 Application/Activity。
- **G2 阶段 2** 已完成：`core:common` 错误/结果契约，`core:domain` 单位、食材、菜谱模型与端口，
  单位换算与配方图循环检测（24 个纯 JVM 测试）。
- **G2 阶段 3** 已完成：Room schema v1（**31 表**）、外键/WAL/busy timeout、15 条单位种子、部分唯一索引与只追加触发器（77 例数据库测试）。
- **G2 阶段 4** 已完成：Repository/Mapper、单写者事务协调、原子图片存储、E05 备份归档与恢复预检（34 例数据层测试）。
- **G2 阶段 5** 已完成（设备级验证延后到阶段 7）：WorkManager 调度、相机权限三态、SAF 适配、ML Kit 解码与手工兜底（19 例平台测试）。
- **G2 阶段 6** 已完成：Navigation Compose、启动门禁与 Recovery、崩溃日志、调试入口（设备实测）。
- **G2 阶段 7** 已完成：食材列表/新建/编辑/归档全链路，设备 E2E。
- **G2 阶段 8** 已完成：配方展开、需求汇总、缺口计算、快照 payload；200 节点 < 500 ms。
- **G2 阶段 9** 已完成：CI workflow、迁移测试、R8 Release、恢复演练、离线冒烟、基线标签。
- **仍为 PROPOSED**：D-005（主键）、D-007（计划快照）、D-013、D-015、D-020、D-021、D-025；
  其中 D-005 必须在 DB v1（阶段 3）前确认。
- **G5 发布门禁**：Release keystore 未创建，不影响框架阶段。

## 文档

- [G2 阶段 9 报告](docs/04-readiness/R19-G2-STAGE9-REPORT.md)
- [G2 阶段 8 报告](docs/04-readiness/R18-G2-STAGE8-REPORT.md)
- [G2 阶段 7 报告](docs/04-readiness/R17-G2-STAGE7-REPORT.md)
- [G2 阶段 6 报告](docs/04-readiness/R16-G2-STAGE6-REPORT.md)
- [G2 阶段 5 报告](docs/04-readiness/R15-G2-STAGE5-REPORT.md)
- [G2 阶段 4 报告](docs/04-readiness/R14-G2-STAGE4-REPORT.md)
- [G2 阶段 3 报告](docs/04-readiness/R13-G2-STAGE3-REPORT.md)
- [G2 阶段 2 报告](docs/04-readiness/R12-G2-STAGE2-REPORT.md)
- [G2 阶段 1 报告](docs/04-readiness/R11-G2-STAGE1-REPORT.md)
- [G2 开工授权记录](docs/04-readiness/R10-G2-START-AUTHORIZATION.md)
- [决策登记册](docs/00-governance/DECISION-REGISTER.md)（0.8.0）
- [文档索引](docs/00-governance/DOCUMENT-INDEX.md)
- [G1→G2 交接说明](docs/04-readiness/R09-G1-TO-G2-HANDOFF.md)

## 文档状态规则

| 状态 | 含义 | 是否可用于正式开发 |
|---|---|---|
| `Draft` | 草案，内容可能不完整 | 否 |
| `Review` | 已自检，等待负责人确认 | 仅可作为讨论依据 |
| `Approved` | 已由项目负责人明确签收 | 是 |
| `Deprecated` | 已废弃，保留追溯 | 否 |

任何影响模块边界、数据库结构、备份格式或锁定技术栈的修改，必须先走 ADR 与负责人签收。
