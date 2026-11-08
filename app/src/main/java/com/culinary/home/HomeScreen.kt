package com.culinary.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.culinary.R
import com.culinary.core.designsystem.component.CulinaryIconButton
import com.culinary.core.designsystem.component.CulinaryRule
import com.culinary.core.designsystem.icon.CulinaryIcons
import com.culinary.core.designsystem.theme.CulinarySpacing
import com.culinary.core.designsystem.theme.CulinaryTextStyles

/**
 * 首页路由（HOME-001）。
 *
 * 本次只替换首页视觉与交互；导航外壳、其他业务模块、Room 数据库均保持不变。
 */
@Composable
fun HomeRoute(
    onOpenTimer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAllPlans: () -> Unit,
    onAddPlan: () -> Unit,
    onNewPurchase: () -> Unit,
    onOpenAllPurchases: () -> Unit,
    onOpenPurchase: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    HomeScreen(
        state = state,
        onTogglePlan = viewModel::togglePlan,
        onOpenTimer = onOpenTimer,
        onOpenSettings = onOpenSettings,
        onOpenAllPlans = onOpenAllPlans,
        onAddPlan = onAddPlan,
        onNewPurchase = onNewPurchase,
        onOpenAllPurchases = onOpenAllPurchases,
        onOpenPurchase = onOpenPurchase,
        modifier = modifier,
    )
}

/**
 * 首页视觉结构：字标 → 今日计划 → 采购单。
 *
 * 编辑极简 + 瑞士排版：白纸、细线、严格左对齐、香槟金只做少量强调；
 * 整个主体可纵向滚动，底部安全留白由外层 Scaffold 的底栏 padding 保证。
 */
@Composable
internal fun HomeScreen(
    state: HomeUiState,
    onTogglePlan: (String) -> Unit,
    onOpenTimer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAllPlans: () -> Unit,
    onAddPlan: () -> Unit,
    onNewPurchase: () -> Unit,
    onOpenAllPurchases: () -> Unit,
    onOpenPurchase: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        HomeMasthead(
            onOpenTimer = onOpenTimer,
            onOpenSettings = onOpenSettings,
        )
        CulinaryRule()
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
        ) {
            TodayPreparationSection(
                state = state,
                onTogglePlan = onTogglePlan,
                onOpenAllPlans = onOpenAllPlans,
                onAddPlan = onAddPlan,
            )
            CulinaryRule()
            PurchaseRecordsSection(
                state = state,
                onNewPurchase = onNewPurchase,
                onOpenAllPurchases = onOpenAllPurchases,
                onOpenPurchase = onOpenPurchase,
            )
            Spacer(modifier = Modifier.height(CulinarySpacing.xl))
        }
    }
}

/** 顶部软件标识：小字号字标 + 低对比副标题，右侧计时器与更多。 */
@Composable
private fun HomeMasthead(
    onOpenTimer: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = CulinarySpacing.pageHorizontal,
                    end = CulinarySpacing.xs,
                    top = CulinarySpacing.sm,
                    bottom = CulinarySpacing.sm,
                ).defaultMinSize(minHeight = 60.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.home_brand),
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.16.em,
                    ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = stringResource(R.string.home_brand_subtitle),
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 0.015.em,
                    ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        CulinaryIconButton(
            icon = CulinaryIcons.Timer,
            contentDescription = stringResource(R.string.home_timer),
            onClick = onOpenTimer,
        )
        CulinaryIconButton(
            icon = CulinaryIcons.More,
            contentDescription = stringResource(R.string.home_more),
            onClick = onOpenSettings,
        )
    }
}

/** 今日计划：标题/动态计数 → 进度条 → 四条计划 → 查看全部/添加计划。 */
@Composable
private fun TodayPreparationSection(
    state: HomeUiState,
    onTogglePlan: (String) -> Unit,
    onOpenAllPlans: () -> Unit,
    onAddPlan: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeading(
            title = stringResource(R.string.home_today_title),
            subtitle = stringResource(R.string.home_today_subtitle),
            modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal),
            trailing = {
                Text(
                    text =
                        stringResource(
                            R.string.home_today_count,
                            state.completedPlanCount,
                            state.totalPlanCount,
                        ),
                    style =
                        MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.05.em,
                            fontFeatureSettings = "tnum",
                        ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
        )
        PlanProgressBar(
            progress = state.planProgress,
            modifier =
                Modifier
                    .padding(horizontal = CulinarySpacing.pageHorizontal)
                    .padding(top = CulinarySpacing.sm),
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.xs))
        state.plans.forEach { plan ->
            PlanRow(
                plan = plan,
                onToggle = { onTogglePlan(plan.id) },
            )
        }
        TodayPlanActions(
            onOpenAllPlans = onOpenAllPlans,
            onAddPlan = onAddPlan,
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.xs))
    }
}

@Composable
private fun TodayPlanActions(
    onOpenAllPlans: () -> Unit,
    onAddPlan: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = CulinarySpacing.pageHorizontal)
                .padding(top = CulinarySpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InlineTextAction(
            text = stringResource(R.string.home_today_view_all),
            onClick = onOpenAllPlans,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.weight(1f))
        InlineTextAction(
            text = stringResource(R.string.home_today_add),
            onClick = onAddPlan,
            color = MaterialTheme.colorScheme.secondary,
            showArrow = false,
        )
    }
}

@Composable
private fun SectionHeading(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = CulinarySpacing.lg, bottom = CulinarySpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = CulinaryTextStyles.sectionTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 0.04.em,
                    ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(CulinarySpacing.sm))
        trailing()
    }
}

/** 计划进度：2dp 浅灰轨道 + 香槟金完成段；无圆角、无阴影。 */
@Composable
private fun PlanProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        label = "today-plan-progress",
    )
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
                .testTag("today-plan-progress")
                .semantics {
                    progressBarRangeInfo =
                        ProgressBarRangeInfo(
                            current = progress.coerceIn(0f, 1f),
                            range = 0f..1f,
                        )
                },
    ) {
        if (animatedProgress > 0f) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.inversePrimary),
            )
        }
    }
}

/** 一条计划：左侧固定 22dp 圆圈，右侧文字严格左对齐；整行可点击。 */
@Composable
private fun PlanRow(
    plan: HomePlan,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .toggleable(
                    value = plan.completed,
                    role = Role.Checkbox,
                    onValueChange = { onToggle() },
                ).defaultMinSize(minHeight = 48.dp)
                .padding(
                    start = CulinarySpacing.pageHorizontal,
                    end = CulinarySpacing.pageHorizontal,
                    top = 11.dp,
                    bottom = 11.dp,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlanCheck(checked = plan.completed)
        Spacer(modifier = Modifier.width(CulinarySpacing.sm))
        Text(
            text = stringResource(plan.titleRes),
            style = MaterialTheme.typography.bodyLarge,
            color =
                if (plan.completed) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            maxLines = 2,
        )
    }
}

@Composable
private fun PlanCheck(checked: Boolean) {
    val fill = if (checked) MaterialTheme.colorScheme.secondary else Color.Transparent
    val border = if (checked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
    Box(
        modifier =
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(fill)
                .border(width = 1.dp, color = border, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                imageVector = CulinaryIcons.Check,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSecondary,
            )
        }
    }
}

/** 采购单：标题行 + 三条纵向信息记录 + 完整列表入口。 */
@Composable
private fun PurchaseRecordsSection(
    state: HomeUiState,
    onNewPurchase: () -> Unit,
    onOpenAllPurchases: () -> Unit,
    onOpenPurchase: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeading(
            title = stringResource(R.string.home_purchase_title),
            subtitle = stringResource(R.string.home_purchase_subtitle),
            modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal),
            trailing = {
                InlineTextAction(
                    text = stringResource(R.string.home_purchase_new),
                    onClick = onNewPurchase,
                    color = MaterialTheme.colorScheme.secondary,
                    showArrow = false,
                )
            },
        )
        state.purchases.forEach { purchase ->
            PurchaseRecordRow(
                purchase = purchase,
                onClick = { onOpenPurchase(purchase.id) },
            )
            CulinaryRule(modifier = Modifier.padding(horizontal = CulinarySpacing.pageHorizontal))
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = CulinarySpacing.pageHorizontal,
                        end = CulinarySpacing.pageHorizontal,
                        top = CulinarySpacing.xs,
                    ),
        ) {
            InlineTextAction(
                text = stringResource(R.string.home_purchase_view_all),
                onClick = onOpenAllPurchases,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        Text(
            text = stringResource(R.string.home_purchase_demo_note),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier.padding(
                    start = CulinarySpacing.pageHorizontal,
                    end = CulinarySpacing.pageHorizontal,
                    top = CulinarySpacing.xxs,
                ),
        )
    }
}

/** 采购记录：名称是主信息，日期/时间为辅助信息，状态与箭头同一行对齐。 */
@Composable
private fun PurchaseRecordRow(
    purchase: HomePurchaseRecord,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(
                    start = CulinarySpacing.pageHorizontal,
                    end = CulinarySpacing.pageHorizontal,
                    top = CulinarySpacing.md,
                    bottom = CulinarySpacing.md,
                ),
    ) {
        Text(
            text = stringResource(purchase.nameRes),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.home_purchase_date, purchase.createdDate),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.home_purchase_time, purchase.createdTime),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(CulinarySpacing.xs))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.home_purchase_status),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = CulinaryIcons.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 克制的文字入口：40dp 命中区，不使用大按钮。
 *
 * 箭头是装饰性字符的语义替代，不单独提供点击节点；整个文字行是一个按钮。
 */
@Composable
private fun InlineTextAction(
    text: String,
    onClick: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
    showArrow: Boolean = true,
) {
    Row(
        modifier =
            modifier
                .defaultMinSize(minHeight = 40.dp)
                .clip(RoundedCornerShape(2.dp))
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = CulinarySpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            textAlign = TextAlign.Start,
            maxLines = 1,
        )
        if (showArrow) {
            Spacer(modifier = Modifier.width(3.dp))
            Icon(
                imageVector = CulinaryIcons.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = color,
            )
        }
    }
}
