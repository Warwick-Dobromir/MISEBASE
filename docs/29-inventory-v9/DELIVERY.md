# MB-INV-V9 库存模块测试开发交付报告

**文档编号**：MB-INV-V9-001
**版本**：`1.0.1`（2026-11-10 验收缺陷修复复验）
**状态**：`Completed（AVD 验收通过；真机/实体打印机未验证）`
**日期**：2026-11-10
**依据**：《MISEBASE 库存模块完整开发任务书 V1.0》+《MB-INV-AUDIT-001 现状审计》
**基线**：分支 `restore-v7`，HEAD `2b7411a`（审计时工作区干净）

---

## 1. 本次实现的五个决策落点

| 决策 | 落点 | 代码位置 |
|---|---|---|
| Q1 档案与采购商品分离 | 新表 `inventory_archive` + `inventory_archive_product`；批次/流水新增 `archive_id` 并回填 | `InventoryArchiveEntities.kt`、`CulinaryMigrations.kt` v8→v9 |
| Q2 默认 FIFO | `InventoryBatchDao.listFifoAvailable` 按 `received_at, id` 升序；出库分配器 `FifoBatchAllocator`；FEFO 不再是默认路径 | `InventoryBatchDao.kt`、`IssueUseCases.kt` |
| Q3 酱汁现货统一建模 | `inventory_archive.archive_kind = SAUCE` + `sauce_id`；酱汁批次走同一批次/流水体系 | `InventoryArchiveEntity`、`BuildRecipeIssuePlanUseCase` |
| Q4 通用打印 | Android 系统打印框架（`PrintManager` + `PrintDocumentAdapter` + `PdfDocument`），58/80mm 版式；设备解耦，后续可换 ESC/POS | `feature/inventory/print/` |
| Q5 单用户模式 | 流水/冻结/出库单预留 `operator` 字段，默认 null（本机）；不建用户/角色体系 | `InventoryLedgerEntity.operator`、`InventoryFreezeRecordEntity.operator` |

---

## 2. 数据库 v9

### 2.1 新增表（9）

| 表 | 用途 |
|---|---|
| `inventory_archive` | 库存档案（中/英名、分类、类型、库存单位、最低/目标库存、图片、存储方式/温度、默认库位、备注） |
| `inventory_archive_product` | 档案 ↔ 采购商品（换算比、默认标记；一个商品只归属一个档案） |
| `inventory_freeze_record` | 冻结/解冻留痕（动作、原因、操作者、时间） |
| `inventory_location_transfer` | 库位转移记录（转出/转入、数量、备注） |
| `inventory_issue_slip` | 出库单（单号、类型、菜谱、幂等键、操作者） |
| `inventory_issue_slip_line` | 出库明细（逐批次：档案、批次、库位、数量、单位、角色 INGREDIENT/SAUCE/SAUCE_GAP） |
| `inventory_print_job` | 打印任务（类型、关联单、状态、失败原因、重打次数、纸宽） |
| `inventory_stocktake` | 盘点单（范围、状态、差异数、确认时间） |
| `inventory_stocktake_line` | 盘点明细（账面/实盘、冻结标记） |

### 2.2 既有表变更

- `inventory_batch`：新增 `archive_id`、`production_date`、`frozen_at`、`frozen_reason`、`recycled_at`、`is_temporary`、`temporary_since`；`product_id` 放宽为可空（酱汁现货批次没有采购商品）。
- `inventory_ledger`：新增 `archive_id`、`operator`、`print_job_id`；`product_id` 放宽为可空。
- 批次状态枚举新增 `FROZEN`、`RECYCLED`；流水类型新增 `FREEZE`、`UNFREEZE`、`LOCATION_TRANSFER`（零数量变动，仅留痕）。

### 2.3 迁移与数据安全

- `MIGRATION_8_9`：先移除部分索引 → `PRAGMA defer_foreign_keys = 1` → 重建 `inventory_batch` / `inventory_ledger`（放宽 NOT NULL）→ 建新表与索引 → 回填档案（每个采购商品生成 `arch_<product_id>` 档案并建立关联）→ 旧批次/流水按商品挂到档案。
- 未使用破坏性迁移；旧数据一行不丢；触发器由 `CulinarySchemaExtras.apply` 在 onOpen 按新谓词重建。
- 迁移测试：`CulinaryMigrationV8ToV9Test`（旧批次/流水保留、`product_id` 可空、冻结可写、流水仍不可改、9 张新表建立）。
- Schema 导出：`core/database/schemas/.../9.json`（47 表）；`SchemaExportTest` 已锁定 v9。
- `StartupCoordinator.EXPECTED_SCHEMA_VERSION` 同步为 9，避免升级后被误判为"更高版本"。

---

## 3. 领域逻辑

### 3.1 出库（三层 + FIFO + 原子）

`BuildRecipeIssuePlanUseCase`：
1. 菜谱直接食材 → 找到对应档案（经档案-商品关联反查食材）→ 换算到档案库存单位；
2. 酱汁组件 → 找 `sauce_id` 对应现货档案 → 先扣现货批次；
3. 现货不足 → 缺口按酱汁版本 `min_range_qty` 向上取整 → 只对制作量展开配方 → 基础食材进入 `SAUCE_GAP` 行；
4. 全部行再经 `FifoBatchAllocator` 按入库时间分配批次，冻结批次不参与，同一批次不被重复占用。

`ConfirmIssueUseCase`：在事务内重新分配 → 任一行不足即失败（不写任何流水）→ 全部通过后写流水、出库单与明细、打印任务；幂等键防止撕单动画/重复点击二次扣减。

### 3.2 其他

- 手工出库：`BuildManualIssuePlanUseCase`（数量/单位换算 + FIFO + 可选指定批次）。
- 入库：`RecordArchiveReceiptUseCase`（建批次余额 0 → 追加流水 → 打印任务，同一事务；默认库位取档案默认库位）；`RecordPurchaseReceiptUseCase`（多行待确认单，确认时整单事务）。
- 盘点：`StocktakeUseCases`（快照 → 报告 → 确认后逐行写调整流水；冻结批次可盘点）。
- 冻结/解冻/转移：写记录 + 状态 + 零变动流水；冻结批次不出库。
- 回收站：归零批次 30 天保留、可恢复；到期由维护标记为已报废，不删除历史；不提供手动清理。
- 临时库存：15 天提醒、20 天待处理，阈值读取 `app_metadata` 规则；不自动删除。
- 规则：FIFO 固定生效；临时库存/回收天数可改且真实影响业务。
- 流水：只追加；类型 + 时间紧凑展示；可按档案/批次/单据查询。

---

## 4. 页面（任务书 §20 的 30 个页面）

| # | 页面 | 路由 / 实现 |
|---|---|---|
| 01 | 库存首页 | `inventory`（概况/三大操作/分类目录/最近记录/三点） |
| 02 | 分类目录 | `inventory/category?categoryId=` |
| 03 | 分类管理 | `inventory/categories` |
| 04 | 库存物品列表 | `inventory/archives?filter=` |
| 05 | 档案详情 | `inventory/archive/{archiveId}` |
| 06–07 | 档案新建 / 编辑 | `inventory/archive/edit?...`（同一页） |
| 08 | 批次列表 | `inventory/archive/{archiveId}/batches` |
| 09 | 批次详情 | `inventory/batch/{batchId}`（冻结/解冻/转移/标签） |
| 10–11 | 手工入库 / 采购入库确认 | `inventory/receipt`、`inventory/purchase`（同页两模式） |
| 12 | 入库打印预览 | `inventory/print/RECEIPT/{batchId}` |
| 13–16 | 出库单 / 不足提示 / 撕单 / 出库打印预览 | `inventory/issue`、`inventory/print/ISSUE/{slipId}` |
| 17–18 | 盘点 / 差异报告 | `inventory/stocktake`（报告与确认同页） |
| 19–21 | 冻结管理 / 冻结详情 / 解冻 | `inventory/freeze`、`inventory/batch/{batchId}` |
| 22 | 临时库存 | `inventory/temporary` |
| 23–24 | 库位管理 / 库位转移 | `inventory/locations`、批次详情内转移 |
| 25–26 | 库存流水 / 流水详情 | `inventory/ledger-v9`（行内展开详情） |
| 27 | 批次回收站 | `inventory/recycle` |
| 28 | 库存规则设置 | `inventory/rules` |
| 29 | 批次标签预览 | `inventory/print/LABEL/{batchId}?width=58` |
| 30 | 库存管理 | `inventory/management`（三个编号区域、六个入口） |

旧入口（商品维度详情、旧流水、旧出入库表单、需求计划）保留在导航中，未删除。

---

## 5. 扫码改造（任务书 §24）

- 扫码确认页不再写库存：`ScanConfirmViewModel.handoff()` 只返回条码、建议档案名与数量；
- 主按钮改为「下一步：建立库存档案」，页面明确提示"识别成功不等于入库"；
- app 导航把交接结果带到库存「新建档案」页（名称/条码预填），之后由用户在库存模块填写数量并确认入库。
- 原 `ScanStockGateway` 与幂等入库实现保留（既有测试仍覆盖），但不再是扫码主路径。

---

## 6. 打印（任务书 §18）

- `PrintManager` + `PrintDocumentAdapter` + `PdfDocument`，页面尺寸按 58/80mm 自定义（`MediaSize` 用 mil 换算）；
- 单列、黑白、中文系统字体、按宽度自动换行、内容决定页面高度、超出走多页；
- 打印任务落 `inventory_print_job`；失败只改任务状态（`FAILED` + 原因），不回滚库存、不新增流水；重打只更新原任务（`reprint_count`），不重复扣减。
- 真机打印验证需要支持打印服务的设备；本轮以预览与任务状态机验证为主，实际纸面输出标记为"未验证"（见 §9）。

---

## 7. 修改范围

> 以 2026-11-10 `git status` 为准（HEAD `2b7411a`，分支 `restore-v7`，尚未提交）：**修改 48 个文件、新增 52 个路径、删除 0**，共 100 个路径。新增中的 `feature/inventory/.../print/` 是目录条目，实际含 3 个文件。

### 7.1 按模块

| 模块 | 修改 | 新增 | 关键文件 |
|---|---:|---:|---|
| `app` | 1 | 0 | `CulinaryNavHost.kt`（v9 路由 + 本页签发的 `onTemporary` 入口） |
| `core/database` | 19 | 12 | `CulinaryDatabase.kt`、`CulinaryMigrations.kt`、`InventoryArchiveEntities.kt`、`InventoryIssueEntities.kt`、`InventoryLifecycleEntities.kt`、`InventoryStocktakeEntities.kt`、`InventoryEnums.kt`、9 个 DAO、`schemas/.../9.json`、`CulinaryMigrationV8ToV9Test.kt` |
| `core/domain` | 10 | 14 | `InventoryArchive.kt`、`InventoryIssue.kt`、`InventoryOps.kt`、`InventoryCommands.kt`、`InventoryBatchRecord.kt`、`InventoryArchiveRepository.kt`、`ArchiveUseCases.kt`、`IssueUseCases.kt`、`ReceiptUseCases.kt`、`MaintenanceUseCases.kt`、`CategoryUseCases.kt` 与 3 个新测试 |
| `core/data` | 11 | 3 | `InventoryArchiveRepositoryImpl.kt`、`InventoryArchiveMapper.kt`、`InventoryArchiveUseCaseModule.kt`、`InventoryMapper.kt`、DI、`StartupCoordinator.kt`、4 个 v9 版本断言测试 |
| `feature/inventory` | 2 | 21 | 19 个页面文件 + `InventoryText.kt` + `print/`（目录条目，含 3 个文件）；`InventoryRoutes.kt`、`InventoryUiState.kt` |
| `feature/scan` | 5 | 0 | 扫码确认只交接、不写库存（`ScanConfirm*`） |
| `docs` | 0 | 2 | `docs/28-inventory-audit/`、`docs/29-inventory-v9/` |

### 7.2 本轮集成做的改动（除三组并行任务外的增量）

1. **等价修复（编译/测试契约，不改业务逻辑）**：
   - `core/database` 6 个测试（V1→V2、V2→V3、V3→V4、V4→V5、Rollback、Foundation）把 schema 断言从 v8 改为 v9，并给前四个迁移链补 `MIGRATION_8_9`；`SchemaExportTest` 的 v9 断言已在并行任务中完成；
   - `core/data` 4 个测试（`BackupRestoreV4Test`、`QuiescedDatabaseSnapshotterTest`、`DebugInfoProviderTest`、`StartupCoordinatorTest`）同步 v9 版本断言；`rejects a database created by a newer app` 的"更高版本"从 9 改为 10。
   - 这些只改测试期望值，不触碰迁移 SQL、库存计算或页面交互。
2. **导航契约修复（非新功能）**：`InventoryTemporaryScreen` 与 `InventoryRoutes.TEMPORARY` 早已存在，但全工程没有任何 `navigate(TEMPORARY)`，导致页面不可达。本次仅在 `InventoryManagementScreen` 的「03 库存规则」下增加「临时库存」入口，并在 `CulinaryNavHost` 接线；页面本体、状态与阈值逻辑未改。

### 7.3 未改动的边界

数据库 v9 迁移、旧入口（商品维度详情/旧流水/旧出入库/需求计划）、需求计划页、酱汁/菜谱界面、扫码"不写库存"语义、事务/幂等、打印失败不回滚、盘点确认才写库存、页面不合并大 Screen——均保持原实现。

### 7.4 本轮验收缺陷修复（2026-11-10，仅两处代码）

| 文件 | 改动 | 等价性说明 |
|---|---|---|
| `feature/inventory/.../InventoryLedgerV9Screen.kt` | 流水行不再用 `entry.printJobId` 判状态，改为按 `referenceType + referenceId`（出库 `issue_slip`/单据 ID，入库 `stock_receipt`/批次 ID）用 `PrintJobUseCases.latest` 解析打印任务；无任务显示「无打印任务」，FAILED 红字「打印失败」+「重新打印」，PRINTED「已打印」，PENDING「待打印」；`reprint` 按解析出的任务 ID 回写行状态。 | 不改数据库、不回填流水、不改流水写入语义；重打仍只调用 `PrintJobUseCases.reprint`（仅 `print_job` 状态 + `reprint_count`），不扣库存、不新增流水。 |
| `feature/inventory/.../InventoryArchiveScreen.kt` | 档案详情「04 关联采购商品」增加「＋ 关联采购商品」对话框（商品列表来自 `ListArchiveFormOptionsUseCase().products`、换算比 >0、默认开关 → `LinkArchiveProductUseCase`）与每行「解除关联」（`UnlinkArchiveProductUseCase`）；空态「尚未关联采购商品」。 | 不新建页面、不加路由；沿用现有 `ArchiveModuleHeader`/`CulinaryRule`/`CulinaryTextButton` 的编辑式极简视觉，无卡片。列表仍来自档案详情聚合的 `archiveLinks`（与 `ListArchiveProductLinksUseCase` 同一仓储方法）。 |

其余改动仅为文档与证据更新。

---

## 8. 测试

- 数据库：v8→v9 迁移、Schema 导出、既有约束/DAO 测试。
- 领域：三层出库（现货足够/不足/为零/最小制作容量取整）、FIFO 与冻结过滤、整单失败不写流水、盘点调整流水。
- 页面：Compose UI 测试与 ViewModel 测试（按模块现有测试体系补充）。
- 回归：食材库、酱汁库、菜谱库、扫码识别、数据库升级、离线、备份恢复。

> 具体通过数、失败数与未验证项在 §9 回填；未实际执行的测试一律标注"未验证"，不写成通过。

---

## 9. 交付回填（构建后填写）

| 项目 | 结果 |
|---|---|
| 编译 | `:feature:inventory:compileDebugKotlin`、`:app:assembleDebug`、`:app:assembleRelease`（R8）均 **BUILD SUCCESSFUL**，0 error；仅有 Kotlin `KT-73255` 注解目标告警（既有代码，不影响产物）。 |
| 单元测试 | 验收修复轮强制重跑（`--rerun-tasks`）**454** 个用例：**454 passed / 0 failed / 0 skipped**（`feature:inventory` 16、`core:domain` 192、`core:database` 184、`app` 62）。未重跑全量 `test`；首轮全量基线为 661 个（660 passed / 1 个既有 release 变体环境失败，见 §9.1 注）。 |
| APK 路径（推荐交付） | `app/build/outputs/apk/release/app-release.apk`（**release 签名**，2026-11-10 21:5x 构建） |
| APK 大小 | **25,007,538 bytes**（约 23.85 MiB，R8 压缩后） |
| APK SHA-256 | `70E0596B3807266E2FB92E13E65DFB0E07C46F0F8845F50E70E2DC36B68CE5F6` |
| 签名校验 | `apksigner verify --print-certs`：`CN=MISEBASE Release, OU=Culinary, O=MISEBASE, L=Hong Kong, ST=Hong Kong, C=HK`；证书 SHA-256 `5ca41959d9f450cdc851dcb9f052ac9130aaeab71c64062173a699355056af61`，与 `KEYSTORE-CREDENTIALS.txt` 记录一致；keystore 文件 SHA-256 `34538C3D…77CA` 与交付包内密钥一致（本地 `D:\MISEBASE_RELEASE\release.keystore` 与交付目录为同一文件）。 |
| 调试包（仅内部验证） | `app/build/outputs/apk/debug/app-debug.apk`，40,650,772 bytes，SHA-256 `518C8C7A…A76D`；**debug 签名，不可作为发布/升级包**，AVD 验收使用的是这一包。 |
| 数据库版本 | **v9，47 张实体表**（设备内 `PRAGMA user_version = 9`；`sqlite_master` 实体表 47，另有 `android_metadata`/`room_master_table`）。设备侧核对通过。 |
| 截图 | `docs/29-inventory-v9/evidence/`（50+ 个证据文件：PNG + MP4 + README + 数据库核对 TXT；环境与 APK 指纹见该目录 README）。 |
| 未验证项 | 真机运行、真机系统打印服务、实体热敏打印机 58/80mm 纸面输出、蓝牙/USB/ESC-POS、多用户权限、云端同步。release 包的启动冒烟见 §9.2 说明。 |

### 9.2 发布包签名与启动冒烟（2026-11-10 更正）

此前交付的 `app-debug.apk` 是 **debug 签名**，只能用于模拟器验收，不能作为发布/覆盖升级包。已按要求改用项目发布密钥重新构建：

- 构建：`.\gradlew.bat assembleRelease` → **BUILD SUCCESSFUL**（R8 `minifyReleaseWithR8` + `packageRelease`）
- 产物：`app/build/outputs/apk/release/app-release.apk`，**25,007,538 bytes**
- SHA-256：`70E0596B3807266E2FB92E13E65DFB0E07C46F0F8845F50E70E2DC36B68CE5F6`
- 签名：`apksigner verify --print-certs` → `CN=MISEBASE Release, OU=Culinary, O=MISEBASE, L=Hong Kong, ST=Hong Kong, C=HK`；证书 SHA-256 `5ca41959d9f450cdc851dcb9f052ac9130aaeab71c64062173a699355056af61`，与 `KEYSTORE-CREDENTIALS.txt` 第 20–22 行记录一致
- 密钥来源：`D:\MISEBASE_RELEASE\release.keystore` 与交付目录 `D:\MISEBASE-DELIVERY\03-keys\MISEBASE-Credentials\release.keystore` **文件 SHA-256 相同**（`34538C3D…77CA`），`local.properties` 指向前者，四个键值齐全
- 启动冒烟（AVD `Medium_Phone_API_36.1`）：先 `adb uninstall com.culinary`（debug 包签名不同，直接覆盖会报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`），再安装 release 包 → 启动 → `pidof com.culinary` 返回 3956，`logcat` 无 `FATAL EXCEPTION` / `ClassNotFoundException` / `NoSuchMethodError` / `VerifyError`；`dumpsys package` 显示 `versionCode=9`、`versionName=1.0.8`
- 截图：`evidence/15-release-home.png`（release 包实际渲染，全新安装因此无库存数据）

> 注意：release 冒烟是**全新安装**（卸载 debug 包导致数据清空），只证明 release 包能启动与渲染；V9 业务验收（撕单、盘点、冻结、打印失败等）是在 debug 包上完成的，两者构建类型不同但业务代码相同。

### 9.1 分模块测试结果

| 模块 | 任务 | tests | passed | failed | skipped |
|---|---|---:|---:|---:|---:|
| `core:domain` | `:core:domain:test` | 192 | 192 | 0 | 0 |
| `core:database` | `:core:database:testDebugUnitTest` | 92 | 92 | 0 | 0 |
| `core:database` | `:core:database:testReleaseUnitTest` | 92 | 92 | 0 | 0 |
| `core:data` | `:core:data:testDebugUnitTest` | 100 | 100 | 0 | 0 |
| `core:common` | `:core:common:test` | 16 | 16 | 0 | 0 |
| `core:designsystem` | debug / release | 10 / 10 | 10 / 10 | 0 | 0 |
| `core:platform` | `:core:platform:testDebugUnitTest` | 26 | 26 | 0 | 0 |
| `core:platform` | `:core:platform:testReleaseUnitTest` | 26 | 25 | **1** | 0 |
| `feature:ingredients` | debug / release | 11 / 8 | 11 / 8 | 0 | 0 |
| `feature:inventory` | `:feature:inventory:testDebugUnitTest` | 16 | 16 | 0 | 0 |
| `app` | `:app:testDebugUnitTest` | 62 | 62 | 0 | 0 |

> 其余 feature 模块（products/recipes/sauces/settings/scan）本轮 `test` 未产生单测报告；不写成通过。
>
> 本轮（2026-11-10 验收缺陷修复）按要求强制重跑：`feature:inventory:testDebugUnitTest` 16、`:core:domain:test` 192、`:core:database:test` 184（debug 92 + release 92）、`:app:testDebugUnitTest` 62，合计 **454 / 454 passed**；其余行为首轮基线，未在本轮重跑。`core:domain` / `core:database` 的既有断言未改动。首轮全量 `test` 的 661 个用例基线仍记录在案，其中唯一失败为 `core:platform:testReleaseUnitTest` 的 `WorkManagerBackupSchedulerTest > enqueues an immediate backup and returns its id`（Robolectric 无 Worker runner，release 变体稳定复现，属既有环境性失败，非 v9 改动；debug 变体通过），本轮未重跑该任务。

### 9.2 AVD 验收结果摘要

| 场景 | 结果 | 关键证据 |
|---|---|---|
| a 库存首页 | 通过（1/1/0/0、三大操作、分类目录、最近记录、右上「···」） | `01-inventory-home.png`、`01-home-recent.png` |
| b 分类管理 | 通过（新建大分类 → 子分类 → 改名 → 上移/下移 → 删除空分类） | `02-category-manage.png` |
| c 档案新建/详情/编辑/筛选 | 通过；改备注后图片 URI 保留；「正常」0 条/「不足」1 条 | `03-*.png` |
| d 一个档案关联两个采购商品 | **通过（UI）**：档案详情「04 关联采购商品」显示 2 个商品（Tomato Case 默认 / Tomato Box，含换算比与「解除关联」）；「＋ 关联采购商品」对话框可从 6 个采购商品中选择、填换算比、设默认；点「解除关联」后剩 1 个，重新关联后恢复 2 个（Tomato Box 换算比 2）。 | `14-archive-links-two.png`、`14-archive-link-dialog.png`、`14-archive-unlink.png`、`14-archive-relink.png` |
| e 手工入库/批次/冻结/解冻/转移/标签 | 通过（入库 1000 g → 冻结 → 解冻 → Dry Store→Cold Room → 58mm 标签预览） | `04-receipt.png`、`05-*.png` |
| f 出库/回弹/撕单/打印预览 | 回弹与撕单通过；**「前两项 + 固定两行」已复现**：用 4 种物料的「普罗旺斯风味炖菜」走菜谱出库（4 份），出库单只显示番茄 250 g、胡萝卜 100 g，随后固定两行「其他物料 / 请查看具体打印单或库存流水」，并显示「物料档案数 4」；撕单后打印预览列全 4 项。 | `13-issue-multi-material.png`、`13-issue-print-preview.png`、`13-tear-multi.mp4`、`07-issue-rebound-*.png`、`07-issue-tear-drag.png` |
| g 库存不足 | 通过（900 > 800，底部面板 + 撕单无效 + 库存保持 800） | `08-insufficient.png` |
| h 撕单前后对照 | 通过：BATCH-001 剩余 **1000 g → 800 g**；档案流水 **4 → 5 条**；出库单号 **ISS-20261109-235510-B06D**（id `7081aa29-…`）；`inventory_ledger` 的 `idempotency_key` 唯一索引无重复。 | `07-issue-after-stock.png`、`07-batch-after-issue.png`、DB 导出 |
| i 盘点 | 通过（报告 800 → 实盘 700 未确认 → 确认后 -100 盘亏流水，批次 700） | `09-stocktake-*.png` |
| j 临时库存/库位/流水/回收站/规则 | 临时库存与规则联动通过；库位/回收站/流水有截图。**流水页打印状态已修复**：失败任务显示红字「打印：打印失败」，展开详情有「打印状态 打印失败」+「重新打印」；点击后任务变「待打印」、提示「已重新加入打印队列，库存余额不变。」。 | `10-temporary*.png`、`10-rules.png`、`10-recycle.png`、`06-locations.png`、`12-ledger-print-failed.png`、`12-ledger-failed-detail.png`、`12-ledger-reprint-pending.png` |
| k 打印预览 58/80mm + 失败 | 58/80mm 预览通过；禁用 `com.android.printspooler` 后点打印显示「打印失败，库存已记账，可重新打印」，批次仍 700 g。 | `05-print-58.png`、`05-print-80.png`、`11-print-failure.png` |
| l 本轮缺陷修复数据核对 | 4 种物料出库：4 条出库明细 = 4 条流水（1:1）；批次 2000→1750 g、1000→900 g、10→9 个、50→48 g；重打前后 `inventory_ledger` 保持 12 条、余额不变，打印任务 FAILED→PENDING（`reprint_count=1`）。 | `db-summary-v9-fix.txt` |

### 9.3 本轮三个验收缺陷的修复前后对照

| 缺陷 | 修复前 | 修复后（AVD 实测） | 证据 |
|---|---|---|---|
| 1 流水打印状态 | 流水行用 `printJobId` 判断，出库/入库流水写入时任务尚未创建，`print_job_id` 恒 NULL，页面永远「无打印任务」 | 按 `reference_type + reference_id` 反查打印任务：FAILED 显示红色「打印失败」+「重新打印」，点击后 PENDING 并提示余额不变；PRINTED「已打印」、PENDING「待打印」、无任务「无打印任务」 | `12-ledger-print-failed.png`、`12-ledger-failed-detail.png`、`12-ledger-reprint-pending.png` |
| 2 出库单前两项 | 只有 1 行手工出库；菜谱无 ingredient→archive 映射，返回 `CUL-VAL-001`，溢出分支未出现 | 4 种物料菜谱出库成功：屏幕仅前两项 + 固定两行「其他物料」「请查看具体打印单或库存流水」（纯文本、不可点击），完整数量「物料档案数 4」；撕单后 4 项全部扣减、4 条流水 = 4 条明细 | `13-issue-multi-material.png`、`13-issue-print-preview.png`、`13-tear-multi.mp4` |
| 3 采购商品绑定 UI | 档案详情只有展示区，无关联/解除入口；上轮靠直接写 DB | 详情「04 关联采购商品」可展示、解除、重新关联；对话框从 `ListArchiveFormOptionsUseCase().products` 选商品、填换算比（>0）、设默认 | `14-archive-links-two.png`、`14-archive-link-dialog.png`、`14-archive-unlink.png`、`14-archive-relink.png` |

---

## 10. 已知限制与未决

1. 采购入库"待确认单"在页面内以草稿形式存在，未跨进程持久化（确认前不改库存这一点成立）。
2. 打印使用 Android 系统打印框架，实际设备需具备打印服务；蓝牙 ESC/POS / USB / 厂商 SDK 适配接口未实现。
3. 操作人员仅为字段预留（单用户模式），没有登录、角色与权限。
4. 批次"拆分/分装"未实现（任务书未列入本期页面与验收项）。
5. 盘点范围按页面选择（全部/分类/库位/档案），报告确认后批量调整；并发出入库的锁粒度依赖 Room 事务。
6. 需求计划（旧入口）保留原商品维度扣减逻辑，与新出库单并存；两者写同一批次余额与追加式流水，未做统一收口。
7. **采购商品绑定 UI 已补**：档案详情「04 关联采购商品」新增「＋ 关联采购商品」对话框与每行「解除关联」；本轮通过 UI 完成两个商品展示、解除、重新关联（Tomato Box 换算比 2），不再依赖直接写库。测试版约束「一个商品只归属一个档案」仍在，换绑需先解绑。
8. **菜谱出库溢出分支已复现**：为「普罗旺斯风味炖菜」（4 种物料）准备 4 个档案、4 个采购商品、ingredient→product→archive 映射与批次后，4 份菜谱出库成功；出库单只显示前两项 + 固定两行，显示「物料档案数 4」，撕单后 4 条流水 = 4 条明细。该验收数据集为直接写库准备（任务书允许），真实用户仍需在 UI 逐项建档案并绑定商品。
9. **流水打印状态已修**：不再依赖未回填的 `inventory_ledger.print_job_id`，改按 `reference_type + reference_id` 反查 `inventory_print_job`。出库流水显示「打印失败」并可重打；重打只改任务状态（FAILED→PENDING、`reprint_count+1`），库存与流水条数不变。`print_job_id` 继续保持 NULL（append-only 流水不回填），属设计选择。
10. **临时库存页导航缺口已补**：页面与路由原本存在但无任何 `navigate(TEMPORARY)`，本次在库存管理页补入口后才可达（§7.2）；页面本体未改。
11. 打印失败是通过 `pm disable-user com.android.printspooler` 模拟"无打印服务"触发的，错误为 `No Activity found to handle null`；不是真实打印机卡纸/离线/缺纸故障。
12. AVD 为 **Android 模拟器**（`Medium_Phone_API_36.1`，1080×2400，headless + swiftshader），不是真机；真实触控手感、性能、系统打印服务、权限弹窗、后台/杀进程恢复均未验证。
13. 真机热敏打印、实体打印机 58/80mm 纸面输出、蓝牙/USB/ESC-POS、多用户权限、云端同步**均未验证**，不得视为通过。
14. 回归仅覆盖库存与上述路径；扫码主路径（只交接不写库存）、酱汁/菜谱/食材库的完整 UI 回归未在本轮逐项重跑。

---

## 11. 回滚

- 代码：`git revert` 本次提交（或切回 `2b7411a`）。
- 数据库：v9 迁移不可逆；回滚需恢复到 v8 备份（备份体系见 `docs/10-backup`），或保留 v9 库并使用支持 v9 的构建。
- 打印：无设备依赖，删除打印任务不影响库存数据。

---

## 12. 验收证据索引

全部证据在 [`docs/29-inventory-v9/evidence/`](evidence/)，环境、APK 指纹与逐文件说明见 [`evidence/README.md`](evidence/README.md)，首轮库内数字见 [`evidence/db-summary.txt`](evidence/db-summary.txt)，本轮缺陷修复库内数字见 [`evidence/db-summary-v9-fix.txt`](evidence/db-summary-v9-fix.txt)。录屏：[`tear-issue.mp4`](evidence/tear-issue.mp4)、[`13-tear-multi.mp4`](evidence/13-tear-multi.mp4)。

| 证据边界 | 可证明 | 证据 |
|---|---|---|
| **AVD 可证明** | 页面可达、交互流程、Route/参数契约、Room v9 读写、FIFO/整单失败/幂等/确认后写库存、打印任务失败不回滚、流水打印状态按业务引用解析与重打不扣库存、档案-采购商品 UI 绑定、多物料出库前两项+固定两行、库存按完整物料扣减 | 本目录 45 张截图 + 2 个 MP4；设备 DB 导出（`user_version=9`、47 表、`inventory_ledger` 12 条、`inventory_issue_slip` 3 条、`inventory_print_job` 6 条、`idempotency_key` 唯一） |
| **真机可证明** | 真实触控/滚动性能、系统打印服务可用性、权限与通知、进程被杀后的恢复、厂商 ROM 差异 | **未执行**：无真机，AVD 结果不能替代 |
| **实体打印机可证明** | 58/80mm 纸面排版、切纸、打印速度、蓝牙/USB/ESC-POS 适配、缺纸/离线故障 | **未执行**：模拟器只有 `Save as PDF`，`05-print-*.png` 只是预览，`11-print-failure.png` 与 `12-ledger-print-failed.png` 是禁用系统打印服务后的应用级失败提示 |

