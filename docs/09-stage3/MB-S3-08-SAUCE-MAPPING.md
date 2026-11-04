# MB-S3-08 `SAUCE` 组成行映射边界处理

**文档编号**：MB-S3-08
**版本**：`1.0.0`
**状态**：`Review`
**日期**：2026-11-04

---

## 1. 问题

Schema v2 的 `recipe_component.component_type` 支持 `INGREDIENT / SAUCE / RECIPE`，
但 Stage 2 的领域模型只有 `ComponentTarget.Ingredient` 与 `ComponentTarget.Recipe`，
`RecipeMapper` 遇到 `SAUCE` 行返回 `CUL-REP-004`。Stage 3 要求菜谱可引用酱汁。

## 2. 处理

| 层 | 变更 |
|---|---|
| 领域模型 | 新增 `ComponentTarget.Sauce(sauceId, versionId)`；`SauceVersion` 增加 `childSauceVersions()` |
| 领域草稿 | 新增 `ComponentTargetDraft.SaucePinned(sauceId, versionId)` |
| 菜谱输入 | 新增 `RecipeLineTarget.SaucePinned`，解析器映射为 `ComponentTargetDraft.SaucePinned` |
| 映射 | `RecipeMapper` 的 `ComponentTypeDb.SAUCE` 读取 `referenced_sauce_id` + `referenced_sauce_version_id`，映射为 `ComponentTarget.Sauce`；缺 id 才返回 `CUL-REP-004` |
| 写入 | `RecipeRepositoryImpl.toComponentEntity` 写 `referenced_sauce_id` / `referenced_sauce_version_id` |
| 详情 | `GetRecipeDetailUseCase` 通过 `SauceRepository` 解析酱汁名与版本号 |
| 编辑器 | 「添加酱汁」选择当前已发布版本，固定 `sauce_version_id` |
| 展开 | `RecipeExploder` 遇到 `ComponentTarget.Sauce` 显式返回 `CUL-REP-004`（酱汁图展开属 Stage 4），不静默少算 |
| 数据库 | 未修改 Entity/表结构/迁移；只使用既有列与触发器 |

## 3. 行为

- 菜谱可引用任意在用酱汁的当前已发布版本；
- 引用写入后固定版本 ID，不随酱汁新版本漂移（与 MB-D-013 一致）；
- 详情展示「子酱汁 vN」；
- 含酱汁目标的配方展开明确失败，错误信息指出 `SAUCE` 目标不受支持。

## 4. 测试

| 测试 | 断言 |
|---|---|
| `RecipeRepositoryTest.sauce component maps to a pinned sauce target` | Room 往返保留 `sauceId` / `versionId` |
| `RecipeUseCasesTest.expansion rejects a sauce target with an explicit domain error` | `CUL-REP-004` |
| `RecipesViewModelTest.editor references a sauce with a pinned sauce version` | 编辑器发布后组成目标为 `ComponentTarget.Sauce`，版本固定 |
| `RecipesUiTest.detail ...` | 详情组成行渲染 |

## 5. 未做

- 酱汁嵌套酱汁的编辑入口；
- 酱汁图展开/需求汇总（Stage 4）；
- 表结构或迁移变更。
