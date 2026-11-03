# CULINARY 代码规范与工程目录规范

**文档编号**：E01
**版本**：`0.2.0`
**状态**：`Review`
**日期**：2026-11-04
**负责人**：首席架构师 / 技术负责人
**适用范围**：正式框架搭建与后续全部开发

---

## 1. 总则

1. **可读性优先于技巧。** 代码首先写给未来的自己看。
2. **边界优先于复用。** 不为少量重复引入跨层抽象；不为“以后可能”预建空模块。
3. **显式优先于隐式。** 依赖、事务、错误、单位、时间都必须显式。
4. **小文件、小函数、小提交。** 让修改可审查、可回滚。
5. **规范可自动检查。** 能交给 lint/CI 的，不靠人记忆。

## 2. 工程根目录

> 下列树是**细粒度参考**（16 模块）。D-004 推荐 15 模块：`core:model` 并入 `core:domain`；酱汁/菜谱、商品/库存保持独立。最终按 D-004 审查结论创建。

```text
culinary/
├─ app/                         # 组合根：Application、Activity、导航、Hilt 入口
├─ core/
│  ├─ common/                   # Result/错误/日志/时钟/ID/调度器
│  ├─ model/                    # 纯 Kotlin 领域模型
│  ├─ domain/                   # Repository 端口、Use Case、领域规则
│  ├─ database/                 # Room Entity/DAO/Migration
│  ├─ data/                     # Repository 实现、Mapper、事务、文件、备份
│  ├─ designsystem/             # 主题与通用 Compose 组件
│  └─ platform/                 # 相机/扫码/权限/SAF/WorkManager
├─ feature/
│  ├─ ingredients/
│  ├─ sauces/
│  ├─ recipes/
│  ├─ products/
│  ├─ inventory/
│  ├─ planning/
│  ├─ procurement/
│  └─ settings/
├─ build-logic/
│  └─ convention/               # Gradle 约定插件
├─ gradle/
│  └─ libs.versions.toml        # 唯一版本源
├─ config/
│  ├─ detekt/
│  └─ ktlint/
├─ docs/                        # 架构/ADR/数据字典/运行手册
│  ├─ adr/
│  ├─ diagrams/
│  └─ ...
├─ scripts/                     # check/build/release/backup 辅助脚本
├─ tools/                       # 一次性工具（不参与运行时）
├─ .github/workflows/           # CI
├─ gradlew / gradlew.bat
├─ settings.gradle.kts
├─ build.gradle.kts
├─ gradle.properties
├─ .editorconfig
├─ .gitignore
├─ .gitattributes
└─ README.md
```

### 2.1 不预建空目录

- 不为想象中的功能创建空包/空模块。
- 目录在第一次放入文件时创建。
- 删除功能时同步删除空目录，避免“看起来有很多模块”。

### 2.2 `docs/` 与本文档集

- 当前工作区的架构文档在正式工程中复制/迁移到 `docs/`，作为版本化资产。
- 代码仓库中的 `docs/` 是唯一有效版本；聊天记录不算文档。
- 文档变更与代码变更同一个 PR，避免文档滞后。

## 3. 模块目录结构

### 3.1 通用模块

```text
core/domain/src/main/kotlin/com/culinary/core/domain/
├─ repository/                  # 端口接口
│  ├─ IngredientRepository.kt
│  ├─ RecipeRepository.kt
│  └─ ...
├─ usecase/
│  ├─ ingredient/
│  ├─ recipe/
│  ├─ planning/
│  ├─ inventory/
│  └─ procurement/
├─ rule/
│  ├─ recipe/                   # 图校验/展开
│  ├─ unit/                     # 换算
│  ├─ requirement/              # 汇总/缺口
│  └─ inventory/                # 分配/FEFO
└─ service/                     # 跨用例的应用服务（少量）
```

### 3.2 feature 模块

```text
feature/ingredients/src/main/kotlin/com/culinary/feature/ingredients/
├─ IngredientsRoute.kt          # 唯一对外入口
├─ navigation/
│  └─ IngredientsNavigation.kt
├─ list/
│  ├─ IngredientListScreen.kt
│  ├─ IngredientListViewModel.kt
│  └─ IngredientListUiState.kt
├─ detail/
├─ edit/
└─ component/                   # 本 feature 专用组件
```

- 一个页面一个包；Screen/ViewModel/UiState 同包，便于阅读。
- 通用组件放 `core:designsystem`；只有两个以上 feature 使用的才上移。
- feature 内不建 `data/`、`domain/` 子模块；业务在 core:domain，持久化在 core:data。

### 3.3 database 模块

```text
core/database/src/main/kotlin/com/culinary/core/database/
├─ CulinaryDatabase.kt
├─ entity/
├─ dao/
├─ relation/                    # @Relation 投影
├─ converter/
├─ view/                        # @DatabaseView
├─ migration/
└─ schema/                      # 导出的 schema JSON（build 产物/提交）
```

### 3.4 data 模块

```text
core/data/src/main/kotlin/com/culinary/core/data/
├─ repository/
├─ mapper/
├─ transaction/
├─ file/
├─ backup/
├─ import/
├─ di/
└─ diagnostic/
```

### 3.5 platform 模块

```text
core/platform/src/main/kotlin/com/culinary/core/platform/
├─ scanner/
├─ permission/
├─ storage/                     # SAF
├─ work/
├─ share/
├─ device/
└─ di/
```

## 4. 命名规范

### 4.1 Kotlin

| 对象 | 规则 | 示例 |
|---|---|---|
| 包 | 全小写，点分 | `com.culinary.core.domain.usecase.recipe` |
| 类/接口 | PascalCase | `RecipeGraphValidator` |
| 函数/属性 | camelCase | `validateFormula` |
| 常量 | UPPER_SNAKE_CASE | `MAX_RECIPE_DEPTH` |
| 枚举值 | UPPER_SNAKE_CASE | `PURCHASE_RECEIPT` |
| 测试类 | `<Class>Test` | `UnitConverterTest` |
| 测试方法 | 反引号句子 | ``fun `rejects cross-dimension conversion without density`()`` |
| Composable | PascalCase，动词/名词 | `IngredientListScreen` |
| Route | `<Feature>Route` | `IngredientListRoute` |
| ViewModel | `<Screen>ViewModel` | `IngredientListViewModel` |
| UiState | `<Screen>UiState` | `IngredientListUiState` |
| UseCase | `<Verb><Noun>UseCase` | `ConfirmProductionPlanUseCase` |
| Repository | `<Aggregate>Repository` | `InventoryRepository` |
| Impl | `<Interface>Impl` | `InventoryRepositoryImpl` |
| Room Entity | 单数名词 | `RecipeVersionEntity` |
| DAO | `<Entity>Dao` | `RecipeVersionDao` |

### 4.2 文件

- 一个文件一个主要公开类；文件名与类名一致。
- 允许同文件放紧密相关的私有辅助类型，但不超过 3 个。
- 文件名不使用 `Utils`、`Manager`、`Helper` 这类无信息词；用具体职责。
- 扩展函数放 `XxxExt.kt`，且只服务于一个明确场景。

### 4.3 数据库

| 对象 | 规则 |
|---|---|
| 表 | 单数 `snake_case`：`recipe_version` |
| 列 | `snake_case`：`normalized_name` |
| 主键 | `id` |
| 外键 | `<entity>_id` |
| 布尔 | `is_` / `allow_` |
| 时间 | `_at`；日期 `_date` |
| 金额 | `_minor` |
| 索引 | `idx_<table>_<cols>` |
| 唯一索引 | `ux_<table>_<cols>` |
| 外键约束 | `fk_<table>_<ref>` |
| 迁移 | `M001_002_AddProductPackage` |

- 不使用 SQL 保留字做列名（`order`、`group`、`index` 等）。
- 枚举值用 `UPPER_SNAKE_CASE`，存 TEXT。
- 不在表名加 `tbl_` 前缀。

### 4.4 资源

- 字符串：`feature_<screen>_<purpose>`，如 `ingredient_list_title`。
- 颜色/尺寸：`culinary_` 前缀。
- 图片：`img_<purpose>`。
- 不使用中文做资源键。
- 默认 `values/strings.xml` 为简体中文；英文放 `values-en/strings.xml`（O-27）。

## 5. 代码注释规范

### 5.1 何时写

- 公开 API：KDoc 说明用途、参数、返回值、错误。
- 非显然的业务规则：为什么这样做，引用需求/决策编号。
- 并发、事务、迁移、安全相关：必须写“为什么”，不只写“做什么”。
- 复杂 SQL：说明查询意图与索引依赖。

### 5.2 何时不写

- 不写 `// 设置 name` 这种复述代码的注释。
- 不用注释代替命名。
- 不保留大段注释掉的代码；用 Git 历史。
- 不写与代码可能不同步的“文档”。

### 5.3 TODO

```kotlin
// TODO(CUL-123, D-010): 预留策略待产品确认；当前默认 RESERVE_AVAILABLE
```

- 必须带 issue/决策编号与原因。
- 禁止无编号 TODO。
- 发布前扫描并清理或转正式任务。

## 6. 代码质量规则

### 6.1 强制规则

| 规则 | 说明 |
|---|---|
| 禁止 `!!` | 用 `requireNotNull`/`checkNotNull` 并给出上下文 |
| 禁止 `GlobalScope` | 用 `viewModelScope`/`applicationScope`/`coroutineScope` |
| 禁止生产代码 `runBlocking` | 测试可用；启动同步初始化需 ADR |
| 禁止空 catch | 至少记录并转换错误；`CancellationException` 必须重抛 |
| 禁止 `println` | 用日志门面 |
| 禁止硬编码 UI 文案 | 用 string resources |
| 禁止 UI import DB | 见 A02/A03 |
| 禁止 `+` 动态依赖版本 | 版本目录锁定 |
| 禁止 `fallbackToDestructiveMigration` | 数据安全 |
| 禁止在事务中做网络/图片解码 | 长事务风险 |

### 6.2 规模限制（软/硬）

| 对象 | 软限制 | 硬限制 |
|---|---:|---:|
| Kotlin 文件 | 400 行 | 600 行 |
| 类 | 400 行 | 600 行 |
| 函数 | 50 行 | 80 行 |
| Composable | 80 行 | 120 行 |
| 参数个数 | 5 | 7（超过用参数对象） |
| 嵌套深度 | 3 | 4 |

超限时优先拆分职责；不要用 `@Suppress` 绕过。

### 6.3 不可变性

- 领域模型、UiState 用 `val` + `data class`。
- 集合默认 `List`/`Set`，不暴露可变集合。
- 不返回 `null` 集合；空用 `emptyList()`。
- 可变状态只存在于 ViewModel/事务内部。

### 6.4 协程

- 注入 `DispatcherProvider`；测试替换。
- `suspend` 函数必须可取消；长循环检查 `ensureActive()`。
- 捕获异常时先重抛 `CancellationException`。
- 并发写不直接 `async` 多个事务；走 `TransactionRunner`。
- Flow 用 `flowOn` 指定上游调度器；不阻塞收集者。

### 6.5 Compose

- 状态上提；Screen 接收 `UiState` + 事件回调。
- 不在 composition 中做 I/O 或创建数据库连接。
- 列表使用稳定 key。
- 副作用用 `LaunchedEffect`/`DisposableEffect`，并说明依赖。
- 预览不依赖真实 Repository。
- 图片区域按 `images.isEmpty()` 条件渲染，禁止空占位。

## 7. 模块接口规范

1. **最小暴露。** 模块默认 `internal`；只有跨模块需要的才 `public`。
2. **接口在 domain，实现在 data/platform。** 不在 feature 定义跨模块接口。
3. **不暴露框架类型。** domain 不出现 `Context`、`Cursor`、`Uri`、`Entity`。
4. **错误用 `Outcome`。** 可预期失败不抛异常；编程错误用 `require`/`check`。
5. **输入/输出模型明确。** Use Case 不接受 Entity，不返回 DAO 投影。
6. **不返回可变对象。** 返回 `List`/不可变 data class。
7. **版本化。** 跨模块公开 API 的破坏性变更需在 PR 说明并更新调用方；内部 API 不承诺稳定。

```kotlin
sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val error: CulinaryError) : Outcome<Nothing>
}
```

## 8. Git 提交规范

### 8.1 Conventional Commits

```text
<type>(<scope>): <subject>

<body: why / what changed / risk>

<footer: refs>
```

| type | 用途 |
|---|---|
| `feat` | 新功能 |
| `fix` | 修复 |
| `refactor` | 不改变行为的重构 |
| `test` | 测试 |
| `docs` | 文档 |
| `build` | 构建/依赖 |
| `ci` | CI |
| `perf` | 性能 |
| `chore` | 杂项 |
| `revert` | 回滚 |

scope 用模块名：`ingredients`、`recipes`、`inventory`、`planning`、`db`、`backup`、`build`。

示例：

```text
feat(inventory): reserve stock on plan confirmation

Reserve available batches FEFO when a plan is confirmed, while keeping
on-hand unchanged. Release reservations on cancel. Implements D-010.

Refs: CUL-42, D-010
```

### 8.2 分支

| 分支 | 用途 |
|---|---|
| `main` | 始终可构建、测试通过 |
| `feat/<short>` | 功能 |
| `fix/<short>` | 修复 |
| `docs/<short>` | 文档 |
| `chore/<short>` | 构建/杂项 |
| `release/<x.y>` | 仅在需要稳定线时创建 |

- 分支短生命周期；合并后删除。
- 不直接在 `main` 提交（个人项目也建议 PR/自查）。
- 合并前 rebase 或 squash，保持线性历史（团队约定）。

### 8.3 提交粒度

- 一个提交一个逻辑变更；不把格式化与功能混在一起。
- 数据库迁移与对应 Entity/DAO/测试同一个提交。
- 文档与代码同 PR。
- 不提交生成的 APK、密钥、`.idea/`、本地 `local.properties`。

## 9. 版本号规范

### 9.1 App

- `MAJOR.MINOR.PATCH`，预发布 `0.x.y`。
- `versionCode = MAJOR * 10000 + MINOR * 100 + PATCH`。
- 破坏性数据/行为变更升 MAJOR；功能升 MINOR；修复升 PATCH。
- 每个发布 tag：`v<versionName>`。

### 9.2 数据库

- 整数，从 1 开始，只增。
- 与 App 版本独立。
- 每次 schema 变更 +1，必须有迁移与测试。

### 9.3 备份格式

- 整数，从 1 开始。
- 变更必须新增 ADR 与兼容测试。

## 10. 测试命名规范

```kotlin
class UnitConverterTest {
    @Test
    fun `converts kilograms to grams within mass dimension`() { ... }

    @Test
    fun `rejects volume to mass without ingredient density`() { ... }
}
```

- 测试名描述行为与条件，不写实现细节。
- 集成测试：`<Feature>IntegrationTest`。
- 迁移测试：`Migration<old>To<new>Test`。
- 测试数据构造器：`TestData.recipeVersion(...)`，集中管理，避免魔法值。

## 11. 目录与文件组织规则

1. 按职责分包，不按“层”在 feature 内重复建 data/domain。
2. 同一条业务链的类放在相近包；不为了“整齐”把 Use Case 全堆一个包。
3. 一个包超过 ~15 个文件时考虑按子域拆分。
4. 不建 `utils` 大杂烩；通用工具放 `core:common` 的具体包。
5. 不提交空包/空模块。
6. 资源按 feature 分目录（如 `res/drawable/ingredient_` 前缀），避免命名冲突。

## 12. 自动化检查

### 12.1 本地

```powershell
# scripts/check.ps1
./gradlew ktlintCheck detekt lintDebug testDebugUnitTest
```

### 12.2 CI 门禁

- `ktlintCheck`
- `detekt`
- `lintDebug`
- 单元测试
- 数据库/迁移测试
- `assembleDebug`
- 依赖规则检查

### 12.3 评审清单

- [ ] 是否新增跨层依赖？
- [ ] 是否在 UI 里写业务规则？
- [ ] 是否所有写操作有事务？
- [ ] 是否所有库存变化有流水？
- [ ] 是否所有用户可见错误有错误码与恢复建议？
- [ ] 是否新增数据库字段有迁移与测试？
- [ ] 是否新增图片路径可缺失降级？
- [ ] 是否更新文档/ADR？

## 13. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 0.1.0 | 2026-11-04 | 初稿；目录、命名、注释、接口、Git、版本、测试规范 | 架构组 |
| 0.2.0 | 2026-11-04 | 目录树标注为细粒度参考；补 15 模块合并映射，待 D-004 | 架构组 |
