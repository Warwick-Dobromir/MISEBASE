# MB-UI-SAUCE-EDITOR-001 第一阶段审查

日期：2026-11-09  
范围：酱汁新增 / 编辑页  
状态：**仅审查，未修改任何代码或数据库**

---

## 1. 审查结论

现有酱汁编辑器是一个“基础字段 + 组成行”的表单页，不是本任务要求的连续纵向档案编辑器。

当前已具备：

- 新建 / 编辑同一路由；
- 中文名、分类、产量、单位；
- 食材组成行（仅限已有食材）；
- 草稿 / 发布两种保存动作；
- 已发布版本不可覆盖，保存会创建新版本；
- 档案扩展字段已存在并可保存。

当前缺失：

- 九段式页面结构；
- 英文主标题、分类标签、描述、难度；
- 成品图、图注、步骤图、研发照片；
- 制作步骤（标题 / 说明 / 排序 / 独立图片）；
- 适用范围、衍生酱汁、研发备注；
- 删除档案、未保存离开提示；
- 原料 / 步骤 / 关系的拖动排序与即时增删。

---

## 2. 涉及文件

### 2.1 当前编辑器直接链路

```text
feature/sauces/src/main/java/com/culinary/feature/sauces/SauceEditorScreen.kt
feature/sauces/src/main/java/com/culinary/feature/sauces/SauceEditorViewModel.kt
feature/sauces/src/main/java/com/culinary/feature/sauces/SaucesUiState.kt
feature/sauces/src/main/java/com/culinary/feature/sauces/SaucesRoutes.kt
feature/sauces/src/main/res/values/strings.xml
feature/sauces/build.gradle.kts
```

### 2.2 编辑器依赖的领域 / 数据链路

```text
core/domain/.../model/Sauce.kt
core/domain/.../model/SauceVersion.kt
core/domain/.../model/SauceComponent.kt
core/domain/.../model/ComponentTarget.kt
core/domain/.../model/SauceDetail.kt
core/domain/.../usecase/sauce/SaveSauceUseCase.kt
core/domain/.../usecase/sauce/SaveSauceDraftUseCase.kt
core/domain/.../usecase/sauce/PublishSauceVersionUseCase.kt
core/domain/.../usecase/sauce/GetSauceDetailUseCase.kt
core/domain/.../repository/SauceRepository.kt
core/data/.../repository/SauceRepositoryImpl.kt
core/data/.../mapper/SauceMapper.kt
core/database/.../entity/SauceEntity.kt
core/database/.../entity/SauceVersionEntity.kt
core/database/.../entity/SauceComponentEntity.kt
core/database/.../dao/SauceDao.kt
core/database/.../dao/SauceVersionDao.kt
core/database/.../dao/SauceComponentDao.kt
```

### 2.3 图片、关系、备注相关现状

```text
core/database/.../entity/ImageAssetEntity.kt
core/database/.../dao/ImageAssetDao.kt
core/data/.../file/LocalImageStorage.kt
core/database/.../entity/KitchenRelationEntity.kt
core/database/.../dao/KitchenRelationDao.kt
core/domain/.../model/KitchenRelation.kt
core/domain/.../repository/KitchenRelationRepository.kt
core/data/.../repository/KitchenRelationRepositoryImpl.kt
```

### 2.4 应用外壳 / 导航

```text
app/src/main/java/com/culinary/navigation/CulinaryNavHost.kt
```

编辑器当前位于应用统一的 `TopAppBar` 下。新设计要求“返回 / 取消 / 保存”一体顶栏，需要让编辑器路由隐藏统一顶栏；这是本任务之外唯一必须触碰的 app 外壳点，改动应限制为一个路由条件。

---

## 3. 当前页面结构

```text
[应用统一 TopAppBar：仅返回]

PageHeader
  SAUCE
  新建酱汁 / 编辑酱汁 · 新版本 vN
  已发布提示（可选）

[名称] CulinaryTextField
[分类 / 家族] CulinaryTextField

SectionHeader 标准产量
[产量输入框] [单位选择按钮 → AlertDialog]

SectionHeader 组成（N 行）
只读提示（若有子菜谱 / 子酱汁行）
每行：
  名称 + 类型
  数量输入框 + 单位
  可选开关
  移除
[添加食材 → 食材选择 AlertDialog]

[保存草稿] [保存并发布]
```

没有成品图、步骤、适用范围、衍生关系、研发备注、删除、取消、未保存提示。

---

## 4. 现有数据模型

### 4.1 `sauce`（v4）

| 字段 | 用途 | 与本任务的关系 |
|---|---|---|
| `name` | 中文名 | 可复用 |
| `normalized_name` | 查重 | 保留 |
| `family` | 分类 / 家族 | 可复用为分类标签与 CATEGORY，不能重复造字段 |
| `texture` | 质地 | 可复用为描述候选 |
| `flavor_profile` | 成品标准 | 已有详情展示 |
| `usage_notes` | 适用方向 | 可作为“适用范围”候选，但语义偏档案说明 |
| `english_name` | 英文名 | 可复用为英文主标题 |
| `main_ingredients` | 主要原料文本 | 不能替代结构化原料 |
| `preparation_summary` | 制作概要 | 不能替代步骤 |
| `key_parameters` | 关键参数 | 不承载难度 |
| `storage_notes` | 保存建议 | 不属于本页必填 |
| `adjustable_notes` | 可调整项 | 可作为“研发备注”候选 |
| `seed_note` | 种子备注 | 只读展示 |
| `created_at` / `updated_at` | 时间 | `updated_at` 未进入领域模型 |

### 4.2 `sauce_version`

| 字段 | 用途 | 限制 |
|---|---|---|
| `yield_qty` / `yield_unit_id` | 产量 | 可复用 |
| `instructions_md` | 制作说明纯文本 | 没有步骤标题、步骤序号、步骤图片关联 |
| `status` / `is_current` | 草稿 / 当前版本 | 已发布内容不可修改 |

### 4.3 `sauce_component`

| 字段 | 用途 | 限制 |
|---|---|---|
| `ingredient_id` | 食材目标 | 只能引用既有食材，不能保存自由文本名称 |
| `quantity` / `unit_id` | 数量 | 可复用 |
| `is_optional` | 可选 | 可复用 |
| `sort_order` / `line_no` | 顺序 | 可复用 |
| `preparation_note` / `group_label` | 备注 / 分组 | 当前编辑器未使用 |

`SauceComponent` 是 `ComponentTarget` 三选一（食材 / 子菜谱 / 子酱汁），没有“自由文本原料”类型。

### 4.4 图片

- `image_asset` 已存在：`owner_type`、`owner_id`、`role`、`step_no`、`relative_path`、`mime_type`、`width`、`height`、`byte_size`、`sha256`、`sort_order`、`deleted_at`。
- `ImageOwnerTypeDb` 已包含 `SAUCE`、`SAUCE_VERSION`。
- `ImageRoleDb` 已包含 `PRIMARY`、`PROCESS`、`REFERENCE`。
- `ux_image_primary` 保证每个 owner 至多一张 PRIMARY。
- `LocalImageStorage` 已实现原子写入、哈希、私有目录、删除。
- **但没有 `ImageAssetDao` 的 Hilt provider，也没有 domain repository/use case；当前没有任何生产代码使用图片。**

### 4.5 衍生关系

- `kitchen_relation` 已存在，可存 `SAUCE → SAUCE`。
- 现只有只读 `KitchenRelationRepository.observeForRecord`。
- 没有新增 / 删除 / 重排关系的 use case。
- 没有 `sort_order` 字段，不能持久化拖动顺序。

### 4.6 研发备注

- 没有独立 `sauce_note` 表。
- 只能用 `sauce.adjustable_notes` 之类的文本字段。
- 研发照片可以挂到 `image_asset(owner_type=SAUCE, role=REFERENCE)`，但没有“某张照片属于某条备注”的独立关联。

### 4.7 删除

- `SauceDao` 有 `archive(id, at)` 和 `deleteById(id)`。
- `SauceRepository` 没有 archive/delete 方法。
- 没有 `ArchiveSauceUseCase`。
- 现有食材/参考资料使用归档语义；酱汁没有对应入口。

---

## 5. 可直接复用的组件

| 组件 | 复用方式 |
|---|---|
| `CulinaryRule` | 模块 / 行分割线 |
| `SectionHeader` | 01–05 模块标题；支持 eyebrow、subtitle、action、细线 |
| `CulinaryTextButton` | 取消、添加、移除、跳转等低强调动作 |
| `CulinaryIconButton` | 返回、删除、添加、拖动入口 |
| `CulinaryIcons` | Back、Add、Close、Trash、Sort、ChevronRight、ArrowForward、Check |
| `ConfirmDialog` | 未保存离开、删除档案二次确认 |
| `ErrorBanner` | 保存失败、字段校验 |
| `CulinaryButton` | 仅用于确认弹窗；页面主操作按任务要求改为顶栏文字 |
| `CulinaryTheme` / `CulinarySpacing` / `CulinaryTextStyles` | 不新增全局 token；页面局部使用现有间距与字阶 |
| `LocalImageStorage` | 复用现有图片文件存储，不改造机制 |
| `image_asset` + `ImageAssetDao` | 复用现有图片元数据表与 DAO |

不建议直接使用 `CulinaryTextField` 作为页面主体输入：它是 56dp 高、1dp 外框的 Material 轮廓输入框，和“无卡片、可连续纵向排版、原位编辑”的编辑式档案不一致。应在 `feature:sauces` 内部实现轻量的行内可编辑文本组件，不改全局设计系统。

---

## 6. 拟修改文件（待确认，尚未修改）

### 6.1 编辑器 UI / 状态

```text
feature/sauces/.../SauceEditorScreen.kt        重写为九段式页面
feature/sauces/.../SauceEditorViewModel.kt     草稿状态、动态行、图片、关系、脏状态、删除
feature/sauces/.../SaucesUiState.kt            新增强类型编辑状态
feature/sauces/.../sauceeditor/                仅本编辑器使用的局部组件
feature/sauces/src/main/res/values/strings.xml 新增文案
feature/sauces/build.gradle.kts                 仅新增必要的 Activity Result 图片选择依赖
```

### 6.2 领域 / 数据

```text
core/domain/.../model/Sauce.kt                 可选：difficulty、description、updatedAt
core/domain/.../model/SauceVersion.kt          可选：结构化步骤
core/domain/.../model/SauceComponent.kt        可选：显示名覆盖
core/domain/.../usecase/sauce/SaveSauceUseCase.kt
core/domain/.../usecase/sauce/SaveSauceDraftUseCase.kt
core/domain/.../usecase/sauce/ArchiveSauceUseCase.kt（新增）
core/domain/.../repository/SauceRepository.kt
core/domain/.../repository/KitchenRelationRepository.kt（增加写方法）
core/domain/.../repository/SauceImageRepository.kt（新增，复用 image_asset）
core/data/.../repository/SauceRepositoryImpl.kt
core/data/.../repository/KitchenRelationRepositoryImpl.kt
core/data/.../repository/SauceImageRepositoryImpl.kt（新增）
core/data/.../mapper/SauceMapper.kt
core/data/.../di/ArchiveDaoModule.kt            提供 ImageAssetDao
core/data/.../di/RepositoryModule.kt            绑定 SauceImageRepository
core/data/.../di/SauceUseCaseModule.kt          提供归档 / 图片 use case
app/.../navigation/CulinaryNavHost.kt           仅：酱汁编辑路由隐藏统一顶栏
```

### 6.3 若批准数据库变更

```text
core/database/.../entity/SauceEntity.kt
core/database/.../entity/SauceVersionEntity.kt
core/database/.../entity/SauceComponentEntity.kt
core/database/.../entity/KitchenRelationEntity.kt
core/database/.../dao/*（对应查询）
core/database/.../CulinaryDatabase.kt           v5
core/database/.../CulinaryMigrations.kt         MIGRATION_4_5
core/database/schemas/.../5.json
core/database/src/test/.../CulinaryMigrationV4ToV5Test.kt
core/data/.../startup/StartupCoordinator.kt     EXPECTED_SCHEMA_VERSION=5
core/data/.../settings/SettingsMaintenance.kt   恢复预检 tested sources
```

---

## 7. 技术限制 / 必须先确认

### A. 难度

`sauce` / `sauce_version` 没有难度字段。  
可选：

1. 新增 `sauce.difficulty`（`EASY/MEDIUM/HARD`），完整支持；
2. 不新增，页面不显示难度，或只读推断（不推荐，属于假数据）。

### B. 描述与图注

没有 `description` 和图片 caption 字段。  
可选：

1. 新增 `sauce.description`、`sauce.hero_caption`；
2. 描述映射到现有 `texture`，图注不持久化（不满足验收）；
3. 图注存储到 `image_asset.caption`（需要改通用图片表）。

### C. 原料中文 / 英文名称输入

现有 `sauce_component` 只能引用 `ingredient_id`，没有自由文本名称字段。  
可选：

1. 原料行必须选择既有食材；入力框用于搜索/选择，显示名可覆盖但保存为 `label_override`（新增 2 列）；
2. 允许自由文本原料，需要新增 label 字段并放宽 component 目标校验，或自动创建食材档案（会触碰食材库，超出本任务边界）；
3. 不做自由文本，只保留现有食材选择器（不满足任务书）。

### D. 制作步骤标题 / 排序

`instructions_md` 只有一整段文本，没有步骤标题、步骤序号、步骤表。  
可选：

1. 新增 `sauce_version.steps_json`，用一个结构化 JSON 保存步骤（标题、说明、顺序），图片仍用 `image_asset(step_no)`；
2. 新增 `sauce_step` 表（更规范，但数据库改动更大）；
3. 把 Markdown 约定写入 `instructions_md`（零迁移，但格式脆弱）。

### E. 衍生关系排序与写入

`kitchen_relation` 可存关系，但没有 `sort_order`，Repository 只读。  
可选：

1. 新增 `kitchen_relation.sort_order`，增加写 use case；
2. 不持久化拖动顺序，只按创建时间显示（不满足排序持久化）；
3. 单独建 `sauce_relation` 表（重复建模，不推荐）。

### F. 研发备注与照片

没有独立备注表。  
建议：

- 文本使用 `sauce.adjustable_notes`；
- 照片使用 `image_asset(owner_type=SAUCE, role=REFERENCE)`；
- 不做“一条备注对应多张照片”的独立结构，除非批准新增 `sauce_note` 表。

### G. 图片与备份

`LocalImageStorage` 与 `image_asset` 可用，但：

- `ImageAssetDao` 没有 Hilt provider；
- 没有 domain repository/use case；
- 备份写入器支持附件，但当前没有生产代码收集 `image_asset`；
- 任务禁止修改备份机制。

因此图片可以落本地并被编辑器读取，但**现有备份 ZIP 不会自动包含这些新图片**，除非允许增加一个最小的图片附件收集器。需要确认是否接受这个边界，或授权修改备份附件收集。

### H. 顶栏

编辑器当前在 app 统一 `TopAppBar` 下。要显示“返回 / 取消 / 保存”及底部细线，必须在 `CulinaryNavHost` 对 `SaucesRoutes.EDIT` 隐藏统一顶栏，由编辑器自己画顶部操作区。这是 app 外壳的 1 个路由条件，不是全局导航重构。

### I. 保存语义

现有编辑器有两个动作：“保存草稿”和“保存并发布”。新设计只有一个“保存”。  
需要确认：

1. 保存 = 保存草稿，随后在详情页发布（更安全，保留现有版本流程）；
2. 保存 = 直接发布新版本（更接近“保存即完成”，但会改变已发布档案）；
3. 首次保存草稿，已存在草稿时发布（混合语义，不建议）。

### J. 删除语义

没有 `ArchiveSauceUseCase`。建议沿用食材的归档语义：`status=ARCHIVED`，不物理删除，不影响历史版本；需要新增 use case。是否接受“删除 = 归档”？

---

## 8. 建议方案

### 方案 A（推荐，完整满足，需要最小 v5 迁移）

新增 7 个可空/默认列，不新增表：

```text
sauce.description              TEXT
sauce.difficulty               TEXT  (EASY / MEDIUM / HARD)
sauce.hero_caption             TEXT
sauce_component.label_override     TEXT
sauce_component.label_override_en  TEXT
sauce_version.steps_json       TEXT
kitchen_relation.sort_order    INTEGER NOT NULL DEFAULT 0
```

配套：

- 步骤图片继续用 `image_asset(owner_type=SAUCE_VERSION, role=PROCESS, step_no)`；
- 成品图用 `image_asset(owner_type=SAUCE, role=PRIMARY)`；
- 研发照片用 `image_asset(owner_type=SAUCE, role=REFERENCE)`；
- 研发备注文本用 `sauce.adjustable_notes`；
- 原料名称可编辑为 `label_override`，但仍必须选择既有食材目标；新增自由文本原料需要再确认（否则不允许保存新名称）；
- 关系写入 `kitchen_relation`，排序用新 `sort_order`；
- 删除使用归档。

仍然存在的边界：原料自由文本不新建食材档案；研发备注没有独立记录表；图片不进入现有备份 ZIP（除非授权）。

### 方案 B（零迁移，接受不完整）

- 不做难度、图注、步骤标题、关系排序、图片。
- 适用范围复用 `usage_notes`，研发备注复用 `adjustable_notes`。
- 只能做视觉和已有字段编辑。

不满足任务书 B/C 全面验收，不建议。

### 方案 C（先 UI，后数据）

- 先完成不依赖新字段的九段式页面骨架、顶栏、原料/步骤/关系动态列表、未保存提示。
- 图片、难度、图注、步骤标题、关系排序标记为待数据结构确认，不伪装完成。
- 之后单独做 v5 迁移和图片链路。

---

## 9. 需要用户确认的问题

1. 是否批准 **方案 A 的最小 v5 迁移**（7 列，不新增表）？
2. 原料中文/英文名是否必须允许“自由文本新原料”？若必须，需要放宽 `sauce_component` 目标校验或自动创建食材档案，二者都超出当前任务边界，需要明确授权。
3. 研发备注是否接受“单文本 `adjustable_notes` + 多张 `REFERENCE` 图片”，还是必须新增独立备注记录表？
4. 图片是否必须进入现有备份 ZIP？若必须，需要授权修改备份附件收集。
5. 单一“保存”是保存草稿还是直接发布？
6. “删除此酱汁档案”是否接受归档（`ARCHIVED`）而非物理删除？

在以上确认前，不进行第二阶段代码修改。
