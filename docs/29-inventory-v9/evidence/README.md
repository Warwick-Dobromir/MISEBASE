# 库存 V9 验收证据（AVD）

- **环境**：Android Studio AVD `Medium_Phone_API_36.1`（x86_64，Android 16 / API 36.1），1080×2400，无实体打印机（系统仅提供 Save as PDF），`-no-window -gpu swiftshader_indirect` 冷启动。
- **时间**：2026-11-10 07:30–08:20（首轮验收）、09:00–09:20（验收缺陷修复复验），Asia/Shanghai，UTC+08:00。
- **APK**：`app/build/outputs/apk/debug/app-debug.apk`，versionName `1.0.8`，versionCode `9`，40,650,772 bytes，SHA-256 `518C8C7A7C213AB91588F29A1E761445A7BE3E44CF57D021698257334806A76D`，HEAD `2b7411a`（`restore-v7` + 本轮未提交修复）。
- **说明**：本目录全部截图/录屏来自 **Android 模拟器**，不是真机，也不是实体热敏打印机；`Save as PDF` 只证明系统打印框架被调起，不代表纸面输出。

## 截图与录屏索引

| 文件 | 内容 |
|---|---|
| `00-app-home.png` | 应用首页（启动成功） |
| `01-inventory-home.png` | 库存首页：概况 1/1/0/0、三大操作、分类目录、右上「···」 |
| `01-home-recent.png` | 库存首页「最近库存记录」 |
| `02-category-manage.png` | 分类管理：大分类 Vegetables、子分类 Leafy Greens（含重命名/排序/删除后的最终状态） |
| `03-archive-detail.png` | 档案详情（新建后） |
| `03-archive-edit-saved.png` | 编辑备注并保存后，图片 URI 仍在 |
| `03-archive-filter-normal.png` | 库存物品筛选「正常」（0 个） |
| `03-archive-filter-out.png` | 库存物品筛选「不足」（1 个） |
| `04-archive-products.png` | 档案详情「关联采购商品」显示 2 个（Tomato Case 默认 / Tomato Box） |
| `04-receipt-product-picker.png` | 手工入库「来源采购商品」下拉列出同一档案的 2 个商品 |
| `04-receipt.png` | 手工入库：数量、批次号、生产/到期、库位 Dry Store |
| `04-archive-with-stock.png` | 入库后档案详情：批次、余额、流水 |
| `05-batch-frozen.png` | 批次详情：已冻结 |
| `05-batch-transferred.png` | 批次详情：库位已转 Cold Room（解冻后） |
| `05-label-58.png` | 批次标签 58mm 预览 |
| `05-print-58.png` | 入库单打印预览 58mm |
| `05-print-80.png` | 入库单打印预览 80mm |
| `05-print-no-service.png` | 模拟器系统打印面板（无实体打印机） |
| `07-issue-rebound-drag.png` | 出库单向下拖动未达阈值（拖动中） |
| `07-issue-rebound-after.png` | 松手回弹、未撕单（库存未变） |
| `07-issue-tear-drag.png` | 出库单拖动超过阈值（拖动中） |
| `07-issue-print-preview.png` | 撕单成功后进入打印预览（单号 7081AA29-7B9） |
| `07-issue-after-stock.png` | 撕单后档案详情：批次剩余 800 g、流水 5 条 |
| `07-batch-after-issue.png` | 撕单后批次详情：剩余 800、累计出库 200 |
| `08-insufficient.png` | 出库数量 900 > 可用 800：底部「库存不足，无法出库」 |
| `09-stocktake-report.png` | 盘点报告生成（账面 800） |
| `09-stocktake-draft.png` | 只改实盘 700、未确认（确认前库存不变） |
| `09-stocktake-confirmed.png` | 确认后差异 -100、盘亏、已确认 |
| `10-temporary.png` | 临时库存页（0 天，正常） |
| `10-temporary-remind.png` | 规则默认 15 天时，16 天临时批次显示「需处理」 |
| `10-temporary-after-rule.png` | 提醒天数改为 30 后，同一批次显示「正常」 |
| `10-rules.png` | 规则设置保存成功 |
| `10-ledger.png` | 首轮库存流水详情；当时缺陷 1 未修复，打印状态显示「无打印任务」（修复后证据见 `12-ledger-*.png`） |
| `10-recycle.png` | 批次回收站（空） |
| `11-print-failure.png` | 禁用打印服务后点「打印」：失败警告 + 重新打印，库存仍 700 |
| `tear-issue.mp4` | 撕单拖动→撕断→进入打印预览的 15s 录屏 |
| `db-summary.txt` | 设备库导出后的 schema/批次/流水/出库单/打印任务/幂等键核对数字 |

### 验收缺陷修复新增（2026-11-10 09:00–09:20）

| 文件 | 内容 |
|---|---|
| `12-ledger-print-failed.png` | 库存流水：4 条多物料出库流水均显示红字「打印：打印失败」，旧出库流水显示「打印：已打印」 |
| `12-ledger-failed-detail.png` | 展开失败流水：来源单号 `issue_slip / f0e38494…`、打印状态「打印失败」、红色「打印失败」标记与「重新打印」按钮 |
| `12-ledger-reprint-pending.png` | 点「重新打印」后：提示「已重新加入打印队列，库存余额不变。」、打印状态变「待打印」、余额仍 50 → 48 g |
| `13-issue-multi-material.png` | 多物料菜谱出库单：只显示前两项（番茄 250 g、胡萝卜 100 g）+ 固定两行「其他物料 / 请查看具体打印单或库存流水」+「物料档案数 4」 |
| `13-issue-print-preview.png` | 撕单后出库打印预览：4 项物料（番茄 250 g、胡萝卜 100 g、黄洋葱 1 个、百里香 2 g），证明完整物料参与扣减 |
| `13-tear-multi.mp4` | 多物料出库单撕单→提交→打印预览的录屏 |
| `14-archive-links-two.png` | 档案详情「关联采购商品」：2 个商品（Tomato Case 默认、Tomato Box），每行「解除关联」，下方「＋ 关联采购商品」 |
| `14-archive-unlink.png` | 点「解除关联」后：只剩 1 个商品（Tomato Case） |
| `14-archive-link-dialog.png` | 「＋ 关联采购商品」对话框：商品列表、换算比输入（1 个商品 = ? g）、默认开关 |
| `14-archive-relink.png` | 重新关联后：2 个商品恢复（Tomato Box 换算比 2） |
| `db-summary-v9-fix.txt` | 本轮缺陷修复后的设备库核对：4 行明细/4 条流水/打印任务 FAILED→PENDING、批次余额、关联数 |

- 缺陷 1 的 FAILED 任务通过 `pm disable-user com.android.printspooler` 触发（`No Activity found to handle null`），不是实体打印机故障。
- 缺陷 2 的 4 个档案/采购商品/ingredient→archive 映射/批次为**直接写库**准备验收数据（任务书允许）；出库、撕单、扣减均为 UI 操作。
- 缺陷 3 的展示、解除关联、重新关联（换算比 2）均为 **UI 操作**，不是直接写库。
