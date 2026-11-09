# MISEBASE 1.0.6 · 档案详情统一改版 + 步骤时间 + 统一制作模式

**交付日期：** 2026-11-09
**项目路径：** `D:\MISEBASE`（分支 `restore-v7`，未切换分支、未重置提交）
**版本：** versionName `1.0.6` / versionCode `7` / DB schema `v7`
**任务书：**
1. 《# MISEBASE 开发任务书.docx》—— 菜谱库与酱汁库详情展示页面统一改版（以下简称 T1）
2. 《# MISEBASE · 厨房档案与制作计时系统联合开发任务书.docx》（以下简称 T2）

---

## 0. 两任务冲突与处理（必须先说明）

T1 §4.5 明确"本次不添加步骤时间 / 启动此阶段计时 / 计时器"，并注明"完成计时器功能后，再统一接入"。
T2 第六阶段要求"详情页展示步骤时间与启动此阶段计时"，第七～十二阶段要求统一制作模式。

**处理：** 两任务一次交付，按 T2 作为后置需求实现计时接入，同时严格保留 T1 的视觉框架与全部展示要求；
T1 的"不新增计时"约束按 T2 的"计时已完成并接入"理解，不额外增加 T1 禁止的版本管理、分类层级等任何功能。

---

## 1. 修改前现状审查（T1 §2.2 / T2 第一阶段）

### 1.1 真实架构

- 15 个 Gradle 模块：`app` + `core:{common,domain,database,data,designsystem,platform}` + `feature:{ingredients,sauces,recipes,products,inventory,scan,settings}`。
- Compose + Hilt + Room；`core:domain` 为纯 Kotlin（不依赖 Android）。
- 六库入口在 `app/.../kitchen`：`KitchenScreen`（厨房首页）→ `library/{tab}`（`ArchiveLibraryScreen`，分类钻取 + 档案列表）→ 各库详情/编辑。
- 分类体系已存在：`archive_category` + `archive_category_assignment`（schema v6，`ArchiveCategorySeeder` 已内置三级测试分类树与空分类、测试档案、可清理标记）。

### 1.2 六库现状（修改前）

| 档案库 | 一级页 | 新建入口 | 详情页 | 说明 |
|---|---|---|---|---|
| 酱汁 | `library/sauces` | **缺失**（仅空态里有入口） | `feature:sauces/SauceDetailScreen` | 旧版：PageHeader + 徽标 + 档案说明标签堆叠 + 当前版本 + 组成 |
| 食材 | `library/ingredients` | **缺失** | `feature:ingredients/IngredientDetailScreen` | 本次未改 |
| 菜谱 | `library/recipes` | **缺失** | `feature:recipes/RecipeDetailScreen` | 旧版：PageHeader + 档案说明 + 版本 + 组成 + 展开 |
| 烹饪技术 | `library/techniques` | **缺失** | `app/.../KitchenReferenceScreens` | 本次未改 |
| 制作标准 | `library/production-standards` | **缺失** | 同上 | 本次未改 |
| 储存标准 | `library/storage-standards` | **缺失** | 同上 | 本次未改 |

> 六个库的"新建"按钮在上一轮目录页重构中丢失，只剩空状态里的按钮，属于 T2 §4 必须修复项。

### 1.3 数据模型与图片

- 酱汁：`sauce` + `sauce_version`（含 `steps_json`、`instructions_md`）+ `sauce_component`；图片 `image_asset` 归属 `SAUCE` / `SAUCE_VERSION`（`step_no` 关联步骤）。
- 菜谱：`recipe` + `recipe_version`（**无 steps_json**，只有 `instructions_md`）+ `recipe_component`；图片归属 `RECIPE` / `RECIPE_VERSION`。
- 步骤：酱汁已有"标题 + 说明 + 步骤图 + 增删排序"，但**没有时间**；菜谱只有整段 `instructions_md`，没有结构化步骤。
- 数据库版本：v6；迁移 `CulinaryMigrations.ALL` 1→6；`StartupCoordinator.EXPECTED_SCHEMA_VERSION = 6`。

### 1.4 修改前已定位的问题

1. 六库一级页无新建按钮（T2 §4）。
2. 菜谱/酱汁详情页视觉与 T1 规范差异大：模块无编号、主图/图注缺失、步骤不展示、关联与备注混在"档案说明"里、页脚无更新时间。
3. 菜谱无结构化步骤，无法承载"每步时间"。
4. 编辑入口在详情页中部（T1 要求顶部右侧"编辑档案 ↗"）。

---

## 2. 改造后的统一详情框架（T1 §四 / §五）

### 2.1 共用组件（全部新增在 `core:designsystem/component/ArchiveDetail.kt`）

| 组件 | 作用 |
|---|---|
| `ArchiveDetailBar` | 顶部操作栏：左侧返回箭头、右侧"编辑档案 ↗"、下方 1dp 细线 |
| `ArchiveDetailHeading` | 英文分类标签 → 英文主标题 → 中文名称 → 简短描述 |
| `ArchiveMetaGrid` / `ArchiveMetaItem` | 1:1:1 三列基础信息；空值项自动隐藏并补空列 |
| `ArchiveWideImage` | 16:10 宽幅成品图 + 灰色小字图注；无图时不占位 |
| `ArchiveModuleHeader` | `01 / INGREDIENTS` 式模块头：香槟金编号 + 英文粗体 + 中文名 + 可选副标题 |
| `ArchiveIngredientRow` | 左侧中文名 + 英文次级信息 + 可选标记，右侧用量右对齐 |
| `ArchiveProcedureStep` | 编号 + 标题 → 步骤图（有才显示）→ 完整说明 → 步骤时间 → 启动计时入口 |
| `ArchiveProse` | 保留原始换行的长文本 |
| `ArchiveRelatedRow` | 轻量关联档案行（无卡片） |
| `ArchiveNoteImages` | 研发照片横向排布 |
| `ArchiveDetailFooter` | 细线 + "最后更新 yyyy.MM.dd HH:mm" + MISEBASE 品牌 |
| `KitchenTimeField` | 步骤时间输入：固定六位、显示 `HH/MM/SS`、连续输入右对齐填充 |
| `KitchenTimeVisualTransformation` | 六位数字 ↔ `HH/MM/SS` 光标映射 |
| `KitchenProgressLine` | 制作模式连续细线进度条 |
| `CulinaryLinkText` | 品牌色纯文字动作（启动计时 / 请返回） |
| `formatArchiveTimestamp` | 真实时间戳格式化（页脚，禁止写死日期） |

时间/步骤的纯逻辑：

- `core:common/KitchenTime.kt`：`digits` / `secondsOfDigits` / `digitsOf` / `format` / `describe` / `countdown`（纯函数，可单测）。
- `core:domain/model/KitchenStep.kt`：`KitchenStep(id,title,body,timeSeconds)` + `KitchenStepCodec`（`steps_json` v2；兼容 v1；空 JSON 回退整段说明）。
- 酱汁编辑器 `SauceStepCodec` 改为委托 `KitchenStepCodec`，编辑态仍保留 `SauceStepEditor`。

### 2.2 两个页面的差异处理

- 共用排版、颜色、字号、间距、分割线、图片比例、交互位置；
- 各自读取自己的真实字段，不做"为了统一而统一数据"（详见 §4 字段映射）；
- 不复制两套页面代码：两个详情页只保留"字段映射 + 模块取舍"，其余全部走共用组件。

---

## 3. 视觉规范落地（T1 §三 / T2 第十四阶段）

- 背景 `#FFFFFF`、主文字 `#171717`、次级 `#666666`、辅助 `#999999`、分割线 `#EAEAEA` —— 全部走既有 `CulinaryColorScheme` 语义槽位，未新增硬编码色。
- 香槟金强调：模块编号、启动计时、确认/返回等文字使用 `colorScheme.secondary`（白底 5.03:1 的 `#8A6A2B`，即 T2 指定品牌色）；不使用大面积金色填充，`#B8996D`（Brass400）只作为品牌填充 token 保留。
- 字号层级：英文主标题 `headlineLarge`、中文 `titleLarge`、模块编号 `numeralM`、模块英文 `sectionTitle`、正文 `bodyLarge`、辅助 `bodySmall/labelSmall`；全部来自既有 `CulinaryTextStyles`，无页面级魔法字号。
- 分割线统一 `CulinaryRule`（1dp），无卡片、无阴影、无渐变、无大圆角按钮。

---

## 4. 字段映射（T1 §4.2 / T2 §3.2）

### 4.1 菜谱

| 展示位置 | 数据来源 |
|---|---|
| 英文分类标签 | 分类路径（`archive_category`）→ `RECIPE · 菜品 / 分类` |
| 英文主标题 | `recipe.english_name` |
| 中文名称 | `recipe.name` |
| 简短描述 | `recipe.description` |
| CATEGORY | 分类路径末级，回退 `recipe.category` 枚举中文 |
| YIELD | 当前版本 `yield_qty + yield_unit_id` |
| DIFFICULTY | `recipe.difficulty`（空则 `—`） |
| 主图 | `image_asset`（`RECIPE` + PRIMARY） |
| 01 原料配方 | `recipe_component` + 已解析标签；单位原样保留 |
| 02 制作工艺 | `recipe_version.steps_json`（v2）；为空时回退 `instructions_md` 单步骤 |
| 步骤图片 | `image_asset`（`RECIPE_VERSION` + PROCESS + `step_no`） |
| 03 适用范围 | `recipe.cuisine` / `prep_minutes` / `cook_minutes` / `allergen_notes` / `ingredient_summary`（与 description 重复时隐藏） |
| 04 关联档案 | `kitchen_relation`（既有关系，只读） |
| 05 研发备注 | `recipe.seed_note`（有才显示） |
| 页脚 | `recipe.updated_at` |
| 按份数展开 | 既有 `ExpandRecipeUseCase`，默认收起，保留原功能 |

### 4.2 酱汁

| 展示位置 | 数据来源 |
|---|---|
| 英文分类标签 | `SAUCE · family` 或分类路径 |
| 英文主标题 | `sauce.english_name` |
| 中文名称 | `sauce.name` |
| 简短描述 | `sauce.description`（空则 `usage_notes`） |
| CATEGORY / YIELD / DIFFICULTY | `family` / 版本产量 / `difficulty` |
| 主图 + 图注 | `image_asset`（`SAUCE` + PRIMARY）+ `sauce.hero_caption` |
| 01 原料配方 | `sauce_component`（名称覆盖优先） |
| 02 制作工艺 | `sauce_version.steps_json` v2（含时间） |
| 步骤图片 | `image_asset`（`SAUCE_VERSION` + PROCESS + `step_no`） |
| 03 适用范围 | `sauce.usage_notes` |
| 04 关联档案 | `kitchen_relation`（衍生酱汁） |
| 05 研发备注 | `flavor_profile` / `texture` / `main_ingredients` / `preparation_summary` / `key_parameters` / `storage_notes` / `adjustable_notes` / `seed_note` + REFERENCE 图片 |
| 页脚 | `sauce.updated_at` |

> 所有字段均来自现有 Entity；没有新增业务字段（除步骤时间，见 §5）。

---

## 5. 步骤时间与数据变更（T2 第五阶段 / 第十五阶段）

### 5.1 数据结构

- 酱汁：`sauce_version.steps_json` 从 v1 升级为 **v2**，每步新增 `"timeSeconds"`；读取 v1 时时间按 0 处理，保存时写 v2。
- 菜谱：`recipe_version` 新增可空列 **`steps_json`**（v7）；历史数据为 NULL，读取时回退 `instructions_md`，不丢任何文字。

### 5.2 Room Migration

```
v6 → v7：ALTER TABLE `recipe_version` ADD COLUMN `steps_json` TEXT
```

- 正式 Migration（`CulinaryMigrations.MIGRATION_6_7`），非 destructive；
- 不改任何既有列、不重建表、不改变档案 ID 与步骤顺序、不删除图片关联；
- `StartupCoordinator.EXPECTED_SCHEMA_VERSION` 同步改为 7（否则旧版会把 v7 误判为"更新版本"而拒绝启动——本次已修复该隐患）；
- `core/database/schemas/.../7.json` 已生成并纳入版本库。

### 5.3 时间输入与展示

- 默认 `00/00/00`；连续输入从秒位向左填充：`155` → `00/01/55`；
- 单输入框（非时/分/秒三段），支持退格、清空、编辑既有时间、零时间；
- 输入获得焦点时全选，第一次按键替换旧值，光标固定在末尾（避免在数字中间插入造成错误进位）；
- 数据保存为总秒数；详情页 `步骤时间 00/01/55` + 线框时钟 + "启动此阶段计时"（品牌色、无背景、无实心按钮）；
- 零时间步骤：不显示启动入口，但步骤本身完整展示。

---

## 6. 统一制作模式（T2 第七～十二阶段）

### 6.1 结构

- 新增 `app/.../cooking/`：`CookingPlan`（纯逻辑）、`CookingViewModel`、`CookingScreen`、`CookingRoute`；
- 菜谱与酱汁共用同一页面与同一 ViewModel；来源只决定标题文案与图片归属（`SAUCE_VERSION` / `RECIPE_VERSION`）；
- 路由 `cooking/{kind}/{id}/{step}`，由详情页"启动此阶段计时"携带**原始步骤序号**进入。

### 6.2 业务规则（与任务书一致）

1. 计划从被点击步骤开始，忽略其之前的步骤；
2. 纳入到**最后一个有效计时步骤**为止，其后的无时间步骤不计入；
3. 中间无时间步骤必须纳入，且参与进度计算；
4. 制作模式重新编号 `01..N`，顶部进度、弹窗、结束状态共用同一套编号；
5. 计时结束进入下一制作步骤：有时间的步骤等待用户开始（不自动开始）；无时间的步骤弹出阻断弹窗，逐个确认，不能跳过、不能返回键绕过、遮罩不可点击；
6. 最后一个有效计时步骤结束后立即完成：进度条填满、对勾、"计时时间已结束 / 所有计时阶段均已完成"、`00/00/00`、最后完成阶段名称与预设时间、"请返回"；
7. 退出（返回箭头 / 系统返回 / 请返回）回到详情页并**自动滚动定位**到最后完成或当前步骤，不回顶部、不回列表；
8. 计时基于 `SystemClock.elapsedRealtime()` 计算剩余时间，UI 刷新只负责显示，滚动/重组不重置，离开页面即取消计时任务；
9. 不改主页计时器、不同步主页快捷计时、不复用制作流程状态。

### 6.3 无时间步骤弹窗

`此步骤没有操作时间  02 / 05` + `请按照以下流程操作。` + 步骤标题 +（有则）步骤图 + 完整说明 + 右下角 `确认 →`；
`DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)` + `BackHandler {}` 双重阻断。

---

## 7. 六库新建入口（T2 第四阶段）

- 位置：档案库一级页（`library/{tab}` 根分类）标题行右侧；厨房首页不显示；二级/三级分类不重复显示。
- 六个库完全一致：同一个 `CulinaryButton`（黑色主按钮）、同一 `CulinaryIcons.Add` 图标、同一高度/间距，文案统一为"新建酱汁 / 新建食材 / 新建菜谱 / 新建烹饪技术 / 新建制作标准 / 新建储存标准"（恢复原有命名，不使用"添加/新增"）。
- 点击仍走原有新建流程：`SaucesRoutes.edit(null)`、`IngredientsRoutes.edit(null)`、`RecipesRoutes.edit(null)`、`kitchenReferenceNewRoute(type)`，未新建任何平行新建系统。

---

## 8. 修改文件清单

### 8.1 新增

| 文件 | 说明 |
|---|---|
| `core/common/.../KitchenTime.kt` | 时间格式/输入/倒计时纯函数 |
| `core/common/src/test/.../KitchenTimeTest.kt` | 时间规则单测 |
| `core/domain/model/KitchenStep.kt` | 共用步骤模型 + `steps_json` v2 编解码 |
| `core/domain/src/test/.../KitchenStepCodecTest.kt` | 编解码单测 |
| `core/designsystem/component/ArchiveDetail.kt` | 统一详情框架组件 |
| `core/designsystem/icon/CulinaryOutlineIcons.kt` | 线框时钟/对勾 |
| `core/database/schemas/.../7.json` | v7 schema 导出 |
| `app/cooking/CookingPlan.kt` | 制作流程构建/进度（纯逻辑） |
| `app/cooking/CookingViewModel.kt` | 制作模式状态机与计时 |
| `app/cooking/CookingScreen.kt` | 制作模式界面、阻断弹窗、完成态 |
| `app/cooking/CookingRoute.kt` | 路由常量 |
| `app/src/test/.../CookingPlanTest.kt` | 制作流程规则单测（8 例） |
| `feature/recipes/RecipeMethodEditor.kt` | 菜谱编辑器步骤区 |

### 8.2 修改（生产代码）

`app/build.gradle.kts`、`app/res/values/strings.xml`、`app/kitchen/archive/ArchiveLibraryScreen.kt`、`app/navigation/CulinaryNavHost.kt`；
`core/database/{CulinaryDatabase,CulinaryMigrations}.kt`、`core/database/entity/RecipeVersionEntity.kt`；
`core/data/{mapper/RecipeMapper,repository/ArchiveImageRepositoryImpl,startup/StartupCoordinator}.kt`；
`core/domain/model/{ArchiveImage,Recipe,RecipeVersion}.kt`、`core/domain/usecase/archive/ArchiveImageUseCase.kt`、`core/domain/usecase/recipe/{RecipeDraftModels,RecipeInputValidator}.kt`；
`feature/sauces/{SauceDetailScreen,SauceDetailViewModel,SauceStepCodec,SauceEditorScreen,SauceEditorSections,SauceEditorViewModel,SaucesUiState}.kt`；
`feature/recipes/{RecipeDetailScreen,RecipeDetailViewModel,RecipeEditorScreen,RecipeEditorViewModel,RecipeUiState}.kt`。

### 8.3 修改（测试代码，仅同步断言/新参数）

`core/data/src/test/.../{BackupRestoreV4Test,QuiescedDatabaseSnapshotterTest,DebugInfoProviderTest,StartupCoordinatorTest}.kt`（schema 6→7）；
`core/domain/src/test/.../{TransactionBoundaryTest,RequirementPlanUseCasesTest,SauceUseCasesTest}.kt`（补齐既有接口 `archive` 的测试替身）；
`feature/sauces/src/testDebug/.../SaucesUiTest.kt`（新布局需先滚动再断言）。

### 8.4 删除

无。未删除任何页面、字段、按钮或数据表。

---

## 9. 数据库变化

| 项 | 修改前 | 修改后 |
|---|---|---|
| schema 版本 | v6 | v7 |
| 迁移 | 1→2→3→4→5→6 | 追加 6→7（仅 `ALTER TABLE recipe_version ADD COLUMN steps_json TEXT`） |
| 数据表 | 36 | 36（无新增/删除表） |
| 字段 | — | 新增 1 个可空列 `recipe_version.steps_json` |
| 既有数据 | — | 不迁移、不改写、不删除；读取端回退 `instructions_md` |

升级验证：在模拟器上先安装 1.0.5（v6，含种子数据），再覆盖安装 1.0.6（v7），首页与六库数据完整，无崩溃（见证据 01）。

---

## 10. 前后对比（截图见 `evidence/`）

| 对比项 | 修改前 | 修改后 |
|---|---|---|
| 酱汁详情 | PageHeader + 徽标；"档案说明"9 个标签值堆叠；"当前版本 v1 已发布"；中部"编辑"按钮；"组成（0 行）" | 顶部返回 + 编辑档案 ↗；英文/中文标题层级；1:1:1 元信息；模块 01–05 编号；步骤含时间与启动计时；页脚更新时间 |
| 菜谱详情 | 同上结构 + 展开输入框常驻 | 同一框架；原料行右对齐用量；步骤区；"按份数展开"默认收起 |
| 六库一级页 | 无新建按钮 | 标题行右侧统一"新建 X"按钮 |

- `evidence/00-sauce-detail-before.png`（1.0.5）
- `evidence/02-sauce-detail-after.png` / `evidence/07-recipe-detail-after.png`（1.0.6）
- `evidence/01-sauce-library-new-button.png` / `evidence/09-category-level2-no-add.png`
- `evidence/03-cooking-mode.png` / `04-no-time-dialog.png` / `05-cooking-finished.png` / `06-return-position.png`
- `evidence/08-recipe-editor-step-time.png`

---

## 11. 构建与产物

- 构建命令：`.\gradlew.bat :app:assembleRelease`
- 结果：**BUILD SUCCESSFUL**（`:app:lintVitalRelease` 通过；R8 + 资源压缩启用）
- 签名：沿用 `local.properties` 中的原有 release keystore（未更换签名、未改包名 `com.culinary`）
- APK：`dist/MISEBASE-1.0.6-archive-detail-cooking-20261109-release.apk`（24,738,593 字节）
- SHA-256：`0a14e60fb88e34d1e4190cda01b88a741e0463b3f7448124be1736326e569ee0`

---

## 12. 测试结果

### 12.1 单元测试（本机 JVM / Robolectric）

| 模块 | 结果 |
|---|---|
| `core:common` | 通过 |
| `core:domain` | 172 / 173 通过；1 失败为**基线已有**（`SaveIngredientUseCaseTest > keeps the id when editing an existing ingredient`，本次未改 ingredient 代码；已用 `git stash` 在 HEAD 复现同样失败） |
| `core:data` | 通过（100 例） |
| `feature:sauces` | 通过（13 例） |
| `feature:recipes` | 通过（11 例） |
| `feature:ingredients` / `feature:inventory` / `feature:scan` / `app` | 通过 |

新增覆盖：
- `CookingPlanTest`：全部有时间 / 中间无时间 / 连续无时间 / 前期无时间起跳 / 末尾无时间忽略 / 单步 / 无有效时间 / 进度按整条流程计算（8 例）。
- `KitchenTimeTest`：`155 → 00/01/55`、六位进位、超长输入、描述文案、倒计时、边界钳制（7 例）。
- `KitchenStepCodecTest`：v2 往返、v1 兼容、旧说明回退、空值、`instructions` 文本（5 例）。

### 12.2 真机（模拟器 `Medium_Phone_API_36.1`，1080×2400@453dpi）人工验证

| 用例 | 结果 |
|---|---|
| 1.0.5（v6）覆盖升级到 1.0.6（v7） | 通过：数据完整、无崩溃 |
| 全新安装 1.0.6 | 通过 |
| 厨房首页无新建按钮、六库入口 | 通过 |
| 酱汁库一级页显示"新建酱汁" | 通过 |
| 二级分类页不重复显示新建入口 | 通过 |
| 酱汁详情：标题层级 / 三列信息 / 模块编号 / 步骤 / 步骤时间 / 启动计时 / 页脚 | 通过 |
| 无原料 / 无步骤时模块合理隐藏 | 通过（荷兰酱无组成行，01 模块不显示空占位） |
| 酱汁编辑器步骤时间输入（输入 10 → 00/00/10） | 通过 |
| 保存后详情回显步骤时间 | 通过 |
| 从第 01 步启动制作模式：编号 01/03、进度、不自动开始 | 通过 |
| 计时结束自动进入 02/03 并弹出无时间阻断弹窗 | 通过 |
| 确认后进入 03/03（不自动开始） | 通过 |
| 最后一步结束：完成态、进度填满、00/00/00、最后完成阶段 | 通过 |
| "请返回"回到详情并滚动到第 03 步 | 通过 |
| 菜谱详情：统一框架、说明回退为步骤、展开入口收起 | 通过 |
| 菜谱编辑器：步骤区 + 步骤时间 + 原有说明保留 | 通过（界面） |

### 12.3 未验证（如实标注）

| 项目 | 状态 |
|---|---|
| 食材库 / 烹饪技术 / 制作标准 / 储存标准四个库的新建按钮逐个点击 | 未验证（同一组件与同一 `onAdd` 分支，仅酱汁库实测） |
| 第三级分类实际钻取 | 未验证（测试树与空分类已存在，本次仅到二级） |
| 菜谱步骤时间保存 → 菜谱制作模式全流程 | 未验证（菜谱编辑器已提供入口，未在设备上保存并启动） |
| 步骤图片展示（酱汁/菜谱） | 未验证（种子无步骤图，未上传图片） |
| 暂停 / 继续 / ±60s 调整的设备行为 | 未验证（代码路径已实现，仅开始/结束实测） |
| 后台/锁屏计时漂移 | 未验证 |
| 备份恢复、扫码、库存、商品、采购、搜索回归 | 未验证（未改动相关代码） |
| 长标题/长文本/超多步骤压力 | 未验证 |
| 旋转屏幕、深色模式 | 未验证 |
| lint / detekt / ktlint 全量检查 | 未运行（仅 `lintVitalRelease` 通过） |

---

## 13. Git 与回滚

- 工作分支：`restore-v7`（未切换、未重置、未清理他人未提交内容）
- 回滚点（提交前）：`2c56f6b docs(archive): finalize APK hash and verification screenshots`
- 本次提交：`1740660` · `feat(archive): unify recipe/sauce detail, step time and cooking mode`
- 回滚方式：`git revert <本次提交>` 或 `git reset --hard 2c56f6b`（后者会丢弃本次改动，按需使用）
- APK 回滚：重新安装 `dist/MISEBASE-1.0.5-archive-unified-release.apk` 前需注意数据库为 v7，旧版会因 schema 更高而拒绝启动；如需降级必须先导出/清空数据。

---

## 14. 已知差异与风险

1. **T1 与 T2 的计时条款冲突**：按 §0 处理，详情页已接入步骤计时（T2 §6）；若验收坚持 T1 原文"详情页不得出现计时"，需单独回退 `ArchiveProcedureStep` 的启动入口与制作模式路由（改动集中在 3 个文件）。
2. **菜谱步骤图片**：菜谱原本没有步骤图上传能力，本次未新增上传机制（遵守"不修改图片上传/存储"约束），仅在 `image_asset` 已存在 `RECIPE_VERSION + step_no` 时展示。
3. **菜谱无"适用范围"独立字段**：03 模块使用 `cuisine / prep / cook / allergen / ingredient_summary` 真实字段，无内容时整块隐藏。
4. **module 编号跳号**：模块隐藏时编号不重排（01/03/05），编号是模块标识而非序号，符合 T1 固定编号要求。
5. **基线失败用例**：`SaveIngredientUseCaseTest` 的 1 例失败为改动前既有问题，未在本次修复（不属于两任务范围，且涉及 ingredient 既有语义）。
