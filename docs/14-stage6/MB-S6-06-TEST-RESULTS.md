# MB-S6-06 全量测试结果清单

**文档编号**：MB-S6-06  
**版本**：`1.0.0`  
**状态**：`Review`  
**日期**：2026-11-05  
**依据**：MB-S6-001 §3.4；MB-D-006（验收与测试口径）  
**环境**：Windows 10.0.26300 / JDK 21.0.8 (JBR) / Gradle 8.14.3 / AGP 8.13.0 / Android SDK 36  
**被测提交**：Stage 6 发布提交（见 [MB-S6-01](MB-S6-01-EXECUTION-REPORT.md) §13）

---

## 1. 汇总

| 指标 | 数值 |
|---|---|
| 测试报告文件（TEST-*.xml） | 154 |
| 执行次数（debug + release 变体） | **857** |
| 唯一用例数（class + name 去重） | **540** |
| 失败 / 错误 | **0** |
| 跳过 | **0** |
| 测试类 | **96** |
| 含测试的模块 | **13** |

> 执行次数为 debug 与 release 两个单元测试变体之和；唯一用例数按 `类名 + 方法名` 去重。
> `feature:settings` 当前没有测试源码，`core:*` 的 UI 测试位于 `app` 与各 feature 模块。

## 2. 命令

```powershell
# 本地发布流水线（scripts/build-release.ps1 内部执行同样的四个任务）
.\gradlew.bat test assembleRelease ktlintCheck detekt --console=plain
```

## 3. 按模块

| 模块 | 执行 | 唯一用例 | 失败 |
|---|---|---|---|
| `app` | 49 | 33 | 0 |
| `core:common` | 9 | 9 | 0 |
| `core:data` | 196 | 98 | 0 |
| `core:database` | 162 | 81 | 0 |
| `core:designsystem` | 20 | 10 | 0 |
| `core:domain` | 168 | 168 | 0 |
| `core:platform` | 52 | 26 | 0 |
| `feature:ingredients` | 19 | 11 | 0 |
| `feature:inventory` | 27 | 16 | 0 |
| `feature:products` | 16 | 8 | 0 |
| `feature:recipes` | 19 | 11 | 0 |
| `feature:sauces` | 18 | 11 | 0 |
| `feature:scan` | 102 | 58 | 0 |

## 4. 按测试类

| 模块 | 测试类 | 执行 | 唯一用例 | 失败 |
|---|---|---|---|---|
| `app` | `CrashLogWriterTest` | 6 | 3 | 0 |
| `app` | `HomeScreenTest` | 6 | 6 | 0 |
| `app` | `HomeViewModelTest` | 8 | 4 | 0 |
| `app` | `LibraryScreenTest` | 2 | 2 | 0 |
| `app` | `LibraryTabTest` | 6 | 3 | 0 |
| `app` | `CulinaryDestinationTest` | 12 | 6 | 0 |
| `app` | `NavigationShellTest` | 9 | 9 | 0 |
| `core:common` | `ErrorCodeTest` | 3 | 3 | 0 |
| `core:common` | `OutcomeTest` | 6 | 6 | 0 |
| `core:data` | `AutoBackupRunnerImplTest` | 16 | 8 | 0 |
| `core:data` | `BackupArchiveTest` | 8 | 4 | 0 |
| `core:data` | `BackupConcurrencyTest` | 2 | 1 | 0 |
| `core:data` | `BackupRestoreV2Test` | 2 | 1 | 0 |
| `core:data` | `BackupRoundTripTest` | 4 | 2 | 0 |
| `core:data` | `DatabaseRestoreDrillTest` | 4 | 2 | 0 |
| `core:data` | `QuiescedDatabaseSnapshotterTest` | 8 | 4 | 0 |
| `core:data` | `RestoreExecutorTest` | 12 | 6 | 0 |
| `core:data` | `RestoreJournalTest` | 10 | 5 | 0 |
| `core:data` | `RestorePrecheckTest` | 14 | 7 | 0 |
| `core:data` | `WalBackupRestoreTest` | 4 | 2 | 0 |
| `core:data` | `WriteBarrierTest` | 10 | 5 | 0 |
| `core:data` | `DebugInfoProviderTest` | 2 | 1 | 0 |
| `core:data` | `LocalImageStorageTest` | 8 | 4 | 0 |
| `core:data` | `DefaultHomeSummaryReaderTest` | 10 | 5 | 0 |
| `core:data` | `CalculationRecordRepositoryTest` | 10 | 5 | 0 |
| `core:data` | `IngredientRepositoryTest` | 10 | 5 | 0 |
| `core:data` | `RecipeRepositoryTest` | 8 | 4 | 0 |
| `core:data` | `RepositoryContractTest` | 20 | 10 | 0 |
| `core:data` | `SauceRepositoryTest` | 10 | 5 | 0 |
| `core:data` | `StartupCoordinatorTest` | 10 | 5 | 0 |
| `core:data` | `BusyRetryPolicyTest` | 6 | 3 | 0 |
| `core:data` | `RoomTransactionRunnerTest` | 8 | 4 | 0 |
| `core:database` | `CulinaryMigrationV1ToV2Test` | 6 | 3 | 0 |
| `core:database` | `CulinaryRollbackV1Test` | 2 | 1 | 0 |
| `core:database` | `DatabaseFoundationTest` | 16 | 8 | 0 |
| `core:database` | `SchemaExportTest` | 6 | 3 | 0 |
| `core:database` | `CrossCuttingConstraintsTest` | 16 | 8 | 0 |
| `core:database` | `ExecutionConstraintsTest` | 20 | 10 | 0 |
| `core:database` | `IngredientConstraintsTest` | 22 | 11 | 0 |
| `core:database` | `KnowledgeConstraintsTest` | 22 | 11 | 0 |
| `core:database` | `PlanSnapshotConstraintsTest` | 24 | 12 | 0 |
| `core:database` | `ProcurementConstraintsTest` | 20 | 10 | 0 |
| `core:database` | `UnitDaoTest` | 8 | 4 | 0 |
| `core:designsystem` | `DesignSystemTokensTest` | 20 | 10 | 0 |
| `core:domain` | `ScanCaptureTest` | 5 | 5 | 0 |
| `core:domain` | `ScanFrameGateTest` | 8 | 8 | 0 |
| `core:domain` | `DomainModelInvariantsTest` | 15 | 15 | 0 |
| `core:domain` | `IngredientNamesTest` | 4 | 4 | 0 |
| `core:domain` | `InventoryDeductionTest` | 8 | 8 | 0 |
| `core:domain` | `PurchaseListTest` | 6 | 6 | 0 |
| `core:domain` | `RecipeExploderSauceTest` | 7 | 7 | 0 |
| `core:domain` | `RecipeExploderServingsTest` | 5 | 5 | 0 |
| `core:domain` | `RecipeExploderTest` | 6 | 6 | 0 |
| `core:domain` | `RecipeGraphValidatorTest` | 5 | 5 | 0 |
| `core:domain` | `RecipeVersionPinRuleTest` | 5 | 5 | 0 |
| `core:domain` | `InventoryAllocatorTest` | 3 | 3 | 0 |
| `core:domain` | `RequirementAggregatorTest` | 4 | 4 | 0 |
| `core:domain` | `ShortfallCalculatorTest` | 6 | 6 | 0 |
| `core:domain` | `UnitConverterDisplayTest` | 8 | 8 | 0 |
| `core:domain` | `UnitConverterTest` | 10 | 10 | 0 |
| `core:domain` | `ScanImportKeyTest` | 7 | 7 | 0 |
| `core:domain` | `CalculationRecordTest` | 7 | 7 | 0 |
| `core:domain` | `SnapshotPayloadCodecTest` | 3 | 3 | 0 |
| `core:domain` | `TransactionBoundaryTest` | 4 | 4 | 0 |
| `core:domain` | `SaveIngredientUseCaseTest` | 7 | 7 | 0 |
| `core:domain` | `InventoryQueriesTest` | 3 | 3 | 0 |
| `core:domain` | `RequirementPlanUseCasesTest` | 7 | 7 | 0 |
| `core:domain` | `StockMovementUseCasesTest` | 8 | 8 | 0 |
| `core:domain` | `RecipeUseCasesTest` | 6 | 6 | 0 |
| `core:domain` | `SauceUseCasesTest` | 7 | 7 | 0 |
| `core:domain` | `LookupScanImportUseCaseTest` | 4 | 4 | 0 |
| `core:platform` | `CameraPermissionTest` | 8 | 4 | 0 |
| `core:platform` | `BarcodeFormatsTest` | 4 | 2 | 0 |
| `core:platform` | `BarcodeParserTest` | 10 | 5 | 0 |
| `core:platform` | `SafBackupStorageTest` | 8 | 4 | 0 |
| `core:platform` | `AutoBackupWorkerTest` | 12 | 6 | 0 |
| `core:platform` | `SchedulerWorkerIntegrationTest` | 2 | 1 | 0 |
| `core:platform` | `WorkManagerBackupSchedulerTest` | 8 | 4 | 0 |
| `feature:ingredients` | `IngredientErrorMessagesTest` | 6 | 3 | 0 |
| `feature:ingredients` | `IngredientsUiTest` | 3 | 3 | 0 |
| `feature:ingredients` | `IngredientsViewModelTest` | 10 | 5 | 0 |
| `feature:inventory` | `InventoryScanStockGatewayTest` | 14 | 7 | 0 |
| `feature:inventory` | `InventoryUiTest` | 5 | 5 | 0 |
| `feature:inventory` | `InventoryViewModelTest` | 8 | 4 | 0 |
| `feature:products` | `ProductScanIngredientGatewayTest` | 16 | 8 | 0 |
| `feature:recipes` | `RecipesUiTest` | 3 | 3 | 0 |
| `feature:recipes` | `RecipesViewModelTest` | 16 | 8 | 0 |
| `feature:sauces` | `SaucesUiTest` | 4 | 4 | 0 |
| `feature:sauces` | `SaucesViewModelTest` | 14 | 7 | 0 |
| `feature:scan` | `ScanConfirmUiTest` | 6 | 6 | 0 |
| `feature:scan` | `ScanConfirmViewModelTest` | 28 | 14 | 0 |
| `feature:scan` | `ScanRoutesTest` | 12 | 6 | 0 |
| `feature:scan` | `ScanUiTest` | 8 | 8 | 0 |
| `feature:scan` | `ScanViewModelTest` | 32 | 16 | 0 |
| `feature:scan` | `BarcodeFormatNamesTest` | 6 | 3 | 0 |
| `feature:scan` | `BarcodeValueMapperTest` | 10 | 5 | 0 |

## 5. 静态检查与构建

| 任务 | 结果 | 说明 |
|---|---|---|
| `ktlintCheck` | 通过 | 覆盖全部模块的 Kotlin 源码与 `*.gradle.kts` 脚本 |
| `detekt` | 通过 | 全部模块 |
| `assembleRelease` | 通过 | R8 + resource shrink；产物见 MB-S6-01 §8.1 |
| `lintVitalRelease` | 通过 | 随 `assembleRelease` 执行 |

> 本轮唯一一次质量门禁失败是 `ktlintKotlinScriptCheck` 对 `app/build.gradle.kts`
> 两处多行表达式缩进的告警；修正后重跑通过。未通过时未产出 APK，避免带瑕疵产物。

## 6. 真机结果清单

| # | 用例 | 结果 | 证据 |
|---|---|---|---|
| T-01 | 升级安装：发布签名 Debug 基线 → `install -r` Release | 通过 | [MB-S6-04](MB-S6-04-UPGRADE-DATA-SAFETY.md) §4 |
| T-02 | 升级后数据完整性：2 食材 / 2 商品 / 3 批次 / 3 流水 | 通过（与升级前 SQLite 一致） | MB-S6-04 §4；`evidence/upgrade-data-before.json` |
| T-03 | 升级后批次 UUID 与升级前逐条一致 | 通过 | `evidence/upgrade-04-before-ledger.png` vs `upgrade-07-after-ledger.png` |
| T-04 | Release 包扫码入库（手工输入 → 新建食材 → 入库） | 通过（`已入库 stage6oil 500 g`） | `evidence/smoke-01-scan-import.png` |
| T-05 | 首页数据刷新（3 商品 / 4 批次 / 3 食材） | 通过 | `evidence/smoke-02-home.png` |
| T-06 | 库存流水（4 条入库，含变化前后与来源） | 通过 | `evidence/smoke-03-ledger.png` |
| T-07 | 桌面图标生效与可启动 | 通过 | `evidence/icon-01-launcher-home.png` |
| T-08 | 崩溃/异常扫描（`adb logcat -d`） | 通过（无 FATAL / SQLiteException） | 本轮 logcat 过滤结果 |
| T-09 | 签名不匹配对照实验（debug 签名 → release 包） | 预期失败（`INSTALL_FAILED_UPDATE_INCOMPATIBLE`） | MB-S6-04 §5 |

## 7. 说明

1. 本轮**没有新增或修改任何测试用例**；857/540 为 Stage 5 之后（含 MB-S5-08 幂等修复）的仓库稳定口径，与 README 记录一致。
2. 真机用例为人工/脚本驱动的冒烟与升级验证，不是 instrumentation 断言；
   `connectedAndroidTest` 未在本轮执行（Stage 5 的 ML Kit 解码用例已单独在真机通过）。
3. 所有证据均为本机原始输出（`adb exec-out screencap -p` 截图、`uiautomator dump` XML、SQLite 只读查询）。
