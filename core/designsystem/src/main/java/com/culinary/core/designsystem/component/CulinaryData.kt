package com.culinary.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.culinary.core.designsystem.theme.CulinaryColorScheme
import com.culinary.core.designsystem.theme.CulinarySizes
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.designsystem.theme.CulinaryStrokes
import com.culinary.core.designsystem.theme.CulinaryTextStyles

// 数据展示组件（MB-UI-001 §3.4）。
//
// 数字与单位分离：数量用 tabular figures 的 numeral 样式，单位降一级并降低对比，
// 视觉重点不靠放大字号，而靠字重、留白与对齐关系。

/** 全大写拉丁小标题（"STOCK OVERVIEW"）。中文不会被转换，仅原样展示。 */
@Composable
fun Eyebrow(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text = text.uppercase(),
        style = CulinaryTextStyles.eyebrow,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * 数量 + 单位：数量等宽、单位弱化。
 *
 * @param emphasisColor 需要强调时传入品牌金/语义色；默认主文字色。
 */
@Composable
fun QuantityText(
    amount: String,
    unit: String?,
    modifier: Modifier = Modifier,
    emphasisColor: Color? = null,
    amountStyle: TextStyle = CulinaryTextStyles.numeralM,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        Text(
            text = amount,
            style = amountStyle,
            color = emphasisColor ?: MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        if (!unit.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(CulinarySpacing.xxs))
            Text(
                text = unit,
                style = CulinaryTextStyles.unit,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 3.dp),
                maxLines = 1,
            )
        }
    }
}

/**
 * 指标：数字 + 单位 + 标签。
 *
 * 首页库存/知识库、详情摘要统一使用本组件，避免同类数字出现两种字号。
 */
@Composable
fun MetricValue(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    eyebrow: String? = null,
    large: Boolean = false,
    emphasis: Boolean = false,
    valueColor: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    val clickModifier =
        if (onClick == null) {
            Modifier
        } else {
            Modifier.clickable(role = Role.Button, onClick = onClick)
        }
    Column(
        modifier =
            modifier
                .defaultMinSize(minHeight = CulinarySizes.touchTarget)
                .then(clickModifier)
                .padding(vertical = CulinarySpacing.xs),
        horizontalAlignment = Alignment.Start,
    ) {
        if (!eyebrow.isNullOrBlank()) {
            Eyebrow(text = eyebrow)
            Spacer(modifier = Modifier.height(2.dp))
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = if (large) CulinaryTextStyles.numeralXl else CulinaryTextStyles.numeralL,
                color =
                    valueColor
                        ?: if (emphasis) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                maxLines = 1,
            )
            if (!unit.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(CulinarySpacing.xxs))
                Text(
                    text = unit,
                    style = CulinaryTextStyles.unit,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
    }
}

/** 库存增减：正数用成功色、负数用危险色，符号与颜色同时表达方向。 */
@Composable
fun DeltaText(
    amountText: String,
    isIncrease: Boolean,
    modifier: Modifier = Modifier,
    unit: String? = null,
) {
    val color =
        if (isIncrease) {
            CulinaryColorScheme.current.success
        } else {
            CulinaryColorScheme.current.danger
        }
    QuantityText(
        amount = amountText,
        unit = unit,
        modifier = modifier,
        emphasisColor = color,
        amountStyle = CulinaryTextStyles.numeralS,
    )
}

/** 标签 + 值：详情页与设置页的统一信息行。 */
@Composable
fun CulinaryInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    labelWidth: Int = 96,
    valueStyle: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = CulinarySpacing.xs),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(labelWidth.dp),
        )
        Text(
            text = value,
            style = valueStyle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 细横线：页面区块、列表尾部与信息边界统一使用 1dp。 */
@Composable
fun CulinaryRule(modifier: Modifier = Modifier) {
    androidx.compose.material3.HorizontalDivider(
        modifier = modifier,
        thickness = CulinaryStrokes.hairline,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}
