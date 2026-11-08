# MB-UI-ARCHIVE-UNIFIED-001 数据库设计与迁移方案

状态：**已实施（v6）**  
实施前版本：v5（`1.0.4 (5)`）  
当前版本：v6（`1.0.5 (6)`）

> 实施修订：分类仍采用 `archive_category` + `archive_category_assignment` 两张表；
> 因任务扩展授权三个参考资料库的专业字段，额外新增 `kitchen_reference_content`
> 一张内容行表。六张档案主表仍不增加分类列或专业字段列。
> 实际 schema 表数 38，分类正式节点 44，测试节点 17，测试档案 2 条。

---

## 1. 设计结论

采用已批准的：

```text
archive_category
archive_category_assignment
```

两张独立表；并额外使用：

```text
kitchen_reference_content
```

承载烹饪技术/制作标准/储存标准的专业字段与可排序条目。

- **不修改** `sauce`、`ingredient`、`recipe`、`kitchen_reference` 主表结构。
- 保留现有 `family/category` 字符串作为兼容回退值。
- 分类树与档案分离。
- 六库共用结构，通过 `library` 隔离。
- 支持任意层级、同父排序、同级分类 + 档案、不同库不同深度。
- 正式分类由现有真实分类值迁移生成。
- 测试分类单独写入 `test_batch='UI-CATEGORY-TEST-V1'`，不挂真实档案。

---

## 2. 表结构

### 2.1 `archive_category`

```sql
CREATE TABLE IF NOT EXISTS `archive_category` (
    `id` TEXT NOT NULL,
    `library` TEXT NOT NULL,
    `parent_id` TEXT,
    `name` TEXT NOT NULL,
    `normalized_name` TEXT NOT NULL,
    `english_name` TEXT,
    `sort_order` INTEGER NOT NULL DEFAULT 0,
    `status` TEXT NOT NULL DEFAULT 'ACTIVE',
    `test_batch` TEXT,
    `created_at` INTEGER NOT NULL,
    `updated_at` INTEGER NOT NULL,
    `revision` INTEGER NOT NULL DEFAULT 1,
    PRIMARY KEY(`id`),
    FOREIGN KEY(`parent_id`) REFERENCES `archive_category`(`id`)
        ON UPDATE NO ACTION ON DELETE RESTRICT
);
```

字段说明：

| 字段 | 说明 |
|---|---|
| `id` | 稳定 UUID（正式迁移用 seed 值生成确定性 UUID） |
| `library` | `SAUCE` / `INGREDIENT` / `RECIPE` / `TECHNIQUE` / `PRODUCTION_STANDARD` / `STORAGE_STANDARD` |
| `parent_id` | 父分类；NULL 为一级 |
| `name` | 中文分类名 |
| `normalized_name` | 查重/搜索规范化名 |
| `english_name` | 可选英文名；当前正式数据基本没有，保留 null |
| `sort_order` | 同父节点内排序；禁止按字母代替 |
| `status` | `ACTIVE` / `ARCHIVED` |
| `test_batch` | NULL 为正式分类；测试分类固定写入 batch id |
| `created_at` / `updated_at` | 自动时间 |
| `revision` | 更新版本 |

索引：

```sql
CREATE INDEX idx_archive_category_library_parent_sort
ON archive_category(library, parent_id, sort_order);

CREATE INDEX idx_archive_category_test_batch
ON archive_category(test_batch);
```

唯一性：

```text
同一 library、同一 parent、同一 normalized_name 不可重复。
```

Room 无表达式唯一索引；用应用层校验，并在 `CulinarySchemaExtras` 增加运行时唯一索引：

```sql
CREATE UNIQUE INDEX IF NOT EXISTS ux_archive_category_sibling_name
ON archive_category(library, COALESCE(parent_id, ''), normalized_name);
```

该索引不进入 Room 导出 schema 时，需要按现有 `CulinarySchemaExtras` 的运行时索引模式处理，避免 Room 校验不一致。

### 2.2 `archive_category_assignment`

```sql
CREATE TABLE IF NOT EXISTS `archive_category_assignment` (
    `id` TEXT NOT NULL,
    `record_kind` TEXT NOT NULL,
    `record_id` TEXT NOT NULL,
    `category_id` TEXT NOT NULL,
    `created_at` INTEGER NOT NULL,
    `updated_at` INTEGER NOT NULL,
    PRIMARY KEY(`id`),
    FOREIGN KEY(`category_id`) REFERENCES `archive_category`(`id`)
        ON UPDATE NO ACTION ON DELETE RESTRICT
);
```

约束：

```sql
CREATE UNIQUE INDEX ux_archive_category_assignment_record
ON archive_category_assignment(record_kind, record_id);

CREATE INDEX idx_archive_category_assignment_category
ON archive_category_assignment(category_id);
```

- 一个档案只能有一个分类节点。
- `record_id` 为多态 ID，不设跨表外键（与 `kitchen_relation` 一致）。
- `category_id` 外键保证不会引用不存在的分类。
- `record_kind` 与 `library` 必须匹配，由 UseCase/DAO 校验；不匹配拒绝保存。

### 2.3 `kitchen_reference_content`（实施新增）

```sql
CREATE TABLE kitchen_reference_content (
    id TEXT NOT NULL PRIMARY KEY,
    reference_id TEXT NOT NULL,
    section TEXT NOT NULL,
    label TEXT,
    value TEXT NOT NULL,
    sort_order INTEGER NOT NULL,
    FOREIGN KEY(reference_id) REFERENCES kitchen_reference(id)
        ON UPDATE NO ACTION ON DELETE CASCADE
);
```

用途：

- 单字段内容：`section` = 字段 key，`value` = 内容。
- 可重复条目：同一 `section` 多行，`label` = 标题、`value` = 说明、`sort_order` = 顺序。
- 三个库各自使用不同字段 key；不共享一套强制字段。
- 空字段不写行、不显示；已有 `description` 原样保留。

最终字段：

```text
烹饪技术：definition, principle, scope, equipment, key_control_points,
          finished_criteria, common_issues, applications
          + steps（可排序步骤）

制作标准：purpose, scope, prep_requirements, process_control,
          quality_acceptance, qualified_criteria, exception_handling
          + standards（可排序标准项目）

储存标准：storage_target, storage_method, temperature, packaging,
          location, shelf_life, label_requirements, pre_use_check,
          discard_criteria
          + checks（可排序检查项）
```

---

## 3. 完整性保护

### 3.1 同库父子约束

`parent_id` 指向的分类必须与当前分类同 `library`，且不能自引用。

在 Repository 保存分类、或迁移脚本写入时校验；必要时增加 SQLite trigger：

```text
BEFORE INSERT/UPDATE archive_category
  parent.library <> NEW.library -> ABORT
  parent.id = NEW.id -> ABORT
```

### 3.2 防止循环

分类树允许任意深度，但必须拒绝循环：

- 保存/创建分类时递归查询父链，若包含自身则拒绝。
- 迁移/test seed 使用固定树，天然无循环。
- 不在 UI 中创建分类，减少运行期风险。

### 3.3 档案与分类库一致

`archive_category_assignment` 写入前：

1. 校验 `record_id` 对应档案存在且为 ACTIVE/ARCHIVED 合法档案；
2. 校验 `record_kind` 映射到 `archive_category.library`；
3. 校验分类 `status=ACTIVE`；
4. 同一档案已有 assignment 时更新到新分类，不插入第二条。

### 3.4 删除/归档

- 分类删除采用 `ARCHIVED`，不物理删除正式分类。
- 有 assignment 的分类不能删除；必须先迁移档案。
- 测试分类清理只删除 `test_batch` 且没有 assignment 的分类。

---

## 4. 领域与查询模型

### 4.1 `ArchiveCategory`

```text
id: String
library: ArchiveLibrary
parentId: String?
name: String
englishName: String?
sortOrder: Int
status: ACTIVE / ARCHIVED
testBatch: String?
depth: Int
path: List<ArchiveCategoryRef>
```

### 4.2 `ArchiveCategoryNode`

目录页使用：

```text
id
name
englishName
sortOrder
depth
isTest
directRecordCount
descendantRecordCount
childCount
```

- `directRecordCount`：只统计 assignment 直接指向当前分类的档案。
- `descendantRecordCount`：当前分类及全部后代。
- 目录行需明确显示两个对象，不把“子分类数”和“档案数”混成一个数字。

### 4.3 `KitchenArchiveEntry` 扩展

只扩展只读投影，不改变数据库业务表：

```text
id
kind
name
subtitle
categoryId
categoryPath: List<String>
categoryPathLabel: String
searchText
aliases: List<String>       // 食材别名；其他类型为空
```

### 4.4 分类树查询

`archive_category` 数据量小，采用：

1. 一次查询当前库全部 ACTIVE 分类；
2. 一次查询当前库全部 assignment + category_id；
3. 在 Repository 内存构建树、路径和计数。

避免为每个节点递归查询 N+1；深树可在后续改为递归 CTE。

分类路径：

```text
基础酱汁 / 黄油乳化酱 / 热制黄油乳化酱
```

搜索结果、详情头部、实体列表都使用同一 `categoryPathLabel`。

---

## 5. 迁移 v5 → v6

### 5.1 迁移原则

- 只新增表/索引，不修改六张档案主表。
- 不删除旧字段、不移动旧数据。
- 保留 `sauce.family`、`ingredient.category`、`recipe.category`、`kitchen_reference.category`。
- 正式分类由现有值生成；不把任何真实档案挂到测试分类。
- 迁移失败由 Room 迁移事务回滚；不自动清库、不自动删表。
- 迁移不依赖种子重导；旧档案直接迁移。

### 5.2 迁移步骤

```text
MIGRATION_5_6
1. CREATE archive_category
2. CREATE archive_category_assignment
3. CREATE 索引
4. 为每个库的去重分类值插入一级分类节点
5. 为每条档案插入 assignment
6. 插入测试分类树（test_batch）
7. 不写任何 assignment 到测试分类
```

### 5.3 正式分类生成

#### 酱汁

从 `sauce.family` 去重：

- 空值不创建分类；
- 每个值创建一个 `library=SAUCE`、`parent_id=NULL` 的一级节点；
- `sort_order` 使用首次出现顺序（保持旧数据顺序），不是字母顺序；
- 每条 sauce 关联其 family 对应节点。

#### 食材

从 `ingredient.category` 去重，规则同上，`library=INGREDIENT`。

#### 菜谱

从 `recipe.category` 去重，`library=RECIPE`。

- `name` 使用当前 UI 已使用的中文分类名；
- `english_name` 保留枚举名（如 `DISH`），便于搜索/兼容；
- 旧 `recipe.category` 枚举保持不变。

#### 参考资料

从 `kitchen_reference` 的 `(kind, category)` 去重：

- `TECHNIQUE` -> `library=TECHNIQUE`
- `PRODUCTION_STANDARD` -> `library=PRODUCTION_STANDARD`
- `STORAGE_STANDARD` -> `library=STORAGE_STANDARD`
- 同名分类在不同 kind 下是不同节点，不混用。

### 5.4 确定性 ID

正式分类 ID：

```text
UUID.nameUUIDFromBytes("MISEBASE-CATEGORY:<library>:<normalized_name>")
```

正式 assignment ID：

```text
UUID.nameUUIDFromBytes("MISEBASE-CATEGORY-ASSIGN:<record_kind>:<record_id>")
```

测试分类 ID 使用固定 UUID 常量，便于清理和测试。

### 5.5 迁移后的旧字段

保持双写：

- 保存档案时继续写旧 `family/category` 字段为最终叶子名称；
- assignment 是分类树权威来源；
- 旧字段只用于兼容和失败回退，不用于分类路径。

---

## 6. 测试分类树（真实写入数据库）

`test_batch = 'UI-CATEGORY-TEST-V1'`。

### 6.1 酱汁：三级

```text
基础酱汁（L1，测试）
├─ 法式母酱（L2）
│  ├─ 白色母酱（L3）
│  ├─ 褐色母酱（L3）
│  └─ 鱼类母酱（L3）
└─ 黄油乳化酱（L2）
   ├─ 热制黄油乳化酱（L3）
   └─ 冷制黄油乳化酱（L3）
```

### 6.2 其他库：不同深度

```text
食材：测试食材分类（L1）
      └─ 测试香草与香料（L2）

菜谱：测试菜谱分类（L1）

烹饪技术：测试技术分类（L1）
            └─ 测试热加工（L2）

制作标准：测试标准分类（L1）

储存标准：测试储存分类（L1）
            └─ 测试冷藏（L2）
```

这些节点全部 `test_batch=UI-CATEGORY-TEST-V1`。

### 6.3 测试分类与真实档案

- **不把现有真实档案挂到测试分类。**
- 测试分类默认 `directRecordCount=0`，用于验证：
  - 一级进入；
  - 二级进入；
  - 三级进入；
  - 返回；
  - 数量显示 0；
  - 空状态。
- 因此测试树不能直接验证“同一分类同时有子分类和真实档案”。该场景由正式分类单元测试覆盖，或在明确授权后新增一条测试档案。
- 不新增测试档案，除非另行批准；不得把正式档案挂到测试分类。

### 6.4 测试分类标识

目录行显示小号“测试”标记；正式分类不显示。  
测试分类不参与编辑器的分类选择器默认列表，只在验收目录页显示；或编辑器选择器中按“正式 / 测试”分组，但不允许把正式档案保存到测试分类。

### 6.5 测试分类清理

设计为独立维护动作：

```text
清理 test_batch=UI-CATEGORY-TEST-V1 的分类
```

规则：

- 仅当该 batch 下没有 assignment 时删除；
- 有 assignment 则拒绝并报告；
- 不触碰正式分类、档案、版本、图片、关系；
- 不提供分类创建/管理入口。

清理入口可以放在设置页的“厨房测试种子”附近，作为单独的文字操作；是否添加需按任务边界确认。若不添加 UI，则提供可重复的数据库/开发清理脚本，不向最终用户暴露分类管理。

---

## 7. 分类选择与保存

### 7.1 酱汁编辑器分类区域

只改 `CLASSIFICATION / 分类归属` 区域：

- `LEVEL 01`：选一级；
- 选中后若有子节点，动态出现 `LEVEL 02`；
- 继续按实际树出现 `LEVEL 03...`；
- 无子节点即停止；
- 允许在任意存在档案的层级停止；
- 更换上级时清空该分支以下选择；
- 恢复编辑时按 assignment 的 category id 反查完整路径，逐级选中；
- 不允许在编辑器内创建分类；
- 保存时写入 assignment，旧 `family` 更新为叶子 name。

编辑器其余部分（顶栏、标题、主图、原料、步骤、适用范围、关系、备注、图片、排序、草稿/发布）保持不变。

### 7.2 保存顺序

```text
保存档案（现有 UseCase）
→ 保存版本（现有逻辑）
→ 写入/更新 archive_category_assignment
→ 写入旧分类字符串（叶子名称）
```

分类保存失败时，档案保存策略：

- 分类是档案元数据，不能静默丢失；
- 在编辑器中提示保存失败；
- 不改变已发布/草稿版本语义；
- 分类写入使用同一事务或明确的补偿机制，避免“档案保存成功但分类丢失”。

具体实现建议：

- 为档案保存和分类 assignment 写入使用同一个 `TransactionRunner.write` 块；
- 现有 `SaveSauceUseCase` 等可在返回 id 后由上层同一事务写入 assignment；如不能嵌套，需增加一个薄的事务协调 UseCase。

---

## 8. 目录与搜索查询设计

### 8.1 目录数据

```text
observeCategoryTree(library): Flow<List<ArchiveCategoryNode>>
observeCategoryEntries(library, categoryId): Flow<List<KitchenArchiveEntry>>
observeSearchEntries(library, query): Flow<List<KitchenArchiveEntry>>
```

实现层：

- 分类树 + assignment + archive entries 在 Repository 组合；
- 分类数量在内存树中计算；
- `descendantRecordCount` 递归汇总；
- 不访问 UI/DAO。

### 8.2 目录页面状态

```text
currentCategoryId: String?   // null = 当前库一级目录
query: String
isSearchOpen: Boolean
visibleLimit: Int = 20
```

显示规则：

- 有子分类：显示“下级分类”；
- 当前分类直接有档案：显示“当前分类档案”；
- 两者都有：两个分区；
- 都没有：空状态 + 新增入口；
- 实体列表分页 `显示 N / M 项` + `加载更多 ↓`，全部加载后隐藏。

### 8.3 搜索

- 六库各自一个右上角搜索图标；
- 点击从右向左展开，提示“全局搜索”；
- 搜索范围 = 当前 `library`；
- 结果行显示 `name` + `categoryPathLabel`；
- 点击详情返回，保留搜索词、搜索结果、滚动位置；
- 清除/退出搜索恢复目录；
- 不跨库；
- 厨房首页跨六库搜索移除。

搜索字段：

```text
name
englishName
aliases（仅食材，来自 ingredient_alias）
category path
subtitle
现有 searchText
```

编码：当前六类档案没有统一编码字段；不得伪造。若后续有编码字段再加入。

---

## 9. 详情页数据设计

### 9.1 通用阅读结构

六类详情共用视觉骨架：

```text
顶栏：返回 + 直接上一级名称
头部：分类路径 / 英文主标题 / 中文副标题 / 描述 / 状态
主图（有则显示）
分节：按类型
底部：最后更新 / 当前版本 / 历史版本 / 编辑
```

不共用业务字段模型，只共用组件和排版规则。

### 9.2 酱汁

- 分类路径；
- 英文/中文标题、描述、产量、难度、状态；
- 成品图（PRIMARY）+ 图注；
- 原料（中文/英文/数量/单位/说明，按 line_no）；
- 步骤（steps_json + step image，任意数量）；
- 适用范围（usage_notes）；
- 衍生关系（SAUCE 关系）；
- 研发备注（adjustable_notes + REFERENCE 图片）；
- 当前已发布版本 / 待发布草稿；
- 历史版本入口、编辑入口、发布草稿入口。

### 9.3 食材

现有可展示字段：

```text
name, englishName, scientificName, category, description,
flavorProfile, seasonality, origin, selectionNotes,
storageNotes, prepNotes, allergenNotes,
defaultUnit, conversions, relations
```

需要补充领域映射的字段：`scientificName`、`flavorProfile`、`seasonality`、`origin`、`selectionNotes`、`prepNotes` 目前领域模型未包含。

图片：当前无通用食材图片读取；如需显示已有 `INGREDIENT` 图片，需要通用图片读取端口。

### 9.4 菜谱

现有数据库字段：

```text
name, englishName, category, cuisine, description,
prepMinutes, cookMinutes, difficulty,
allergenNotes, ingredientSummary
```

领域模型当前只映射了一部分；必须补齐 `cuisine/description/prepMinutes/cookMinutes/difficulty` 的数据映射，不能显示伪造值。

展示：

- 分类路径、标题、描述、分类、产量、份数、难度；
- 原料组成（按 line_no）；
- 制作步骤（当前 `instructions` 文本；若有结构化步骤则按实际数据）；
- 适用信息；
- 关联；
- 当前/草稿版本、历史入口、编辑。

图片：`image_asset` 有 `RECIPE` / `RECIPE_VERSION` 归属枚举，但没有读取入口；需要通用读取才可显示。

### 9.5 技术 / 制作标准 / 储存标准

当前只有：

```text
name, englishName, category, description, seedNote
```

详情以阅读段落展示 description，不虚构独立字段。  
若要求把“原理/操作步骤/适用范围/具体要求/温度/期限”拆成独立区块，需要新增结构化字段；这会扩大数据库范围，需另行批准。

图片：`ImageOwnerTypeDb` 当前没有 `KITCHEN_REFERENCE`；不显示图片，或需新增枚举，另行决定。

### 9.6 最后更新时间

- 酱汁已有 `updatedAtEpochMillis` 投影；
- 食材/菜谱/参考资料实体有 `updated_at`，但领域模型未映射；
- 详情页最后更新需要补齐领域映射（无 schema 变更）。

---

## 10. 失败保护与回滚

1. v6 迁移前打 Git tag 和 v5 数据库备份。
2. `MIGRATION_5_6` 只创建表和索引，不清库。
3. 迁移在 Room 事务中执行；失败回滚。
4. 迁移后校验：
   - `PRAGMA user_version=6`；
   - 六张档案表行数不变；
   - 正式分类数量正确；
   - assignment 每条档案至多一条；
   - 旧 `family/category` 不变；
   - 测试分类 `test_batch` 正确且无 assignment。
5. 如迁移失败，不自动删除数据库；使用备份/回滚点恢复。
6. 迁移完成前不启用分类 UI 写入。

---

## 11. 测试计划

### 11.1 数据库

- v5 → v6：表/索引/列存在；
- 正式分类数量与现有 distinct 值一致；
- 每条旧档案有且仅有 1 条 assignment；
- 旧分类字段未改；
- 分类父子同库；
- 循环拒绝；
- 测试分类无 assignment；
- 清理 batch 不影响正式数据；
- v1/v2/v3/v4/v5 历史迁移仍可达 v6。

### 11.2 分类目录

- 一级进入；
- 二级进入；
- 三级进入；
- 无子分类停止；
- 分类 + 档案同时显示；
- 空分类；
- 数量：直接档案数 / 子分类数；
- 返回上一级；
- 分页加载；
- 正式/测试分类标识。

### 11.3 编辑器分类

- 新建选择 1/2/3 级；
- 任一级停止；
- 换上级清空下级；
- 编辑旧档案恢复路径；
- 保存 assignment 正确；
- 分类不改变版本/发布语义；
- 分类区之外布局不变。

### 11.4 搜索

- 六库分别搜索；
- 不跨库；
- 中英文/别名；
- 结果显示路径；
- 从详情返回搜索状态；
- 厨房首页无跨库搜索。

### 11.5 详情

- 六类独立详情；
- 数据完整；
- 图片有/无；
- 关系跳转；
- 正式版本与草稿；
- 编辑入口；
- 不伪造字段。

---

## 12. 开放决策

1. **正式分类的初始排序**  
   当前没有专业排序字段。迁移默认按现有值首次出现顺序。是否批准这个排序，或提供人工专业顺序？

2. **测试分类与测试档案**  
   测试树默认无档案，只能验证层级/空状态。是否允许新增少量 **测试档案**（独立 seed 标记、可清理）以验证“分类 + 档案同时显示”和非零数量？不得挂真实档案。

3. **测试分类清理入口**  
   是否允许在设置页增加“清理分类测试数据”文字操作？不增加分类创建/管理入口。

4. **详情图片范围**  
   是否补齐通用图片读取，用于食材/菜谱详情？参考资料当前无 `KITCHEN_REFERENCE` 图片归属，需明确不显示还是新增枚举。

5. **参考资料结构化字段**  
   是否接受以现有 `description` 段落展示技术/标准详情？若要求独立字段，需要新增列，扩大迁移范围。

6. **分类与档案保存事务**  
   是否允许新增一个薄的事务协调 UseCase，把现有档案保存与 assignment 写入放进同一事务？不改变版本/发布语义。

---

## 13. 结论

本设计满足已批准的：

- 两张独立表；
- 六库隔离；
- 任意层级；
- 排序；
- 档案关联；
- 数据完整性保护；
- 正式分类保留；
- 测试分类真实写入数据库并可清理；
- 不修改六张档案主表。

在以上开放决策确认前，不进入实现。
