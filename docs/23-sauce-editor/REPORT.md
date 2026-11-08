# MB-UI-SAUCE-EDITOR-001 交付报告

日期：2026-11-09  
版本：**1.0.4 (5)**  
包名：`com.culinary`  
任务：酱汁档案新增 / 编辑界面专项改造

---

## 1. 交付摘要

已完成酱汁新增与编辑页的九段式改造，并按确认的方案实施 **schema v5**。

- 新增/编辑使用完全相同的页面结构。
- 顶栏：返回、取消、保存；未保存离开二次确认。
- 基础信息：分类标签、英文名、中文名、描述、CATEGORY/YIELD/DIFFICULTY 三栏。
- 成品图：上传、替换、删除、图注。
- 原料：中文/英文名称覆盖、数量、单位、可选、添加、删除、拖动排序。
- 制作工艺：步骤标题、步骤图、说明、添加、删除、拖动排序、自动重编号。
- 适用范围：多行文本。
- 衍生关系：关联、解除、跳转、拖动排序；只使用酱汁 ID。
- 研发备注：多行文本 + 研发照片。
- 页脚：自动最后更新时间、弱化暗红删除入口、二次确认。
- 保存语义：
  - 新档案 / 从未发布：保存并发布首版；
  - 已有已发布版本：只创建或更新待发布草稿；
  - 再次编辑同一草稿：更新同一版本，不重复创建。
- 详情页区分当前已发布版本与待发布草稿。

---

## 2. 版本与安装包

| 项 | 值 |
|---|---|
| 版本 | `1.0.4` |
| versionCode | `5` |
| Schema | `5` |
| 包名 | `com.culinary` |
| APK | [MISEBASE-1.0.4-sauce-editor-20261109-release.apk](../../dist/KITCHEN-SAUCE-EDITOR-001/MISEBASE-1.0.4-sauce-editor-20261109-release.apk) |
| 大小 | 24,660,430 bytes |
| SHA-256 | `00ac9bcd63ef042782068cac5e81303861a2202dcbe023b28e26e2bcc168fb80` |
| 签名证书 SHA-256 | `5ca41959d9f450cdc851dcb9f052ac9130aaeab71c64062173a699355056af61` |
| 签名校验 | `apksigner verify` 通过（exit 0） |

使用原 MISEBASE 发布证书；未新建 keystore、未改包名、未修改签名配置。

---

## 3. 回滚点与数据库备份

- Git tag：`pre-sauce-editor-v5`
- Commit：`634a5be5d188980816eae373e45d1dffd0e69831`
- v4 数据库：`build/backup/v4-pre-sauce-editor/culinary-v4.db`
  - SHA-256：`63968da037f675c4e42b737b37fe49e16d24ceaced8917b8dce6f5ff112b9b45`
  - `PRAGMA user_version = 4`
- v4 schema：`core/database/schemas/com.culinary.core.database.CulinaryDatabase/4.json`
  - SHA-256：`1d325727feda1d759e9e9c5b2f0ef9953a5512b1483e8ecb0f03260f383e597b`
- v5 schema：`core/database/schemas/com.culinary.core.database.CulinaryDatabase/5.json`
  - SHA-256：`fc871c6e4285da10a96c2b957987120b950b1c1fabf2ac767013ed6013937af6`

回滚说明见 `build/backup/v4-pre-sauce-editor/README.md`。迁移失败不会自动删除数据库或清空数据；`MIGRATION_4_5` 只做 `ALTER TABLE`，失败由 Room 回滚。

---

## 4. 数据库变更（v5）

只新增 7 列，**不新增表**：

| 表 | 列 | 说明 |
|---|---|---|
| `sauce` | `description TEXT` | 酱汁描述 |
| `sauce` | `difficulty TEXT` | `EASY` / `MEDIUM` / `HARD` |
| `sauce` | `hero_caption TEXT` | 成品图图注 |
| `sauce_component` | `label_override TEXT` | 中文名称覆盖 |
| `sauce_component` | `label_override_en TEXT` | 英文名称覆盖 |
| `sauce_version` | `steps_json TEXT` | 带版本号的步骤 JSON |
| `kitchen_relation` | `sort_order INTEGER NOT NULL DEFAULT 0` | 衍生关系顺序 |

迁移：`MIGRATION_4_5`

1. 移除 v4 运行时局部索引；
2. `ALTER TABLE` 增加列；
3. `onOpen` 重建局部索引。

旧数据全部保留：安装最终 APK 后 `PRAGMA user_version=5`，原 10 个酱汁、10 个版本、260 条关系仍在。

### 4.1 原料名称覆盖

原料仍必须绑定 `ingredient_id`：

- 中文/英文名称输入只写 `label_override` / `label_override_en`；
- 未设置覆盖值时回退食材档案原名；
- 不修改食材库；
- 更换原料仍走既有食材选择器，绑定有效食材 ID。

### 4.2 步骤 JSON

```json
{
  "schemaVersion": 1,
  "steps": [
    { "id": "stable-step-id", "title": "酸性基底还原", "body": "操作说明" }
  ]
}
```

- 图片不写入 JSON，继续由 `image_asset.owner_type=SAUCE_VERSION`、`role=PROCESS`、`step_no` 关联；
- 拖动步骤后保存会同步已持久化步骤图的 `step_no`；
- 删除步骤不会误删其他步骤图片；
- 旧 `instructions_md` 仍写入可读文本，保证旧详情兼容。

### 4.3 图片

复用既有机制，不改存储实现：

- 成品图：`image_asset(owner_type=SAUCE, role=PRIMARY)`；
- 步骤图：`image_asset(owner_type=SAUCE_VERSION, role=PROCESS, step_no=N)`；
- 研发照片：`image_asset(owner_type=SAUCE, role=REFERENCE)`；
- 文件继续由 `LocalImageStorage` 写入应用私有目录；
- 删除图片只删除图片记录/文件，不删除酱汁档案。

---

## 5. 页面与交互实现

### 5.1 顶栏

- 左侧返回图标 + 取消文字；右侧保存文字；
- 保存期间显示加载状态并禁止重复提交；
- 未保存修改点取消/返回会弹出二次确认；
- 键盘打开时返回键先收起键盘，不会误触放弃提示。

### 5.2 基础信息

- 顶部可编辑分类标签，占位 `CLASSIC FRENCH SAUCE`；
- 英文名大字号，必填；
- 中文名中等字号，必填；
- 描述小字号；
- 1:1:1 三栏：CATEGORY（复用 `family`，不重复造字段）、YIELD（版本产量）、DIFFICULTY（v5 字段）。

### 5.3 原料

- 每行原位编辑中文名、英文名、数量、单位；
- 名称保存为 `label_override` / `label_override_en`；
- 支持添加既有食材、删除、拖动排序；
- 保留可选标记；
- 保留既有单位体系与数量校验。

### 5.4 制作工艺

- 步骤序号自动重编号；
- 标题、步骤图、说明；
- 添加/删除/拖动排序；
- 步骤图独立上传、替换、删除。

### 5.5 衍生关系

- 关联既有酱汁，排除当前档案与已关联项；
- 解除关系只删除关系行，不删除目标档案；
- 点击名称或 ↗ 跳转既有酱汁详情；
- 拖动排序写入 `kitchen_relation.sort_order`。

### 5.6 研发备注

- 多行文本写入 `sauce.adjustable_notes`；
- “添加文字备注”在文本末尾追加空条目；
- “上传研发照片”写入 `REFERENCE` 图片；
- 不新增独立备注表，沿用现有单文本字段 + 图片结构。

### 5.7 页脚

- 最后更新时间读取 `sauce.updated_at`，不可手填；
- 删除入口为弱化暗红文字，二次确认后归档；
- 归档保留历史版本、图片与关系。

---

## 6. 已完成功能清单

- [x] 九段式单列纵向页面
- [x] 自定义返回 / 取消 / 保存顶栏
- [x] 未保存离开提示
- [x] 英文名、中文名、分类、描述编辑
- [x] 产量与单位编辑
- [x] 难度编辑并持久化
- [x] 成品图上传 / 替换 / 删除
- [x] 成品图图注
- [x] 原料添加 / 修改 / 删除 / 拖动排序
- [x] 原料中英文名称覆盖
- [x] 步骤添加 / 修改 / 删除 / 拖动排序
- [x] 步骤图独立上传 / 替换 / 删除
- [x] 步骤重编号与图片 step_no 同步
- [x] 适用范围多行编辑
- [x] 衍生酱汁关联 / 解除 / 跳转 / 排序
- [x] 研发备注文本 + 照片
- [x] 归档删除 + 二次确认
- [x] 新档案保存即发布
- [x] 已发布档案保存为草稿
- [x] 再次保存复用同一草稿
- [x] 详情区分已发布版本与待发布草稿
- [x] schema v5 迁移与旧数据保留

---

## 7. 测试结果

### 7.1 单元 / Robolectric

| 模块 | 测试数 | 失败 |
|---|---:|---:|
| app | 53 | 0 |
| core:data | 100 | 0 |
| core:database | 87 | 0 |
| feature:sauces | 13 | 0 |
| feature:ingredients | 11 | 0 |
| feature:recipes | 11 | 0 |
| **合计** | **275** | **0** |

命令：

```powershell
.\gradlew.bat :core:database:testDebugUnitTest `
  :core:data:testDebugUnitTest `
  :app:testDebugUnitTest `
  :feature:sauces:testDebugUnitTest
```

重点测试：

- v4 → v5 迁移：新列存在、旧酱汁/版本保留；
- v1 / v2 / v3 历史迁移仍可达 v5；
- 新档案保存后发布首版；
- 已发布档案保存后创建草稿；
- 同一草稿第二次保存不创建新版本；
- 原料中英文名称覆盖持久化；
- 步骤顺序 JSON 持久化；
- 图片用例、关系排序、归档、备份版本预检；
- 九段式编辑器 UI 结构。

### 7.2 静态检查

```text
:feature:sauces:ktlintCheck / detekt
:app:ktlintCheck / detekt
:core:data:ktlintCheck / detekt
:core:domain:ktlintCheck / detekt
:core:database:ktlintCheck / detekt
:core:database:compileDebugAndroidTestKotlin
```

全部通过。

### 7.3 设备验收

模拟器：`Medium_Phone_API_36.1`；安装最终 `1.0.4 (5)` 全新安装：

- 厨房计数：`10 / 18 / 10 / 10 / 10 / 10`；
- 打开“半釉汁”编辑器，九段顺序正确，无横向滚动、无卡片、无阴影；
- 已发布档案保存：生成 v2 草稿，详情显示“待发布草稿”；
- 再次编辑 v2 并保存：仍为 v2，未重复创建；
- 新建 `Test` 酱汁：保存后直接发布 v1；
- 发布 v2：当前版本变为 v2；
- 原料从 1 行增至 2 行并拖动：DB `line_no` 顺序变为 `10 ml 橄榄油`、`30 g 无盐黄油`；
- 步骤从 1 个增至 2 个并拖动：DB `steps_json` 顺序改变，步骤图 `step_no` 从 1 同步为 2；
- 衍生关系从 1 条增至 2 条并拖动：DB `sort_order` 顺序改变；
- 成品图上传成功；删除成品图后档案保留；步骤图独立保留；
- 未保存修改点取消/返回：出现“放弃未保存修改？”；
- 放弃后原描述未被覆盖；
- 删除测试酱汁：二次确认后归档，历史版本与图片保留；
- 冷启动无 FATAL / ANR。

证据目录：[evidence/](evidence/)  
主界面：[08-final-editor.png](08-final-editor.png)  
页中：[02-editor-mid.png](02-editor-mid.png)  
页尾：[03-editor-bottom.png](03-editor-bottom.png)  
未保存提示：[06-discard-dialog.xml](06-discard-dialog.xml)  
最终厨房计数：[07-final-kitchen.xml](07-final-kitchen.xml)

---

## 8. 修改文件清单

### 8.1 新增

```text
feature/sauces/.../SauceEditorComponents.kt
feature/sauces/.../SauceEditorSections.kt
feature/sauces/.../SauceStepCodec.kt
core/domain/.../model/SauceImage.kt
core/domain/.../repository/SauceImageRepository.kt
core/domain/.../usecase/sauce/SauceImageUseCase.kt
core/domain/.../usecase/sauce/ArchiveSauceUseCase.kt
core/domain/.../usecase/sauce/SaveSauceRelationsUseCase.kt
core/data/.../repository/SauceImageRepositoryImpl.kt
core/database/schemas/.../5.json
core/database/src/test/.../CulinaryMigrationV4ToV5Test.kt
```

### 8.2 修改

```text
app/build.gradle.kts
app/src/main/java/com/culinary/navigation/CulinaryNavHost.kt
app/src/test/java/com/culinary/kitchen/archive/KitchenReferenceViewModelTest.kt

feature/sauces/build.gradle.kts
feature/sauces/.../SauceEditorScreen.kt
feature/sauces/.../SauceEditorViewModel.kt
feature/sauces/.../SaucesUiState.kt
feature/sauces/.../SauceDetailScreen.kt
feature/sauces/src/main/res/values/strings.xml
feature/sauces/src/test/.../SaucesViewModelTest.kt
feature/sauces/src/testDebug/.../SaucesUiTest.kt

core/domain/.../model/Sauce.kt
core/domain/.../model/SauceVersion.kt
core/domain/.../model/SauceComponent.kt
core/domain/.../model/KitchenRelation.kt
core/domain/.../repository/SauceRepository.kt
core/domain/.../repository/KitchenRelationRepository.kt
core/domain/.../usecase/sauce/GetSauceDetailUseCase.kt
core/domain/.../usecase/sauce/SaveSauceUseCase.kt
core/domain/.../usecase/sauce/SaveSauceDraftUseCase.kt

core/data/.../mapper/SauceMapper.kt
core/data/.../mapper/KitchenRelationMapper.kt
core/data/.../repository/SauceRepositoryImpl.kt
core/data/.../repository/KitchenRelationRepositoryImpl.kt
core/data/.../di/ArchiveDaoModule.kt
core/data/.../di/DataModule.kt
core/data/.../di/RepositoryModule.kt
core/data/.../di/SauceUseCaseModule.kt
core/data/.../settings/SettingsMaintenance.kt
core/data/.../startup/StartupCoordinator.kt

core/database/.../CulinaryDatabase.kt
core/database/.../CulinaryMigrations.kt
core/database/.../schema/CulinarySchemaExtras.kt
core/database/.../entity/SauceEntity.kt
core/database/.../entity/SauceVersionEntity.kt
core/database/.../entity/SauceComponentEntity.kt
core/database/.../entity/KitchenRelationEntity.kt
core/database/.../dao/SauceVersionDao.kt
core/database/.../dao/SauceComponentDao.kt
core/database/.../dao/KitchenRelationDao.kt
core/database/.../dao/ImageAssetDao.kt
core/database/src/androidTest/.../CulinaryDatabaseMigrationTest.kt

core/database/src/test/.../CulinaryMigrationV1ToV2Test.kt
core/database/src/test/.../CulinaryMigrationV2ToV3Test.kt
core/database/src/test/.../CulinaryMigrationV3ToV4Test.kt
core/database/src/test/.../CulinaryRollbackV1Test.kt
core/database/src/test/.../DatabaseFoundationTest.kt
core/database/src/test/.../SchemaExportTest.kt

core/data/src/test/.../BackupRestoreV4Test.kt
core/data/src/test/.../QuiescedDatabaseSnapshotterTest.kt
core/data/src/test/.../RestorePrecheckTest.kt
core/data/src/test/.../DebugInfoProviderTest.kt
core/data/src/test/.../FakeRepositories.kt
core/data/src/test/.../StartupCoordinatorTest.kt
```

### 8.3 文档 / 证据

```text
docs/22-sauce-editor-001/REVIEW.md
docs/23-sauce-editor/REPORT.md
docs/23-sauce-editor/*.png / *.xml
build/backup/v4-pre-sauce-editor/*
```

---

## 9. 未完成项与边界

1. **图片不进入现有备份 ZIP**  
   任务禁止修改现有备份机制。`BackupArchiveWriter` 支持附件，但当前生产代码没有收集 `image_asset` 的附件源；因此新图片落本地，不会被现有备份自动打包。  
   若需要“备份包含酱汁图片”，需另行授权增加最小的图片附件收集器。

2. **研发备注不是独立记录表**  
   按已批准的“不新增表”边界，文本使用 `sauce.adjustable_notes`，照片使用 `REFERENCE` 图片；没有“每条备注对应多张图片”的独立记录结构。

3. **原料不允许自由文本新建食材**  
   按确认的方案，原料必须关联既有食材，名称只做当前配方覆盖；不会因为输入新名称而创建食材档案。

4. **难度是受控枚举**  
   仅 `EASY / MEDIUM / HARD`，没有开放式难度文本。

5. **图片不可用时降级**  
   文件丢失时按“无图”显示，不阻塞档案读取；不会伪造占位图片。

---

## 10. 结论

本任务已完成酱汁新增/编辑页的专项改造，schema v5 迁移、保存语义、动态列表、拖动排序、图片、关系、备注、归档和未保存保护均已接通正常数据链路。最终 APK 使用原证书签名，已通过 275 个测试、静态检查和模拟器专项验收。
