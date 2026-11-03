# CULINARY 框架搭建实施顺序

**文档编号**：R01
**版本**：`0.2.0`
**状态**：`Review`
**日期**：2026-11-04
**负责人**：首席架构师 / 技术负责人

---

## 1. 本文的边界

本文不是“完整产品开发计划”，而是**从零到可验证框架**的实施顺序。

框架阶段的目标：

- 工程能构建、能安装、能打开。
- 分层与模块边界真实存在，不是文档里的图。
- 数据库 schema v1 能创建、迁移框架可用、种子数据正确。
- 事务协调器、错误模型、日志、依赖注入、导航、主题可用。
- 至少一个纵向切片（食材）从 UI → Use Case → Repository → Room 打通。
- 配方图/单位换算这类高风险算法有纯 JVM 验证。
- 备份/恢复、扫码、SAF 有接口与最小实现，不要求完整 UI。

**不包含：**

- 完整产品 UI、视觉设计、动画、引导页。
- 所有业务模块的全部功能。
- 云同步、账号、AI。
- 性能优化到最终目标。
- 应用商店发布。

## 2. 阶段总览

| 阶段 | 名称 | 产出 | 退出条件 | 规模 |
|---|---|---|---|---|
| 0 | 环境门禁 | JDK/SDK/Gradle/网络/签名 | 能解析依赖并构建空工程 | S |
| 1 | 工程骨架 | D-004 审查后的模块集（推荐 15）、版本目录、约定插件 | `assembleDebug` 成功 | M |
| 2 | 核心契约 | common/domain（含模型）接口与错误 | 纯 JVM 单测通过 | M |
| 3 | 数据库 v1 | Room schema、DAO、迁移框架、种子 | 建库/约束/迁移测试通过 | L |
| 4 | 数据层 | Repository、Mapper、事务、文件 | 集成测试通过 | L |
| 5 | 平台能力 | 扫码/SAF/WorkManager 接口与最小实现 | 权限/结果映射测试 | M |
| 6 | 应用外壳 | Hilt、导航、主题、启动/恢复 | 可安装打开，无业务 | M |
| 7 | 纵向切片 | 食材 CRUD（最小 UI） | 端到端可用 | M |
| 8 | 算法验证 | 配方图/单位换算/汇总 | 纯 JVM 场景通过 | M |
| 9 | 质量门禁 | CI、lint、迁移、备份冒烟 | 全绿 + 基线标签 | M |

规模是相对量级，不是工期承诺。

## 3. 阶段 0：环境门禁

### 3.1 任务

1. 安装/确认 JDK 21、Android SDK 36、build-tools、platform-tools。
2. 配置 `JAVA_HOME`、`ANDROID_HOME`（或 `local.properties`）。
3. 解决 Google Maven 网络可达性（代理/镜像/证书）。
4. 准备签名 keystore（release）。
5. 确认模拟器 API 26/33/36 可用；连接一台真机（可后置）。
6. 初始化 Git 仓库与 `.gitignore`。

### 3.2 退出条件

- [ ] `java -version`、`adb version` 正常。
- [ ] Gradle 能下载/解析 AGP、Kotlin、Compose BOM、Room、Hilt。
- [ ] 空 Android 工程 `assembleDebug` 成功。
- [ ] `adb install` 成功（模拟器）。
- [ ] 网络/镜像方案写入 R02。

**未通过则不要进入阶段 1。** 依赖解析失败时继续写代码只会积累不可验证的假设。

## 4. 阶段 1：工程骨架

### 4.1 任务

- 按 D-004 审查结论创建模块（推荐 15；细粒度参考 16，见 A03/A03A），每个模块有最小 `build.gradle.kts`。
- `settings.gradle.kts` 集中 include；模块命名与路径一致。
- `build-logic/convention` 提供：
  - `culinary.android.application`
  - `culinary.android.library`
  - `culinary.android.compose`
  - `culinary.android.hilt`
  - `culinary.android.room`
  - `culinary.jvm.library`
- `gradle/libs.versions.toml` 锁定版本。
- 根 `build.gradle.kts` 只做插件声明/仓库配置。
- `.editorconfig`、ktlint、detekt 配置。
- `app` 最小 Application/Activity，显示“CULINARY framework”。
- 不创建空包；每个模块至少一个占位类/接口，说明职责。

### 4.2 退出条件

- [ ] `./gradlew projects` 显示 D-004 审查确定的模块集。
- [ ] `./gradlew assembleDebug` 成功。
- [ ] `./gradlew ktlintCheck detekt` 成功。
- [ ] 依赖版本全部来自 version catalog，无硬编码。
- [ ] 模块依赖符合 A03（无 feature 间依赖）。
- [ ] 安装后能打开空白页。

## 5. 阶段 2：核心契约

### 5.1 任务

- `core:common`：`Outcome`、`CulinaryError`、错误码、`Clock`、`IdGenerator`、`DispatcherProvider`、`AppLogger` 接口。
- 领域模型：核心实体/值对象（先覆盖食材、单位、配方、库存、计划的最小字段）。推荐方案放 `core:domain` 的 `model` 包；若 D-004 保留独立模块则放 `core:model`。
- `core:domain`：Repository 端口（先食材、单位、配方、库存、计划），Use Case 接口/类。
- 领域服务骨架：
  - `UnitConverter`
  - `RecipeGraphValidator`
  - `RecipeExploder`
  - `RequirementAggregator`
  - `InventoryAllocator`
- 所有公开 API 有 KDoc；错误有错误码。

### 5.2 退出条件

- [ ] `core:domain` 不依赖 Android/Room/Compose。
- [ ] 纯 JVM 单测可运行。
- [ ] `UnitConverter` 的“同维度/跨维度/COUNT”测试通过。
- [ ] `RecipeGraphValidator` 的自引用/多层循环测试通过。
- [ ] `Outcome` 与错误映射有测试。

> 这一阶段允许写“算法原型”，但必须放在正式包与正式测试中；一次性验证脚本放 `tools/spikes/`，不进入产品代码。

## 6. 阶段 3：数据库 v1

### 6.1 任务

- 按 A06 建 Room Entity/DAO/View/Converter。
- `@Database(version = 1, exportSchema = true)`。
- 开启外键、WAL、busy timeout；验证 PRAGMA。
- 种子单位数据（`INSERT OR IGNORE`）。
- 迁移框架：`MigrationRegistry`、`MigrationTestHelper`、schema 导出目录。
- 索引与唯一约束按 A06。
- 流水 append-only 触发器（或应用层等价保护 + 测试）。
- 基础 DAO 测试：CRUD、约束、事务回滚。

### 6.2 退出条件

- [ ] 新安装能建库，`user_version=1`。
- [ ] 外键、唯一、CHECK 生效。
- [ ] 流水不可 UPDATE/DELETE。
- [ ] 库存余额不能为负；预留 ≤ 在手。
- [ ] `MigrationTestHelper` 能创建 v1 并打开。
- [ ] schema JSON 已提交。

## 7. 阶段 4：数据层

### 7.1 任务

- Repository 实现 + Mapper。
- `TransactionRunner`（写锁、超时、重试、错误映射）。
- 文件/图片存储骨架（原子写、路径、哈希、缺失降级）。
- 备份/恢复骨架（manifest、ZIP、校验；先不做完整 UI）。
- Hilt 绑定。
- 集成测试：真 SQLite + 临时文件。

### 7.2 退出条件

- [ ] 食材 Repository 可保存/读取/归档。
- [ ] 事务回滚测试通过（中途异常无残留）。
- [ ] 并发写测试通过（20 协程）。
- [ ] 图片写入原子；缺失不阻塞读取。
- [ ] 备份往返测试通过（空库 + 有数据）。
- [ ] 数据库异常映射为错误码。

## 8. 阶段 5：平台能力

### 8.1 任务

- `BarcodeScanner` 接口 + CameraX/ML Kit 最小实现。
- 权限状态机；拒绝后手工输入。
- SAF 文件选择/保存；持久化目录权限。
- WorkManager 调度骨架（自动备份）。
- 设备能力结果映射为 `Outcome`。

### 8.2 退出条件

- [ ] 模拟器/真机能扫码（或用手工输入兜底）。
- [ ] 权限拒绝有可恢复路径。
- [ ] SAF 选择/写入成功。
- [ ] WorkManager 任务可入队/取消。
- [ ] 不申请 `INTERNET` 权限。

## 9. 阶段 6：应用外壳

### 9.1 任务

- `@HiltAndroidApp`、`MainActivity`、`CulinaryNavHost`。
- 主题、Scaffold、导航占位。
- 启动流程：日志 → DB 打开 → 版本检查 → 迁移守卫 → 主界面/恢复页。
- 全局错误边界/崩溃处理。
- Debug 菜单（DB 信息、日志、备份入口）。

### 9.2 退出条件

- [ ] 冷启动进入首页。
- [ ] 数据库版本过高进入兼容性提示。
- [ ] DB 损坏进入 Recovery，不自动清空。
- [ ] 迁移失败进入恢复流程。
- [ ] Hilt 图编译通过。
- [ ] 无业务规则写在 Activity/Application。

## 10. 阶段 7：纵向切片（食材）

### 10.1 任务

- 食材列表、详情、编辑（最小可用 UI）。
- ViewModel + Use Case + Repository + Room 全链路。
- 图片可选；无图不显示区域。
- 错误/空/加载状态。
- 单元/UI/集成测试。

### 10.2 退出条件

- [ ] 新建食材 → 列表可见 → 重启仍在。
- [ ] 名称重复有冲突提示。
- [ ] 无图时无空占位。
- [ ] 保存失败显示错误码与恢复动作。
- [ ] UI 不 import `core:database`/`core:data` 实现。
- [ ] 端到端测试通过。

## 11. 阶段 8：算法验证

### 11.1 任务

- 用真实领域模型实现并测试：
  - 配方图循环检测（含跨类型）。
  - 单位换算（含食材级密度）。
  - 配方展开（子配方产量换算）。
  - 需求汇总（可合并/不可合并）。
  - 缺口计算（商品映射/库存单位）。
- 这些先于完整 UI，避免“界面做完才发现算法不对”。

### 11.2 退出条件

- [ ] 12 个必测场景中的算法类场景通过。
- [ ] 200 节点展开 ≤ 500 ms（JVM 基准）。
- [ ] 错误路径有明确错误码。
- [ ] 结果可序列化为快照 payload。

## 12. 阶段 9：质量门禁与基线

### 12.1 任务

- CI：ktlint、detekt、lint、unit、db、migration、assemble。
- 依赖规则检查（feature 间、domain→Android、db→domain）。
- 备份/恢复冒烟。
- 离线冒烟。
- 打基线标签 `framework-0.1.0`。
- 更新文档中的实际版本号/命令。

### 12.2 退出条件

- [ ] CI 全绿。
- [ ] Release 构建成功（R8）。
- [ ] 迁移测试全绿。
- [ ] 备份恢复演练通过。
- [ ] 真机/模拟器安装通过。
- [ ] 基线标签与构建产物归档。
- [ ] 框架启动报告更新为“框架已就绪”。

## 13. 框架完成定义（DoD）

- D-004 审查确定的模块集可构建，依赖方向可检查。
- 数据库 v1 + 迁移框架 + 种子数据可用。
- 事务/错误/日志/DI/导航/主题可用。
- 食材纵向切片可用。
- 配方/单位算法有测试。
- 扫码/SAF/备份有最小实现与测试。
- 离线可用；无网络权限。
- CI 全绿；基线标签已打。
- 文档与实现一致。

**不要求**：完整所有 feature 的 UI、最终视觉、性能极致、应用商店发布。

## 14. 框架之后的功能顺序

| 顺序 | 功能 | 理由 |
|---:|---|---|
| 1 | 食材完善（别名/换算/图片） | 所有配方的基础 |
| 2 | 酱汁（版本/组成/发布） | 菜谱可引用；先验证版本机制 |
| 3 | 菜谱（版本/组成/循环/展开） | 核心知识资产 |
| 4 | 商品/条码/供应商 | 库存与采购的前提 |
| 5 | 库存（批次/位置/流水/预留） | 计划缺口的前提 |
| 6 | 制作计划（确认/快照/缺口） | 连接知识与执行 |
| 7 | 采购（需求/采购单/收货） | 闭环 |
| 8 | 备份/恢复/设置完整化 | 数据安全收尾 |
| 9 | 性能/无障碍/打磨 | 发布前 |

每完成一个模块，必须更新 E06 验收证据；不允许“全部做完再补测试”。

## 15. 框架阶段禁止事项

- 不先做漂亮 UI 再补数据层。
- 不把多个 feature 同时开工。
- 不为“通用表单引擎”提前抽象。
- 不引入未在 A01 锁定的框架。
- 不跳过迁移/备份测试。
- 不用假数据掩盖数据库未打通。
- 不把技术验证代码合并进产品代码（放 `tools/spikes/` 或删除）。

## 16. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 0.1.0 | 2026-11-04 | 初稿；10 阶段、退出条件、DoD 与后续顺序 | 架构组 |
| 0.2.0 | 2026-11-04 | 按 D-004 将“16 模块”改为审查后的模块集（推荐 15） | 架构组 |
