# MB-UI-ARCHIVE-UNIFIED-001 第一阶段审查

日期：2026-11-09  
范围：六大档案库分类、详情、目录与搜索  
状态：**仅审查，未修改代码**

---

## 1. 审查结论

当前六库的“分类”不是分类体系，而是各表上的单个字符串字段；目录是单层筛选列表；搜索是“当前库简单字段搜索 + 厨房跨六库全局搜索”；详情页各自存在，但仍是旧版表单式布局。

要完成本任务，**必须新增分类树与档案—分类关联结构**。这不是可选优化。

现有可复用基础较好：

- 六库已有统一只读投影 `KitchenArchiveReader`；
- 六库已有统一页面入口 `ArchiveLibraryRoute`；
- 已有 `kitchen_relation` 做档案关系，不应复用于分类；
- 已有独立详情路由和 ViewModel；
- 酱汁编辑器已完成，必须只改分类区域，不重做编辑器；
- 已有 `image_asset` 表与 `SauceImageUseCase`，可扩展为通用档案图片读取。

---

## 2. 当前分类数据

### 2.1 数据库

当前 schema 为 **v5**。没有分类表，也没有父子关系。

分类直接存在档案表：

| 档案 | 当前分类字段 | 类型 | 是否单级 |
|---|---|---|---|
| 酱汁 | `sauce.family` | `TEXT` | 单级 |
| 食材 | `ingredient.category` | `TEXT` | 单级 |
| 菜谱 | `recipe.category` | 枚举 `RecipeCategoryDb` | 单级 |
| 烹饪技术 | `kitchen_reference.category` | `TEXT` | 单级 |
| 制作标准 | `kitchen_reference.category` | `TEXT` | 单级 |
| 储存标准 | `kitchen_reference.category` | `TEXT` | 单级 |

`kitchen_reference` 用 `kind` 区分技术 / 制作标准 / 储存标准，但分类字段本身不区分层级，也没有所属库字段。

### 2.2 当前真实分类值

来自种子数据与当前数据库：

酱汁（7 个单级值）：

```text
基础母酱（3）
乳化酱汁（2）
蛋黄乳化酱（1）
棕色基础酱汁（1）
冷制酱汁（1）
冷制调味汁（1）
复合酱汁（1）
```

食材（5 个单级值）：

```text
乳制品与蛋类（5）
蔬菜与水果（5）
肉类与水产（4）
干货与调味（3）
香草与香料（1）
```

菜谱（2 个枚举值）：

```text
DISH（8）
COMPONENT（2）
```

参考资料（每类 10 个单级值，共 30）：

```text
烹饪技术：酱汁技术、温度控制、基础烹饪技术、热加工、浓缩技术、湿热烹饪、
          冷加工、熟度控制、质地控制、配方管理
制作标准：操作卫生、配方管理、过程控制、食品安全、原料处理、质量控制、
          过敏原管理、追溯管理、设备管理、档案管理
储存标准：冷藏、冷冻、常温干燥、避光常温、果蔬分区、熟制半成品、
          冷制半成品、交叉污染控制、开封管理、库存周转
```

结论：**现有数据只有一层**。任务要求的三级/更多级在当前数据中没有样本。

### 2.3 缺失

- 无 `archive_category` 一类分类实体；
- 无 `parent_id` 父子关系；
- 无分类所属库字段；
- 无分类排序字段；
- 无档案—分类节点关联；
- 无分类路径查询；
- 无分类数量统计；
- 无分类选择器；
- 无分类目录页面；
- 无分类路径搜索结果显示。

---

## 3. 当前目录与搜索

### 3.1 六库目录

当前入口：

```text
app/kitchen/archive/ArchiveLibraryScreen.kt
app/kitchen/archive/ArchiveLibraryViewModel.kt
app/kitchen/archive/KitchenArchiveSection.kt
core/data/kitchen/KitchenArchiveReader.kt
core/data/kitchen/DefaultKitchenArchiveReader.kt
```

现状：

- 六个库共用一个 `ArchiveLibraryScreen`。
- 页面结构为：标题 → 常驻搜索框 → “分类 + 新增”行 → 单层分类筛选胶囊 → 扁平档案行 → “显示 N / N”。
- 六库没有分类层级，只有单层筛选。
- 菜谱分类键在页面内硬编码：

```kotlin
RECIPE_CATEGORY_KEYS = listOf("STOCK", "PREP", "COMPONENT", "DISH", "OTHER")
```

- 其他库的分类值从当前条目 `category` 字符串临时去重，不是真实分类表。
- 没有分类节点与档案实体的区分；分类只是筛选条件。
- 没有“下级分类”与“当前分类档案”两个分区。
- 没有分类页钻取；没有上一级返回。
- 没有分页；只有 `显示 filtered / total`，没有加载更多。
- 分类数量不统计子分类档案，也没有“直接档案数 / 子分类数”的区分。
- 无法表示“同一分类既有子分类又有档案”。

### 3.2 搜索

现状有两套：

1. `ArchiveLibraryScreen` 内常驻搜索框：单库扁平搜索，结果不显示完整分类路径，不支持别名，不支持搜索状态返回恢复。
2. `KitchenGlobalSearchPanel`：厨房首页的跨六库搜索，显示类型 + 单层分类，点击进入详情。

与本任务冲突点：

- 任务要求“当前档案库全部档案，不跨档案库”，当前厨房搜索是跨库的。
- 任务要求默认只显示右上角搜索图标，点击滑出；当前库页是常驻输入框，厨房页是跨库覆盖面板。
- 任务要求结果显示完整分类路径；当前只显示单层 category 文本。
- 任务要求从详情返回恢复搜索结果；当前跨库面板关闭后状态不保证保留。
- 任务要求支持别名、编码；当前 `KitchenArchiveEntry` 只有 name/subtitle/category/searchText，未纳入 `ingredient_alias`，也没有统一编码字段。

### 3.3 当前导航

- 六库通过 `LIBRARY_TAB_ROUTE` + `section` 参数进入。
- 库页上方仍使用全局 `AppTopBar`（返回 + 厨房），页面内再显示库标题。
- 分类页/分类路径没有独立路由。
- 详情页使用既有 `sauces/{id}`、`ingredients/{id}`、`recipes/{id}`、`kitchen-reference/...` 路由。
- 底部导航当前为：首页 / 厨房 / 扫码 / 库存 / 我的；任务书写“知识库”，但不得修改现有底栏，需按当前代码保留“厨房”。

---

## 4. 当前详情页

### 4.1 酱汁

`feature/sauces/SauceDetailScreen.kt`

- 存在独立详情页，但仍是旧版：
  - `PageHeader`；
  - `SauceProfileSection` 平铺 label/value；
  - `CurrentVersionSection`；
  - `DraftVersionSection`；
  - 两个 `CulinaryButton`（编辑 / 发布）；
  - 组成行；
  - 关联；
  - 版本历史。
- 没有成品图、步骤、适用范围、研发备注、分类路径。
- 有 `SauceImageUseCase`，但详情页没有读取图片。
- 当前发布/草稿逻辑已具备，必须保留。

### 4.2 食材

`feature/ingredients/IngredientDetailScreen.kt`

- 独立详情页；
- 展示默认单位、描述、英文名、示例商品、储存、过敏原、换算、关联；
- 无图片读取；
- 无分类路径；
- 使用 `PageHeader` 和 `CulinaryButton`，旧版表单式布局。

### 4.3 菜谱

`feature/recipes/RecipeDetailScreen.kt`

- 独立详情页；
- 展示当前版本、组成、按份数展开、版本历史、关联；
- 无分类路径、图片、适用范围；
- `Recipe` 领域模型只保留 name/category/englishName/allergen/ingredientSummary/seedNote；
- 数据库 `recipe` 还有 `cuisine`、`description`、`prep_minutes`、`cook_minutes`、`difficulty`，但 **没有映射到领域模型**，详情页无法展示这些已有字段。

### 4.4 烹饪技术 / 制作标准 / 储存标准

`app/kitchen/archive/KitchenReferenceScreens.kt`

- 三类共用同一个详情页；
- 数据模型只有 `name`、`category`、`description`、`englishName`、`seedNote`；
- 技术原理、操作步骤、适用范围、标准要求、温度、期限等都拼在 `description` 里，没有独立字段；
- 无分类路径、图片；
- 旧版 `PageHeader` + 大按钮布局。

### 4.5 详情页顶栏

当前非首页路由仍使用全局 `AppTopBar`，只显示返回图标，没有“上一级名称”。详情页内部再使用 `PageHeader`。

任务要求“返回箭头 + 浅灰上一级名称（酱汁库/搜索结果）”，并让详情页独立阅读。最小实现需要：

- 对六库目录和六类详情路由隐藏全局 `AppTopBar`，由页面自己绘制顶栏；
- 或扩展 `AppTopBar` 支持上下文标题。

这是 app 外壳的必要路由条件，不是全局导航重构。

---

## 5. 可复用结构

| 结构 | 复用方式 |
|---|---|
| `KitchenArchiveReader` / `KitchenArchiveEntry` | 扩展为分类路径、分类 id、别名搜索文本 |
| `ArchiveLibraryViewModel` | 改为分类树 + 当前分类档案 Flow |
| `ArchiveLibrarySection` / 路由 | 保留六库入口，增加分类路径状态 |
| `KitchenReferenceStore` | 继续服务三类参考资料详情/编辑 |
| `SauceImageUseCase` / `image_asset` | 提取通用档案图片读取；或仅酱汁详情先接入 |
| `CulinaryRelationSection` | 详情页关联区继续复用 |
| `CulinaryRule` / `CulinaryTextStyles` / `CulinarySpacing` | 统一阅读视觉 |
| 酱汁编辑器的九段结构 | 只在分类区域替换，不重做其他段落 |
| `ArchiveCategory` 新结构 | 新增，不复用 `kitchen_relation`，避免分类与档案关系混用 |

---

## 6. 必要的数据结构调整

### 方案 A（推荐）：分类表 + 档案分类关联表

新增两张表，不改六张档案表的既有分类列：

```text
archive_category
  id TEXT PK
  library TEXT NOT NULL
  parent_id TEXT NULL -> archive_category(id)
  name TEXT NOT NULL
  normalized_name TEXT NOT NULL
  english_name TEXT NULL
  sort_order INTEGER NOT NULL DEFAULT 0
  status TEXT NOT NULL
  created_at INTEGER NOT NULL
  updated_at INTEGER NOT NULL
```

```text
archive_category_assignment
  id TEXT PK
  record_kind TEXT NOT NULL
  record_id TEXT NOT NULL
  category_id TEXT NOT NULL -> archive_category(id)
  created_at INTEGER NOT NULL
  UNIQUE(record_kind, record_id)
```

优点：

- 分类与档案严格分离；
- 一个分类可同时有子分类和档案；
- 六库共用结构但 `library` 隔离；
- 不需要给 `sauce` / `ingredient` / `recipe` / `kitchen_reference` 各加一列；
- 与现有 `kitchen_relation` 的多态 kind 设计一致；
- 可扩展到更多档案库。

缺点：

- 分类路径查询需要 join；
- 档案存在性不由外键强制（record_id 多态），由 UseCase 保证。

### 方案 B：分类表 + 四个 `category_id` 列

在 `sauce`、`ingredient`、`recipe`、`kitchen_reference` 各加 `category_id`。

优点：join 简单，外键强。  
缺点：四张表都要迁移，未来新增档案库还要加列；与“class node separate”仍兼容，但扩展性较弱。

**建议采用 A。**

### 6.1 旧分类字符串

保留现有 `family/category` 字段作为兼容/回退显示，不作为分类树权威来源：

- 迁移时把现有单级值建成各库的一级分类节点；
- 给每条记录写 `archive_category_assignment`；
- 保存时写 assignment；
- 旧字段继续写叶子名称，便于旧详情/旧代码回退；
- 读取以 assignment + category path 为准。

### 6.2 分类层级与现有数据

现有数据只有单级，不能凭空产生三级路径。需要确认采用哪种方式：

1. **只迁移真实单级值**：动态结构已具备，但最终 APK 只有一级分类，无法现场演示二/三级；三级能力只能由测试数据验证。
2. **批准一份最小分类树种子**：把现有值按语义挂到 2–3 级节点下（例如基础酱汁 → 基础母酱 → ...）。这会新增分类关系，但不删除/改写档案。
3. **增加分类管理入口**：任务未授权，不应自行增加。

本报告建议：先按 1 迁移，另附测试分类树验证动态能力；如果验收必须在正式 APK 中看到三级，需批准 2 的最小分类树种子。

---

## 7. 必要修改文件（待确认，尚未修改）

### 7.1 分类数据层

```text
core/database/.../entity/ArchiveCategoryEntity.kt                 新增
core/database/.../entity/ArchiveCategoryAssignmentEntity.kt      新增
core/database/.../dao/ArchiveCategoryDao.kt                      新增
core/database/.../dao/ArchiveCategoryAssignmentDao.kt            新增
core/database/.../CulinaryDatabase.kt                            v6 + entities
core/database/.../CulinaryMigrations.kt                          MIGRATION_5_6
core/database/schemas/.../6.json                                 新增
core/database/src/test/.../CulinaryMigrationV5ToV6Test.kt         新增
core/database/src/androidTest/.../CulinaryDatabaseMigrationTest.kt
```

### 7.2 领域 / 数据层

```text
core/domain/.../model/ArchiveCategory.kt                         新增（含路径、层级、所属库）
core/domain/.../repository/ArchiveCategoryRepository.kt           新增
core/domain/.../usecase/archive/ListArchiveCategoriesUseCase.kt    新增
core/domain/.../usecase/archive/GetArchiveCategoryPathUseCase.kt   新增
core/domain/.../usecase/archive/AssignArchiveCategoryUseCase.kt    新增
core/data/.../mapper/ArchiveCategoryMapper.kt                     新增
core/data/.../repository/ArchiveCategoryRepositoryImpl.kt         新增
core/data/.../di/ArchiveDaoModule.kt / RepositoryModule.kt
core/data/.../kitchen/KitchenArchiveEntry.kt                      + categoryId/path/aliases
core/data/.../kitchen/DefaultKitchenArchiveReader.kt              组合分类路径、别名
```

保存分类关联需要接入：

```text
SaveSauceUseCase / SaveIngredientUseCase / SaveRecipeUseCase
SaveKitchenReferenceUseCase
```

只在档案保存后写 association；不改变版本/发布语义。

### 7.3 目录 / 搜索

```text
app/.../kitchen/archive/ArchiveLibraryScreen.kt                   分类目录 / 实体列表 / 分页
app/.../kitchen/archive/ArchiveLibraryViewModel.kt                分类树 + 当前分类档案 + 搜索
app/.../kitchen/archive/KitchenArchiveSection.kt                  分类上下文
app/.../kitchen/KitchenScreen.kt                                  只保留入口，不重复跨库搜索
app/.../kitchen/KitchenGlobalSearchPanel.kt                       改为单库搜索或移出本任务
app/.../navigation/CulinaryNavHost.kt                             目录/详情上下文顶栏与返回
app/src/main/res/values/strings.xml
```

### 7.4 六类详情

```text
feature/sauces/.../SauceDetailScreen.kt
feature/sauces/.../SauceDetailViewModel.kt
feature/ingredients/.../IngredientDetailScreen.kt
feature/ingredients/.../IngredientDetailViewModel.kt
feature/recipes/.../RecipeDetailScreen.kt
feature/recipes/.../RecipeDetailViewModel.kt
app/.../kitchen/archive/KitchenReferenceScreens.kt
app/.../kitchen/archive/KitchenReferenceDetailViewModel.kt
core/designsystem/.../component/ArchiveReading*.kt                新增共用阅读组件
```

### 7.5 可选但会影响验收

- 通用档案图片读取：`image_asset` 的 `ImageOwnerTypeDb` 已有 `INGREDIENT` / `RECIPE` / `RECIPE_VERSION`，但当前只有酱汁图片读取用例；**没有 `KITCHEN_REFERENCE` 归属类型**。若不补通用读取，食材/菜谱详情只能不显示图片；参考资料详情在当前枚举下没有图片归属，需决定是否新增枚举或明确显示“无图片”。
- 食材别名搜索：`ingredient_alias` 表存在，但没有读取入口。需要新增只读 alias 查询。
- 菜谱已有数据库字段 `cuisine/description/prep_minutes/cook_minutes/difficulty`：需要补充领域映射，否则详情无法展示真实字段。
- 参考资料的结构化字段：现只有 `description`，如果要展示独立“原理/步骤/范围/要求/温度/期限”，需要新增字段；否则只能以说明段落展示。

---

## 8. 数据库迁移计划（建议）

### v5 → v6

1. 创建 `archive_category`；
2. 创建 `archive_category_assignment`；
3. 从现有 `sauce.family`、`ingredient.category`、`recipe.category`、`kitchen_reference.category` 去重建一级分类；
4. 为每条档案写 assignment；
5. 保留旧分类字符串；
6. 不删除/清空任何档案、版本、关系、图片；
7. schema 6 导出；
8. migration test：v5 数据、分类节点、assignment、旧字段保留；
9. 迁移失败仅回滚事务，不自动清库。

分类 `library` 枚举：

```text
SAUCE
INGREDIENT
RECIPE
TECHNIQUE
PRODUCTION_STANDARD
STORAGE_STANDARD
```

### 6.1 分类唯一性

建议 `(library, parent_id, normalized_name)` 唯一；根节点的 `parent_id` 为 NULL。SQLite 的 NULL 唯一语义需在 DAO/UseCase 层补校验，或用 expression index `COALESCE(parent_id,'')`。

### 6.2 分类排序

`sort_order` 同父节点内排序；
不允许按字母排序替代专业顺序。

### 6.3 分类数量

- `directRecordCount`：assignment 直接指向当前节点；
- `descendantRecordCount`：当前节点及所有后代；
- 目录行显示时明确文案，不混淆两者。

---

## 9. 风险与保护

1. **三级分类无真实数据**  
   当前生产数据只有单级。若不批准分类树种子或分类管理入口，只能在测试中证明动态深度，正式 APK 没有三级样本。这是本任务最大阻塞点。

2. **跨库搜索冲突**  
   现有厨房全局搜索跨六库，任务要求不跨库。必须替换/限制，不能同时保留两套冲突语义。

3. **详情页字段缺失**  
   菜谱数据库字段未映射到领域；参考资料只有 description；食材/菜谱/参考图片没有读取链路。不能为了“看起来完整”伪造字段。

4. **全局顶栏**  
   详情页要显示上一级名称，当前 `AppTopBar` 不支持上下文；需对六库目录/详情路由做局部顶栏切换，不能改底栏。

5. **旧分类列与新分类表双写**  
   迁移期要明确权威来源；建议 assignment 为权威，旧字段只做回退，保存时双写叶子名称。

6. **图片备份边界**  
   上一任务已确认图片不进入现有备份 ZIP；本任务若使用通用图片，仍需保持同一限制，除非另行授权。

7. **版本/发布语义**  
   分类只影响档案元数据，不改 `sauce_version`/`recipe_version`、草稿、发布、当前版本规则。分类保存不得触发版本变化。

---

## 10. 需要确认的最小决策

1. 采用 **方案 A（分类表 + assignment）**，还是方案 B（四张表加 category_id）？
2. 是否批准最小分类树种子，把现有单级分类挂成 2–3 级，以便正式 APK 验收三级？（如不批准，正式数据只有一级，三级仅测试证明。）
3. 现有厨房跨六库搜索是否移除/收窄为单库搜索？（任务明确“不跨档案库”。）
4. 是否补齐通用档案图片读取，用于食材/菜谱/参考详情？
5. 是否补齐菜谱已有但未映射的字段（cuisine/description/prep/cook/difficulty）？
6. 参考资料是否需要新增结构化字段，还是以现有 description 段落展示？

在以上确认前，不进入第二阶段实现。
