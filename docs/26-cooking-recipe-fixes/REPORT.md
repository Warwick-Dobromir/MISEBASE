# MISEBASE 1.0.7 · T3–T5 计时交互修复 + 菜谱档案统一

**交付日期：** 2026-11-09
**项目路径：** `D:\MISEBASE`
**分支：** `restore-v7`
**基线提交：** `d12de0a`（1.0.6 / versionCode 7 / schema v7）
**本次提交：** `17bdfa2`（源码 + 测试 + 版本号）
**版本：** `1.0.7` / versionCode `8`
**数据库：** schema **仍为 v7**，无新增迁移、无字段增删

---

## 1. 修改前真实代码审查结论

### 1.1 计时

- 页面：`app/src/main/java/com/culinary/cooking/CookingScreen.kt`
- 状态机：`CookingViewModel.kt`
- 计划构建：`CookingPlan.kt`
- 时间格式：`core/common/.../KitchenTime.kt`

实际现状：

1. `TimerBlock` 的加减按钮调用 `onAdjust(-60)` / `onAdjust(60)`，每次调整 60 秒；
2. 倒计时归零后 `onTimedStepFinished()` 直接推进到下一步，最后一步直接进入结束态；
3. 没有“当前步骤已完成”的状态、没有完成提示、没有提示音；
4. 计时状态全部在 ViewModel 内，旋转屏幕可保留，但进程重建后回到第 1 步；
5. `KitchenTime.format` 本身已经固定输出 `HH/MM/SS`，显示格式不是本次主要缺陷。

### 1.2 菜谱

- `feature/recipes` 新建/编辑/详情。
- `core/domain` 的 `SaveRecipeDraftUseCase`、`GetRecipeDetailUseCase`、`RecipeVersionInput`。
- `core/data` 的 `RecipeRepositoryImpl`。
- 数据库的已发布版本不可变触发器与 `recipe.difficulty` CHECK 约束。

实际现状：

1. 编辑器保存时**总是创建新草稿**，没有 `editingDraftId`，重复保存会产生多个版本；
2. 已发布菜谱的详情把 `currentVersion`（已发布版本）的组成行作为展示数据，编辑保存的草稿内容在详情中看不完整；
3. 编辑器只暴露名称、分类、产量、份数、组成、步骤；英文名、描述、适用菜系、准备/烹饪时间、过敏原、备注等已有字段没有编辑入口；
4. 菜谱没有独立的“锁定”布尔字段；“已发布不可修改”来自数据库触发器与详情提示，不是可解除的 UI 锁。正确做法是发布版本保持只读、编辑写入可继续编辑的草稿，而不是删除保护；
5. `recipe.difficulty` 列有 CHECK 约束，只允许 `EASY/MEDIUM/HARD`；直接把“最小制作容量”当成任意文本写入会触发 `CUL-CON-001`。因此本次保留既有枚举值与存储方式，仅调整显示名称。

### 1.3 酱汁

酱汁新建/编辑/详情、草稿更新、关系、图片、发布流程均已成熟。本次只改两处显示名称，未改结构、未改业务逻辑。

---

## 2. T3：时间调整

### 2.1 实现

- `CookingScreen.kt`：加减按钮改为 `onAdjust(-1)` / `onAdjust(1)`，界面增加“每次调整 1 秒”。
- `CookingViewModel.adjust()`：
  - 以“当前剩余总秒数”为唯一值；
  - 每次只加/减 1 秒；
  - `coerceIn(0, KitchenTime.MAX_SECONDS)` 处理上下边界；
  - 暂停/运行时都会把新的剩余量写回 `baseRemaining`，不会只改显示。
- `KitchenTime.format()` 始终输出 `HH/MM/SS`，因此：
  - `00/00/59 + 1 → 00/01/00`
  - `00/01/00 - 1 → 00/00/59`
  - 不会因为进位/借位隐藏时、分、秒段。

### 2.2 保留

- 从用户点击的步骤开始，忽略之前的步骤；
- 中间无时间步骤仍纳入统一编号，并弹窗阻断；
- 顶部进度、弹窗编号、结束态共用制作流程编号；
- 计时阶段划分与步骤顺序未改。

---

## 3. T4：倒计时结束反馈

### 3.1 实现

- 新增 `app/.../cooking/CookingAlert.kt`：
  - `CookingAlert` 接口；
  - `ToneCookingAlert` 使用系统 `ToneGenerator` 播放一次短提示音；
  - 不新增权限、不引入第三方音频库。
- `CookingViewModel.completeCurrentStep()`：
  - 归零时立即停止 tick；
  - `remainingSeconds = 0`、`isRunning = false`；
  - 把当前步骤编号写入 `completedNumbers`；
  - 播放一次提示音；
  - **不推进步骤**。
- `advanceAfterCompletion()`：
  - 只有当前步骤已完成时才允许；
  - 有下一步则进入下一步；最后一步则进入结束态。
- UI `TimerBlock`：
  - 已完成时显示 `此步已完成`；
  - 原来的启动计时入口替换为 `此步已完成，请进入下一步`；
  - 隐藏加减/播放控件，避免重复启动已完成计时。
- `CookingUiState.completedNumbers` + `currentCompleted` 是完成态唯一来源。
- `SavedStateHandle` 持久化 `index / remaining / completed / finished`：
  - 旋转、页面重组、进程重建后仍能恢复完成态；
  - 恢复时不会再次播放提示音（提示音只由 ViewModel 的完成路径触发一次）。
- 无时间步骤仍走原有阻断弹窗与手动确认，没有改变交互。

### 3.2 保留

- 结束态、进度填满、`00/00/00`、最后完成阶段、返回详情定位；
- 返回详情时仍按最后完成/当前步骤定位。

---

## 4. T5：菜谱新建、编辑、详情

### 4.1 草稿编辑（核心修复）

- `RecipeVersionInput` 新增可选 `versionId`；
- `SaveRecipeDraftUseCase`：
  - `versionId` 为空：创建新草稿；
  - `versionId` 非空：校验必须属于同一菜谱且状态为 DRAFT，然后更新同一份草稿；
  - 已发布版本永远不被覆盖。
- `RecipeRepositoryImpl.saveDraft`：
  - 新草稿插入；
  - 已有草稿更新字段并替换组成行；
  - 不新增表、不新增列。
- `RecipeEditorViewModel`：
  - 记录 `editingDraftId`；
  - 再次保存更新同一草稿，不再重复创建；
  - 只有用户明确点“保存并发布”才发布；保存草稿不会自动重新锁定。
- 已发布菜谱：允许编辑，保存为草稿；详情显示草稿和“发布 vN”入口；发布版本保持只读。

### 4.2 详情展示

- `RecipeDetail` 新增 `displayVersion`：
  - 有草稿时优先草稿；否则当前发布版本；
  - `currentVersion` 仍保持“当前发布版本”的指针语义。
- `GetRecipeDetailUseCase` 用展示版本生成组成行、基准份数与 `displayVersion`。
- `RecipeDetailViewModel` 用展示版本读取步骤、步骤图片，并加载菜谱 REFERENCE 研发照片。
- 详情保留：顶部导航、标题、元信息、主图、原料配方、制作工艺、步骤时间、关联档案、研发备注、页脚、按份数展开、发布草稿入口。

### 4.3 编辑器字段

新增/恢复的已有字段：

- 英文名称 (`recipe.english_name`)
- 档案说明 (`recipe.description`)
- 适用菜系 (`recipe.cuisine`)
- 准备时间 (`recipe.prep_minutes`)
- 烹饪时间 (`recipe.cook_minutes`)
- 过敏原提示 (`recipe.allergen_notes`)
- 最小制作容量（沿用 `recipe.difficulty` 的既有值）
- 研发备注（既有 `seed_note`，只读展示）

保留：

- 原料配方（食材/子菜谱/酱汁、数量、单位、可选）
- 结构化步骤、步骤标题/说明/步骤时间
- 基准份数、按份数展开
- 主图/步骤图片的读取与展示（本次不新增上传能力）
- 现有保存草稿/保存并发布入口

### 4.4 图片

- 详情主图：保留 `image_asset` PRIMARY 读取；
- 步骤图片：保留 `RECIPE_VERSION + step_no` 读取；
- 研发照片：补充 `RECIPE + REFERENCE` 读取；
- 图片为空时不显示、不使用占位图；
- 未新增图片上传 UI、未改图片存储结构。

---

## 5. “难度”→“最小制作容量”

### 5.1 实现

- 酱汁详情：`DIFFICULTY` → `MIN. CAPACITY`；
- 酱汁编辑器：`DIFFICULTY` → `MIN. CAPACITY`，占位符改为“选择最小制作容量”；
- 菜谱详情：`DIFFICULTY` → `MIN. CAPACITY`，值显示为“简单/中等/困难”；
- 菜谱编辑器：增加“最小制作容量”选择器，选项与酱汁一致。

### 5.2 数据结论（必须说明）

- 酱汁：底层仍是 `sauce.difficulty`，值仍为 `EASY/MEDIUM/HARD`，没有迁移、没有删数据；
- 菜谱：底层仍是 `recipe.difficulty`，数据库 CHECK 仍只允许 `EASY/MEDIUM/HARD`；
- 为避免破坏结构，本次没有把该列改成任意文本或新增容量列；
- 因此“最小制作容量”目前是既有难度枚举的**显示名称调整**，不是新的数值容量字段；
- T6 若需要读取真正的数值容量，需要单独决策是否新增字段/迁移；本任务未做。

---

## 6. 修改文件清单

### 6.1 新增

- `app/src/main/java/com/culinary/cooking/CookingAlert.kt`
- `app/src/test/java/com/culinary/cooking/CookingViewModelTest.kt`

### 6.2 修改（生产代码）

- `app/build.gradle.kts`（1.0.7 / 8）
- `app/src/main/java/com/culinary/cooking/CookingScreen.kt`
- `app/src/main/java/com/culinary/cooking/CookingViewModel.kt`
- `app/src/main/java/com/culinary/di/AppModule.kt`
- `app/src/main/res/values/strings.xml`
- `core/data/.../repository/RecipeRepositoryImpl.kt`
- `core/database/.../dao/RecipeComponentDao.kt`
- `core/database/.../dao/RecipeVersionDao.kt`
- `core/domain/.../model/RecipeDetail.kt`
- `core/domain/.../usecase/recipe/GetRecipeDetailUseCase.kt`
- `core/domain/.../usecase/recipe/RecipeDraftModels.kt`
- `core/domain/.../usecase/recipe/SaveRecipeDraftUseCase.kt`
- `core/domain/.../usecase/recipe/SaveRecipeUseCase.kt`
- `feature/recipes/.../RecipeDetailScreen.kt`
- `feature/recipes/.../RecipeDetailViewModel.kt`
- `feature/recipes/.../RecipeEditorScreen.kt`
- `feature/recipes/.../RecipeEditorViewModel.kt`
- `feature/recipes/.../RecipeMethodEditor.kt`
- `feature/recipes/.../RecipeUiState.kt`
- `feature/recipes/src/main/res/values/strings.xml`
- `feature/sauces/.../SauceDetailScreen.kt`
- `feature/sauces/.../SauceEditorComponents.kt`
- `feature/sauces/.../SauceEditorSections.kt`
- `feature/sauces/src/main/res/values/strings.xml`

### 6.3 修改（测试）

- `core/domain/src/test/.../RecipeUseCasesTest.kt`
- `feature/recipes/src/test/.../RecipesViewModelTest.kt`
- `feature/recipes/src/testDebug/.../RecipesUiTest.kt`
- `feature/sauces/src/testDebug/.../SaucesUiTest.kt`

### 6.4 未修改

- 酱汁配方结构、图片上传、关系保存、发布业务；
- 库存、食材、商品、扫码、设置；
- 数据库 Schema/Entity/迁移；
- 六库一级入口与导航。

---

## 7. 测试结果

### 7.1 本次相关模块（全部通过）

| 模块 | 用例 | 结果 |
|---|---:|---|
| `app` 制作模式 | `CookingPlanTest` 8 + `CookingViewModelTest` 6 = **14** | 通过 |
| `feature:recipes` | `RecipesViewModelTest` 12 + `RecipesUiTest` 3 = **15** | 通过 |
| `feature:sauces` | `SaucesViewModelTest` 9 + `SaucesUiTest` 4 = **13** | 通过 |
| `core:domain` 菜谱用例 | `RecipeUseCasesTest` **8** | 通过 |

新增覆盖：

- 每次 ±1 秒、进位/借位；
- 倒计时结束停留当前步骤、不自动推进；
- 完成态、提示音只播放一次；
- 用同一 SavedStateHandle 重建 ViewModel 后完成态不丢失、不重播；
- 最后一步点击完成提示后才进入结束态；
- 无时间步骤阻断确认；
- 从后续步骤开始、忽略前置步骤、统一编号；
- 已发布菜谱保存草稿、草稿指针不变；
- 重复保存更新同一草稿（只有 1 个 DRAFT）；
- 新菜谱默认保存为 DRAFT、不发布；
- 详情优先展示草稿；
- “最小制作容量”标签与选择器。

### 7.2 全量 JVM 测试（含基线失败）

执行：`.\gradlew.bat test`

结果：**未全绿**，失败均来自本次未修改模块，记录如下：

| 任务 | 用例数 | 失败 | 说明 |
|---|---:|---:|---|
| `core:domain:test` | 175 | 1 | `SaveIngredientUseCaseTest > keeps the id when editing an existing ingredient`；1.0.6 报告已记录为基线失败 |
| `core:database:testDebugUnitTest` | 88 | 7 | 迁移/导出测试仍按 v5/v6 断言（期望 5 条迁移、版本 6），当前基线为 schema v7、6 条迁移；本次未改数据库结构 |
| `core:platform:testDebugUnitTest` | 26 | 1 | `WorkManagerBackupSchedulerTest > enqueues an immediate backup and returns its id`；本次未修改 `core:platform`，按基线/环境问题单独记录 |

`core:database` 的 7 个失败：

- `CulinaryMigrationV1ToV2Test`（期望 6，实际 7）
- `CulinaryMigrationV2ToV3Test` / `V3ToV4` / `V4ToV5`（迁移链要求到 7）
- `CulinaryRollbackV1Test`（期望 6，实际 7）
- `DatabaseFoundationTest`（期望 6，实际 7）
- `SchemaExportTest`（期望 5 条迁移，实际 6）

这些失败在本次改动前已由 1.0.6 的 schema v7 变更引入；本次没有修改迁移、schema 或上述测试。

### 7.3 构建

- `:app:assembleRelease`：**BUILD SUCCESSFUL**
- `lintVitalRelease`：通过
- R8 / 资源压缩：启用
- APK 安装、启动：通过

---

## 8. 模拟器人工验证

设备：`Medium_Phone_API_36.1`（Android 16 / API 36，x86_64），Release APK。

| 用例 | 结果 | 证据 |
|---|---|---|
| 制作模式显示 `00/00/05`，提示“每次调整 1 秒” | 通过 | `evidence/01-cooking-timer.png` |
| `00/00/05` 点 +1 → `00/00/06` | 通过 | 屏幕文本 |
| 再点 -2 → `00/00/04` | 通过 | 屏幕文本 |
| 倒计时结束停在当前步骤 `01/01`，显示 `00/00/00`、`此步已完成`、`此步已完成，请进入下一步` | 通过 | `evidence/02-cooking-complete.png` |
| 点击完成提示后进入结束态 | 通过 | 屏幕文本 |
| 已发布菜谱编辑保存为草稿，详情显示草稿与 `发布 v2` | 通过 | `evidence/08-recipe-published-with-draft.png`、DB 查询 |
| 再次编辑同一草稿，版本仍为 v2，未产生 v3 | 通过 | DB 查询：仅 1 条 DRAFT，`version_no=2`，yield 从 3 更新到 4 |
| 新建菜谱保存为草稿，详情显示 `草稿 / 发布 v1` | 通过 | `evidence/05-recipe-new-draft-detail.png` |
| 新建菜谱“最小制作容量：简单” | 通过 | `evidence/03-recipe-new-draft-editor.png`、`evidence/04-recipe-capacity-picker.png` |
| 菜谱详情显示 `MIN. CAPACITY` | 通过 | `evidence/06-recipe-detail-published.png` |
| 酱汁详情显示 `MIN. CAPACITY` | 通过 | 屏幕文本 |
| Release APK 安装启动、版本 1.0.7 / 8 | 通过 | `evidence/07-release-home.png`、`dumpsys package` |

### 未验证 / 未执行

- 步骤图片、主图的实际展示：种子数据没有图片，未在设备上肉眼验证；代码路径保留；
- 提示音的实际可听性：模拟器音频关闭，未做声压/听感验证；单元测试验证调用次数为 1；
- 旋转屏幕手工验证：未执行；已用 SavedStateHandle 重建 ViewModel 的单测覆盖；
- 后台/锁屏计时漂移：未执行；
- 酱汁新建/编辑全流程回归：本次酱汁仅改字符串与 UI 标签，`feature:sauces` 单测通过；未再逐项手工重跑；
- `ktlintCheck` / `detekt` 全量：未执行；只运行了 `lintVitalRelease`；
- 全量 JVM 测试未全绿（见 §7.2，均为基线失败）。

---

## 9. Release APK

- 文件：`dist/MISEBASE-1.0.7-cooking-recipe-20261109-release.apk`
- 大小：**24,760,835** 字节
- SHA-256：`9c72b033bc5fdc984a3d31f782313656d2f3cd0f4d31862f05beed241dc017f0`
- 校验文件：`dist/MISEBASE-1.0.7-cooking-recipe-20261109-release.apk.sha256`
- 签名：沿用原 release keystore，`com.culinary`；v2 签名验证通过（与既有交付一致，无 v1/v3）

---

## 10. Git 与回滚

- 基线：`d12de0a`（1.0.6 / 7 / schema v7）
- 本次功能提交：`17bdfa2` `feat(cooking,recipes): fix timer completion and unify recipe editing`
- 回滚点：`d12de0a`
- 回滚方式：
  - `git revert 17bdfa2`（保留历史）
  - 或 `git reset --hard d12de0a`（丢弃本次改动，按需使用）
- 数据库：schema 仍为 v7，无需降级/迁移；APK 可覆盖安装 1.0.6 数据。

---

## 11. 已知差异与风险

1. “最小制作容量”目前仍复用 `difficulty` 的 `EASY/MEDIUM/HARD` 值；这是为了遵守“只改字段名称、不改数据结构、不删数据”。若后续要求真正的数值容量，需要独立 schema 变更与迁移。
2. 全量测试存在 9 个基线失败（domain 1、database 7、platform 1），本次未修复，避免扩大范围。
3. 菜谱图片上传仍不存在；本次只保留读取/展示，没有新增上传。
4. 制作模式结束提示音使用系统 `ToneGenerator`，不依赖新增音频资源；不同设备音量/静音策略可能影响听感。
5. 计时仍使用 `SystemClock.elapsedRealtime()`，后台/锁屏漂移未做设备级验证。
