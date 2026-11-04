# MB-S3-05 设计系统扩展说明

**文档编号**：MB-S3-05
**版本**：`1.0.0`
**状态**：`Review`
**日期**：2026-11-04
**方向**：MB-D-008 A「专业厨房档案」
**依据**：UI 设计书 1.0.0 §3–§9

---

## 1. 色彩

- 亮色：`primary #B4551F`、`onPrimary #FFFFFF`、`background #FFFBF8`、`surface #FFFFFF`、
  `surfaceVariant #F3E1D6`、`outlineVariant #E4D3C7`、`onSurface #28180E`、`onSurfaceVariant #6F5B4E`；
- 暗色：`background #1A1210`、`surface #241A16`、`primary #FFB68C`，不使用纯黑；
- 语义色：success `#2E6B4F` / warning `#8A5A00` / danger `#A32020` / info `#2F5D8A`（暗色各自提亮）；
- 语义色通过 `LocalCulinarySemanticColors` + `CulinaryColorScheme.current` 提供；
- 规则：橙色只用于主操作/选中/关键数字；语义色不用于装饰；正文对比 ≥4.5:1。

## 2. 字体与字阶

| Token | 字号/行高 | 映射 |
|---|---|---|
| display | 32 / 40 | `displaySmall` |
| headline | 24 / 32 | `headlineMedium` |
| title | 20 / 28 | `titleLarge` |
| subtitle | 16 / 24 | `titleMedium` |
| body | 14 / 22 | `bodyLarge` / `bodyMedium` |
| label | 12 / 16 | `labelMedium` |
| caption | 11 / 16 | `labelSmall` |

数量使用 `CulinaryTextStyles.quantity`：等宽 + `tnum`；数字与单位分离显示。
中文字体使用系统默认，不引入第三方字体。

## 3. 间距 / 圆角 / 尺寸

- 间距只允许 4 / 8 / 12 / 16 / 24 / 32 / 48dp（`CulinarySpacing`）；
- 圆角：4（标签）、8（按钮/输入）、12（卡片）、16（浮层）（`CulinaryShapes`）；
- 触控 ≥48dp；按钮/输入高度 48/56dp；列表底部预留 88dp；
- 列表/卡片用边框或色差，不用重阴影。

## 4. 组件

| 组件 | 用途 |
|---|---|
| `SectionHeader` | 区块标题 + 可选动作 |
| `ArchiveListItem` | 标题/副标题/尾部元数据；行高 56–72dp |
| `StatusBadge` | 中性/强调/成功/警告/危险；文字+颜色双载体 |
| `QuantityText` | 等宽数字 + 弱化单位 |
| `EmptyState` | 为什么空 + 下一步 |
| `ErrorBanner` | 页面级错误 + 可执行动作 |
| `LoadingRows` | 静态骨架行（非全屏 spinner） |
| `FormSection` | 表单分组 |
| `ConfirmDialog` | 破坏性操作确认（危险色） |

## 5. 图标

`CulinaryIcons` 手绘 11 个线性图标（24dp 视口、1.8 线宽）：酱汁、食材、菜谱、库存、扫码、
设置、新增、返回、编辑、勾选、右箭头。不引入第三方图标依赖。

## 6. 主题接入

`CulinaryTheme(darkTheme = isSystemInDarkTheme())` 提供 MaterialTheme + 语义色。
页面只使用 `MaterialTheme.colorScheme`、`MaterialTheme.typography`、`MaterialTheme.shapes`
与设计系统组件，不写魔法色值/间距。

## 7. 测试与约束

- `DesignSystemTokensTest`（9 例）：色值、对比度、字阶、4dp 栅格、圆角；
- 设计系统不依赖 domain/data/database/feature；
- 组件只含视觉/交互，不含业务规则；
- 页面级视觉验收（截图、大字体、暗色）留 Stage 6。
