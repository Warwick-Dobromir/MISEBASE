# G2 阶段 6（应用外壳）完成报告

**文档编号**：R16
**版本**：`1.0.0`
**状态**：`Review`（待项目负责人验收）
**日期**：2026-11-04
**实施依据**：[R01 0.2.2](R01-FRAMEWORK-SEQUENCE.md) §9、A01、A02、E03 §7-§8、D-012
**回档点**：`baseline/g2-stage5-20261104`（`4f023ef`）

---

## 1. 任务目标

应用外壳：Hilt 根、导航、主题、启动/恢复流程、全局错误边界与调试入口；
业务规则不得写在 Activity/Application。

## 2. 交付内容

| 组件 | 说明 |
|---|---|
| 导航 | Navigation Compose：`home` + 8 个模块 + `settings`，路由字符串稳定；首页进入、占位页返回 |
| 首页 | 中文主语言、Orange 主题、模块入口列表、底部“调试信息” |
| 主题 | `core:designsystem` 的 `CulinaryTheme`（浅/深色 + Orange 强调色） |
| 启动协调 | `StartupCoordinator`：读 `user_version` → 版本过高拒绝 → SQLite header 校验 → Room 打开 → Ready/Recovery |
| 兼容性/恢复 | 版本不兼容页（双版本号）；Recovery 页只提供“重试”，**无清库入口** |
| 错误边界 | `CrashLogWriter` 写 `filesDir/crash-logs/crash-<millis>.txt` 后委托原 handler；Timber `AppLogger` |
| 调试入口 | 只读展示 DB 路径/大小/`user_version`/`journal_mode`、崩溃日志数与最近时间 |
| Activity/Application | 只负责主题、导航容器、日志安装与启动协调；无业务规则 |

## 3. 设备实测（模拟器）

| 场景 | 操作 | 结果 |
|---|---|---|
| 冷启动 | 安装后 `am start -W` | `COLD`，首页渲染（9 个入口） |
| 模块导航 | 点击“食材” | 占位页文本出现，标题正确 |
| 返回 | 返回键 | 回到首页 |
| 版本过高 | `sqlite3` 置 `user_version=2` 后启动 | 显示“数据库版本不兼容”及两个版本号 |
| 数据库损坏 | 写入非 SQLite 内容后启动 | 显示“需要恢复”+“重试”；**损坏文件保留**（21 字节） |
| 恢复现场 | 还原备份 DB | `user_version=1`，回到首页 |
| 真实崩溃 | `adb shell am crash com.culinary` | 生成 `crash-*.txt`（线程/异常/堆栈）；进程仍被系统终止；重启正常 |
| 调试入口 | 首页 → 调试信息 | 显示 DB 路径、`user_version`、`journal_mode`、崩溃数 |

## 4. 测试

| 测试类 | 数量 | 覆盖 |
|---|---:|---|
| `CulinaryDestinationTest` | 3 | 路由唯一、首页起点、8 模块 + 设置 |
| `CrashLogWriterTest` | 3 | 日志内容、委托原 handler、同毫秒不覆盖 |
| `StartupCoordinatorTest` | 3 | 就绪、版本过高、损坏进入 Recovery 且文件保留 |
| `DebugInfoProviderTest` | 1 | 版本/路径/崩溃计数 |

工程总测试 **164 例**；`ktlintCheck`、`detekt`、`assembleDebug` 全绿。

## 5. 退出条件核对（R01 §9.2）

| # | 条件 | 状态 |
|---|---|---|
| 1 | 冷启动进入首页 | **PASS**（设备） |
| 2 | 数据库版本过高进入兼容性提示 | **PASS**（设备） |
| 3 | DB 损坏进入 Recovery，不自动清空 | **PASS**（设备 + 单测） |
| 4 | 迁移失败进入恢复流程 | 逻辑 **PASS**（单元测试映射 `MIGRATION_FAILED`）；设备场景未单独构造 |
| 5 | Hilt 图编译通过 | **PASS** |
| 6 | 无业务规则写在 Activity/Application | **PASS**（代码评审） |

## 6. 偏差与遗留

- 迁移失败的设备级场景未构造（需要旧 schema 备份 + 缺迁移的构建）；由 `StartupCoordinator` 的异常映射与单测覆盖；
- 调试入口只读，不做导出；导出诊断属于后续阶段；
- 首页为占位导航，真实业务界面在阶段 7。

## 7. Git

| 项 | 值 |
|---|---|
| 阶段 5 基线 | `baseline/g2-stage5-20261104`（`4f023ef`） |
| 阶段 6 提交 | `9a22482`（导航）、`a7b1d9a`（启动/恢复）、`5f63f2c`（崩溃边界）、`5339c1f`（调试入口） |
| 阶段 6 基线标签 | 验收通过后打 `baseline/g2-stage6-20261104` |

## 8. 结论

R01 §9.2 的六项退出条件全部满足（其中迁移失败为逻辑级验证，已在 §6 标注明）；
导航、启动门禁、恢复、崩溃日志与调试入口均为真实实现，并有设备证据。

**请项目负责人验收阶段 6。** 验收通过后进入阶段 7（食材纵向切片）。

## 9. 变更记录

| 版本 | 日期 | 变更 | 作者 |
|---|---|---|---|
| 1.0.0 | 2026-11-04 | 记录应用外壳、设备证据、164 例测试与遗留项 | 工程组 |
