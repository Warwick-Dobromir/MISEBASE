# MB-S1-07 UI 与业务隔离基础

**文档编号**：MB-S1-07
**版本**：`0.1.0`
**状态**：`Review`（架构约束；**未开发功能页面**）
**日期**：2026-11-04
**依据**：MB-S1-001 任务书 §4.8、MB-D-003、A03A §4/§8、S0-R-03/S0-R-13
**边界**：不开发酱汁/食材/菜谱/库存/扫码页面；不做视觉定稿；不重排导航；不新建 `feature:scan`。

---

## 1. 分层职责

| 层 | 位置 | 可以做什么 | 不可以做什么 |
|---|---|---|---|
| Compose UI | `feature/*/presentation/**`（或现有 `*Screen.kt`） | 渲染 `UiState`、发出用户事件、`collectAsStateWithLifecycle` | 直接操作 DAO/SQLite、直接读写 `core:data` 实现、做库存计算/单位换算/配方展开/缺口计算 |
| ViewModel | 同 feature | 协调状态、调用 UseCase、把 `Outcome` 映射为 `UiState`、处理一次性事件 | 持有 `Context`/`Cursor`/DAO、复制 Domain 规则、直接拼 SQL |
| Domain | `core:domain` | 领域模型、端口、UseCase、单位/配方/库存规则 | 依赖 Android/Room/Compose/Hilt/data/database/platform |
| Data | `core:data` | Repository 实现、Mapper、事务、文件/备份 | 依赖 feature/app、把 `Uri`/`Intent` 泄漏到领域接口 |
| Database | `core:database` | Entity/DAO/Database/Migration/Schema | 依赖 domain/data/feature/Compose |
| 组合根 | `app` | 导航、Hilt 装配、启动/恢复编排 | 写业务规则、直接写 DAO |

## 2. 强制规则

| ID | 规则 | 检查方式 |
|---|---|---|
| UI-01 | Compose 页面不得 `import` `androidx.room.*`、`android.database.sqlite.*`、`com.culinary.core.database.*`、`com.culinary.core.data.*` | `check-module-deps.ps1` 的 `V-UI-DAO` 规则（扫描 feature 中同时 import Compose 的文件） |
| UI-02 | feature 不得依赖 `core:data`/`core:database` 的实现 | `V-FEATURE-IMPL`；Gradle 声明边白名单 |
| UI-03 | feature 之间不得直接依赖 UI/ViewModel | `V-FEATURE-FEATURE` |
| UI-04 | 库存计算、单位换算、配方展开、缺口计算只能在 Domain；UI 只展示结果 | 代码评审 + Domain 纯 JVM 测试 |
| UI-05 | 一个页面一个 `Screen + ViewModel + UiState`；不得把所有页面塞进一个全局文件 | 代码评审；目录规范 |
| UI-06 | ViewModel 只调用 UseCase/端口，不绕过 Domain 调 Repository 实现 | 代码评审；import 扫描 |
| UI-07 | UI 状态不可变；`UiState` 使用 `data class` + `StateFlow` | 代码评审 |
| UI-08 | 不做视觉定稿、不做导航重排、不新增扫码 UI（MB-D-008/009/004 未签收） | 变更范围检查 |

## 3. 现有代码状态（2026-11-04）

| 模块 | 现状 | 判定 |
|---|---|---|
| `feature:ingredients` | `IngredientsScreen`（Compose）+ `IngredientsViewModel` + `IngredientsUiState`；只 import `core.domain`、`core.common`、Compose/Lifecycle/Hilt | **合规**；目录仍是平铺，后续按 A03A §4.3 拆 `presentation/` |
| `feature:sauces` / `recipes` / `products` / `inventory` / `settings` | 仅 8 行占位对象，无页面 | 未开发，符合 Stage 1 边界 |
| `app` | `HomeScreen` + `PlaceholderScreen` + 导航；`app` 可直接依赖 `core:data`（组合根） | 允许；不写业务规则 |
| `core:domain` | 纯 JVM；无 Android/Room/Compose/Hilt import | **合规** |
| `core:data` | 实现 Repository/事务/备份；无 feature/app import | **合规** |
| `core:database` | Entity/DAO/Schema；无 domain/data import | **合规** |

依赖检查实测：13 模块 / 35 条声明边 / 155 条源码 import 边 / 0 违规
（见 [MB-S1-03](MB-S1-03-DEPENDENCY-CHECK.md)）。

## 4. 页面实现模板（后续阶段使用，不是本阶段代码）

```text
feature/<name>/src/main/java/com/culinary/feature/<name>/
  presentation/
    list/
      <Name>ListScreen.kt      // 只渲染 UiState + 事件
      <Name>ListViewModel.kt   // 调 UseCase，产出 StateFlow<UiState>
      <Name>ListUiState.kt     // data class，不可变
    detail/
    editor/
    components/                // 仅本 feature 复用
    navigation/                // 路由/入口
  di/                          // 如需要
```

示例（示意，不新增文件）：

```kotlin
// ViewModel 只依赖 Domain UseCase
@HiltViewModel
class SauceListViewModel @Inject constructor(
    private val listSauces: ListSaucesUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(SauceListUiState())
    val state: StateFlow<SauceListUiState> = _state.asStateFlow()
}

// Screen 只读 state，不碰 DAO/SQLite
@Composable
fun SauceListRoute(viewModel: SauceListViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SauceListScreen(state = state, onOpen = { /* 事件 */ })
}
```

## 5. 评审清单

- [ ] 页面是否只依赖 `core:domain`/`core:designsystem`/`core:common`？
- [ ] 是否出现 `Room`、`SQLiteDatabase`、`Cursor`、`DAO`、`core.data` 的 import？
- [ ] 库存/单位/配方规则是否在 Domain，并有纯 JVM 测试？
- [ ] ViewModel 是否只调 UseCase，状态是否不可变？
- [ ] 是否新增了未授权的页面/扫码/视觉/导航改动？
- [ ] 新增文件是否在允许的 feature 目录内？

## 6. 本阶段未做

- 未新增任何功能页面；
- 未新增 `feature:scan`；
- 未实现扫码 UI；
- 未做视觉定稿、主题大改、导航重排；
- 未修改 `core:designsystem` 的主题/组件（仅保留现有能力）；
- 导航仅删除 `planning`/`procurement` 两个已删除模块的占位入口，其余入口顺序不变。

## 7. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 0.1.0 | 2026-11-04 | 建立 UI/业务隔离规则、现状判定、检查方式与评审清单；未开发页面 | 接管工程组 |
