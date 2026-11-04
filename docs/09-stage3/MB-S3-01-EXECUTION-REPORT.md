# MB-S3-01 Stage 3 三大档案库执行报告

**文档编号**：MB-S3-01
**版本**：`1.0.0`
**状态**：`Review`
**日期**：2026-11-04
**范围**：酱汁库、食材库、菜谱库、N1 导航、设计系统扩展、`SAUCE` 映射边界、Repository 事务边界
**不以本报告关闭 Stage 3，也不构成 Stage 4 启动授权。**

---

## 1. 执行摘要

Stage 3 在既有 Schema v2（33 表）、13 模块与已签收的 MB-D-008 A / MB-D-009 N1 基础上完成：

- 设计系统按「专业厨房档案」扩展：暖中性 + Orange 强调 + 暖灰辅助；字阶、间距、圆角、线性图标与 13 个通用组件落地；
- 导航按 N1 重建：酱汁 / 食材 / 菜谱 / 库存 / 扫码五个底部一级入口，设置放顶栏；库存与扫码为占位页；
- 酱汁库：列表、详情、创建/编辑、草稿版本、发布、已发布不可变、组成行映射、Room Repository 全部落地；
- 食材库：统一到同一套列表/详情/编辑架构，补齐分类、默认单位、换算展示、归档与 UI 测试；
- 菜谱库：列表、详情、创建/编辑、草稿与发布、配方展开、子配方版本固定、`servings` 基准份数、可选配料标记；
- `SAUCE` 组成行从 `CUL-REP-004` 变为可映射的 `ComponentTarget.Sauce`，菜谱可引用已发布酱汁并固定版本；
- 所有档案写操作位于 `TransactionRunner.write { }` 内；新增事务边界回归测试。

验证结果：**344 例唯一 JVM 测试 / 0 失败**（基线 274，新增 70）；`assembleDebug` 成功；
`ktlintCheck`、`detekt` 全仓通过。未修改 Schema、Entity、表结构、迁移、数据库文件、备份/恢复实现，
未新建模块，未开发库存/扫码业务，未接线计算记录业务。

---

## 2. 启动前置检查与决策状态

### 2.1 前置检查结果

| # | 检查项 | 结果 |
|---:|---|---|
| 1 | `git status --porcelain` 为空；记录 HEAD | **PASS**：空；`b7f293870dfa921447b93a6718d2b21c818942ea` |
| 2 | HEAD 等于/后继 Schema 关闭基线 | **PASS**：关闭标签解析 = HEAD = `b7f2938`；`6a71f68` 为其祖先 |
| 3 | S0-05 / MB-S0-07 / README 决策状态 | **PASS**：12 项 ACCEPTED；MB-D-008/009 为 ACCEPTED |
| 4 | Schema v2 / 33 表 / servings / calculation_record | **PASS**：`2.json` version=2，33 实体；`recipe_version.servings` 存在；`calculation_record` / `calculation_record_line` 存在；`1.json`、`2.json` 均在 |
| 5 | 模块集 13；`feature:scan` 不存在 | **PASS**：`app` + 6 core + 6 feature；`feature/scan` 不存在 |
| 6 | MB-D-007 仍 PROPOSED；备份/恢复未修改 | **PASS**：状态未变；本次 diff 对 `core/data/.../backup` 与 `core/platform/.../work` 为空 |
| 7 | 任一不符即停止 | 未触发 |

### 2.2 决策状态（2026-11-04）

| 状态 | 决策 |
|---|---|
| `ACCEPTED`（12） | MB-D-001、002、003、005、008、009、013、014、015、016、017、018 |
| `PROPOSED`（6） | MB-D-004、006、007、010、011、012 |

本阶段未把任何 `PROPOSED` 写成 `ACCEPTED`，未替负责人签收。

---

## 3. 执行前 Git 状态与基线标签解析

| 项 | 值 |
|---|---|
| 分支 | `misebase/main` |
| 执行前 HEAD | `b7f293870dfa921447b93a6718d2b21c818942ea` |
| HEAD 提交信息 | `docs(schema): record schema close commit` |
| 工作树 | 空 |
| `checkpoint/misebase-schema-change-closed-20261104` 解析提交 | `b7f293870dfa921447b93a6718d2b21c818942ea` |
| 关闭记录 MB-SC-CLOSE-001 §3.1 关闭提交 | `6a71f685b4d1d0b89964319751c6837e16a7adb8`（HEAD 的祖先） |
| `framework-0.1.0` | `2b523de4b87e37a2eb08b4c7c851549350ad811a`，未移动 |
| Stage 3 起点 | `b7f2938`（与关闭标签解析一致） |

---

## 4. 设计系统扩展

按 MB-D-008 A 方向扩展 `core:designsystem`，详见 [MB-S3-05](MB-S3-05-DESIGN-SYSTEM.md)。

- `Color.kt`：完整亮/暗色板与语义色（success/warning/danger/info），色值与 UI 设计书 1.0.0 §3.1 一致；
- `Theme.kt`：Material 3 全槽位色板 + 语义色 CompositionLocal + Typography/Shapes；
- `Type.kt`：display 32/40、headline 24/32、title 20/28、subtitle 16/24、body 14/22、label 12/16、caption 11/16；数量用等宽 `tnum`；
- `Shape.kt` / `Dimens.kt`：4dp 栅格、克制圆角、48dp 触控目标；
- `CulinaryComponents.kt`：SectionHeader、ArchiveListItem、StatusBadge、QuantityText、EmptyState、ErrorBanner、LoadingRows、FormSection、ConfirmDialog 等；
- `CulinaryIcons.kt`：五个一级入口 + 设置/新建/返回/编辑/勾选/箭头线性图标，不引入第三方图标库；
- `DesignSystemTokensTest`：9 例，覆盖色值、WCAG 对比度 ≥4.5、字阶、4dp 栅格、圆角。

---

## 5. 导航结构

按 MB-D-009 N1 重建，详见 [MB-S3-06](MB-S3-06-NAVIGATION.md)。

- 底部五入口顺序固定：**酱汁 → 食材 → 菜谱 → 库存 → 扫码**；
- 顶栏：页面标题 + 设置图标；详情/编辑页显示返回；
- 库存、扫码、设置为占位内容（不展示假数据，不提供业务操作）；
- 启动页为 N1 第一个入口「酱汁」；未新增独立首页（UI-07 仍待确认）；
- 一级入口使用 `saveState/restoreState` 保留栈状态；详情/编辑为独立路由。

---

## 6. 酱汁库实现

- 领域：`SauceRepository` 扩展（读取、版本列表、当前版本、版本号、保存、草稿、发布）；
  `ListSaucesUseCase`、`GetSauceDetailUseCase`、`SaveSauceUseCase`、`SaveSauceDraftUseCase`、
  `PublishSauceVersionUseCase`；
- 数据：`SauceMapper` + `SauceRepositoryImpl`，只使用既有 `sauce` / `sauce_version` / `sauce_component`；
- 页面：列表（名称/分类/状态）、详情（当前版本、组成、版本历史、发布草稿）、编辑器（名称/分类/产量/单位/组成/可选）；
- 版本：草稿→发布切换当前版本；已发布内容由数据库触发器保护，重复发布返回 `CUL-CON-001`；
- 测试：领域 6、Repository 5、ViewModel 6、Compose UI 3。

详见 [MB-S3-02](MB-S3-02-ARCHIVE-PAGES.md) 酱汁章节。

---

## 7. 食材库实现

- 领域：`Ingredient` 增加 `category`/`description`；`SaveIngredientUseCase` 增加分类/描述校验；
  `GetIngredientDetailUseCase` 解析默认单位与食材级换算；
- 数据：`IngredientMapper` 映射分类/描述；
- 页面：列表（分类 + 默认单位）、详情（分类、单位、描述、换算、归档）、编辑器（名称/分类/单位/描述）；
- 测试：领域 +2（分类/描述/超长拒绝）、ViewModel 5、Compose UI 3；既有错误文案测试保留。

详见 [MB-S3-02](MB-S3-02-ARCHIVE-PAGES.md) 食材章节。

---

## 8. 菜谱库实现

- 领域：`RecipeRepository` 扩展；`RecipeDetail` / `ExpandedRecipe` 投影；
  `RecipeDraftResolver`（当前版本解析、子图预取、循环校验）、`RecipeInputValidator`；
  `SaveRecipeUseCase`、`SaveRecipeDraftUseCase`、`PublishRecipeDraftUseCase`、
  `PublishStoredRecipeVersionUseCase`、`GetRecipeDetailUseCase`、`ExpandRecipeUseCase`；
- 数据：`RecipeMapper` + `RecipeRepositoryImpl`，支持食材/子菜谱/子酱汁三类组成行；
- 页面：列表、详情（当前版本、组成、展开、版本历史）、编辑器（名称/分类/产量/单位/基准份数/组成/可选）；
- 草稿与发布：草稿可引用子菜谱「当前版本」，保存/发布时固定为明确版本 ID；
  发布前做循环校验，失败返回 `CUL-REP-001`，不写入半成品；
- 展开：按目标份数 ÷ 基准份数缩放；可选配料显式包含并标记，供详情展示（缺口口径仍属 Stage 4）；
- 测试：领域 6、Repository 4、ViewModel 8、Compose UI 3。

详见 [MB-S3-02](MB-S3-02-ARCHIVE-PAGES.md) 菜谱章节。

---

## 9. `SAUCE` 映射边界处理

详见 [MB-S3-08](MB-S3-08-SAUCE-MAPPING.md)。

- 领域新增 `ComponentTarget.Sauce(sauceId, versionId)` 与 `ComponentTargetDraft.SaucePinned`；
- `RecipeMapper` 的 `ComponentTypeDb.SAUCE` 从返回 `CUL-REP-004` 改为映射 `ComponentTarget.Sauce`；
- `RecipeRepositoryImpl` 写入时落 `referenced_sauce_id` / `referenced_sauce_version_id`；
- 菜谱编辑器新增「添加酱汁」，选择时固定当前已发布版本；
- 详情页展示酱汁名称与版本号；
- 展开遇到酱汁目标仍显式返回 `CUL-REP-004`（酱汁图展开属 Stage 4），不静默少算；
- 未改表结构、Entity 或迁移。

---

## 10. Repository 事务边界

详见 [MB-S3-07](MB-S3-07-REPOSITORY-TRANSACTION-BOUNDARY.md)。

- 全部档案写用例经 `TransactionRunner.write { }`：食材保存/归档、酱汁档案/草稿/发布、菜谱档案/草稿/发布；
- Repository 方法只做原子片段，不自行开事务；
- 酱汁草稿的版本号计算移入事务块，避免并发下重复版本号；
- `TransactionBoundaryTest` 4 例：成功路径确实经过 Runner；Runner 失败时三类档案均无部分写入。

---

## 11. 测试与构建结果

| 项 | 结果 |
|---|---|
| 唯一 JVM 测试 | **344**（基线 274，+70），0 失败 |
| 分布 | app 12 / common 9 / domain 118 / database 81 / data 65 / designsystem 9 / platform 19 / ingredients 11 / recipes 11 / sauces 9 |
| `assembleDebug` | **成功**；`app/build/outputs/apk/debug/app-debug.apk` |
| `ktlintCheck` | 通过 |
| `detekt` | 通过 |
| 模块依赖检查 | PASS（13 模块，DAG） |
| 文档一致性 | PASS |
| 休眠表扫描 | 3 处**基线既有**误报（详见 §13），本阶段新增 0 |

测试矩阵见 [MB-S3-03](MB-S3-03-TEST-MATRIX.md)。

必须执行的命令已在最终验证中运行：

```powershell
.\gradlew.bat test assembleDebug ktlintCheck detekt --console=plain --no-daemon
```

---

## 12. Git 提交 / 检查点 / 变更范围

### 12.1 分提交

| 顺序 | 提交 | 内容 |
|---:|---|---|
| 1 | `4a29cd5` | `feat(designsystem): expand visual direction A tokens and components` |
| 2 | `4594ac7` | `feat(app): rebuild navigation shell for N1 five-entry bottom bar` |
| 3 | `9097ecf` | `feat(sauces): implement sauce library UI, versions and Room repository` |
| 4 | `f0aff7c` | `feat(ingredients): unify archive screens with category and unit management` |
| 5 | `055706d` | `feat(recipes): implement recipe library with draft, publish, expansion and servings` |
| 6 | `0974f75` | `feat(recipes): map SAUCE components and allow recipes to reference sauces` |
| 7 | `d72a4ae` | `test(domain): enforce transaction boundaries for archive writes` |
| 8 | `d6dbb06` | `test(ui): add Compose coverage for navigation and three archives` |
| 9 | `b314a9a` | `test(ui): run Compose tests only on the debug variant` |
| 10 | `0779c04` | `docs(stage3): record stage 3 execution report and index`（检查点解析提交） |

### 12.2 范围统计（`b7f2938..HEAD`，不含文档提交）

代码与测试提交统计（截至 `d6dbb06`）：

| 项 | 值 |
|---|---|
| 变更文件 | 113（新增 72 / 修改 37 / 删除 3 / 重命名 1） |
| 设计系统 | 8 |
| app | 8 |
| core:domain | 38 |
| core:data | 13 |
| core:database（仅 DAO） | 6 |
| feature:sauces | 12 |
| feature:ingredients | 14（3 个旧 UI 文件删除 + 1 处重命名） |
| feature:recipes | 13 |
| config | 1（detekt 规则补充） |

另：4 个 Compose UI 测试文件从 `src/test` 迁移到 `src/testDebug`（内容不变，`b314a9a`）；
文档提交新增 `docs/09-stage3/` 与索引更新。最终 `git diff --name-status b7f2938..HEAD` 以交付回执为准。

删除的文件仅为被替换的旧食材 UI：`IngredientsScreen.kt`、`IngredientsUiState.kt`、`IngredientsViewModel.kt`；
未删除模块、未删除表、未修改 Entity。

### 12.3 边界核对

| 边界 | 证据 |
|---|---|
| Schema / Entity / 迁移 / 数据库文件 | `git diff --name-status base..HEAD -- core/database/schemas .../entity .../CulinaryMigrations.kt .../CulinaryDatabase.kt` 为空 |
| 备份/恢复 | `git diff ... -- core/data/.../backup core/platform/.../work` 为空 |
| 模块集 | 13；`feature/scan` 不存在；settings 未增删 |
| 休眠表 | 11 张结构未动；新业务代码引用 0 |
| 计算记录 | 未新增/接线业务 UI 或 Use Case |
| 库存/扫码 | 仅占位页 |
| 技术栈 | 未升级 Room/Hilt/Kotlin/Gradle/JDK |

### 12.4 检查点

| 项 | 值 |
|---|---|
| 标签 | `checkpoint/misebase-stage3-20261104` |
| 类型 | annotated tag |
| 对象哈希 | `531b5f1cf596e90eaaec14b175d593aa1427eaca` |
| 解析提交 | `0779c0470349d717e9c0fd1eaf6587168aea42dd` |
| 回执 | [MB-S3-09 检查点回执](MB-S3-09-CHECKPOINT-RECEIPT.md) |
| 既有标签 | `framework-0.1.0` 与全部既有 `checkpoint/*` / `baseline/*` 未移动 |

---

## 13. 未解决问题与风险

| # | 项 | 影响 | 处置 |
|---:|---|---|---|
| 1 | MB-D-007 仍 `PROPOSED` | 备份一致性未实现/未验收 | 未改备份/恢复；不声称一致备份；Stage 5 前签收 |
| 2 | `check-dormant-tables.ps1` 仍报 3 处 `purchase_order` | 均为 Stage 2 既有注释/测试文本（`PurchaseList.kt`、`PurchaseListBuilder.kt`、`PurchaseListTest.kt`），本阶段未修改这些文件 | 新增业务引用 0；脚本口径清理留后续 |
| 3 | 酱汁编辑器只支持食材组成行 | 若未来导入含子菜谱/子酱汁的酱汁版本，编辑保存会忽略这些行 | 当前无导入路径；Stage 4 前补编辑或只读保护 |
| 4 | 含酱汁目标的配方展开返回 `CUL-REP-004` | 展开预览不可用（不是静默少算） | 酱汁图展开属 Stage 4 |
| 5 | 未做真机验证（MB-D-011） | CameraX/SAF/恢复未验证 | 本阶段不涉及；Stage 6 前补 |
| 6 | Compose UI 测试仅在 debug 变体的 Robolectric 下运行；androidTest 未运行 | 无设备证据 | 关键交互已有 ViewModel 与 UI 测试；真机留 Stage 6 |
| 7 | README 仍描述 Stage 2 / Schema v1 | 文档滞后 | 本阶段只交付 Stage 3 文档与索引；未扩大授权改 README |
| 8 | UI-07「是否保留首页」未决 | 当前以「酱汁」为启动页，无独立首页 | 符合 N1 五入口；如需首页须负责人另定 |
| 9 | 发布签名/CI（MB-D-012/010）未签收 | Release 门禁未完成 | 不阻塞本阶段 |
| 10 | 部分 Compose 页面仅覆盖关键路径 | 边界视觉/大字体未逐项验收 | 留 Stage 6 视觉验收 |

---

## 14. 是否具备 Stage 3 审查条件

**具备审查条件**，建议按以下范围审查：

1. 三大档案库页面与版本/发布/展开行为；
2. 设计系统 token 与组件是否符合 MB-D-008 A；
3. 导航是否符合 MB-D-009 N1（五入口、设置顶栏、库存/扫码占位）；
4. `SAUCE` 映射与事务边界；
5. 边界证据：无 Schema/Entity/迁移/备份变更、无新模块、无库存/扫码/计算记录业务。

审查通过前，Stage 3 状态保持 `Review`，不自动关闭。

---

## 15. 停止声明

本阶段在提交报告、创建检查点标签后停止。

**未进入 Stage 4。**

---

## 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0.0 | 2026-11-04 | Stage 3 执行报告（Review）；记录实现、测试、Git 与边界 | 接管工程组 |
