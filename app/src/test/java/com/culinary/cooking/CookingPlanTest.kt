package com.culinary.cooking

import com.culinary.core.domain.model.KitchenStep
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 制作流程编号与纳入规则（任务书第八阶段 / 第十七阶段 D 组）。 */
class CookingPlanTest {
    private fun step(
        title: String,
        seconds: Int,
    ) = KitchenStep(id = title, title = title, body = "$title 说明", timeSeconds = seconds)

    @Test
    fun `all steps timed keeps every step and renumbers from the start`() {
        val steps = listOf(step("a", 60), step("b", 120), step("c", 30))
        val plan = CookingPlan.build(steps, startIndex = 0)
        assertThat(plan).hasSize(3)
        assertThat(plan.map { it.number }).containsExactly(1, 2, 3)
        assertThat(plan.map { it.originalStepNo }).containsExactly(1, 2, 3)
        assertThat(plan.first().total).isEqualTo(3)
    }

    @Test
    fun `a no-time step in the middle is included and blocks the next timed step`() {
        val steps = listOf(step("a", 60), step("b", 0), step("c", 30))
        val plan = CookingPlan.build(steps, startIndex = 0)
        assertThat(plan).hasSize(3)
        assertThat(plan[1].hasTime).isFalse()
        assertThat(plan[1].number).isEqualTo(2)
        assertThat(plan[2].number).isEqualTo(3)
    }

    @Test
    fun `consecutive no-time steps are all included`() {
        val steps = listOf(step("a", 60), step("b", 0), step("c", 0), step("d", 30))
        val plan = CookingPlan.build(steps, startIndex = 0)
        assertThat(plan).hasSize(4)
        assertThat(plan.filter { !it.hasTime }.map { it.number }).containsExactly(2, 3)
    }

    @Test
    fun `starting from a later step ignores everything before it`() {
        val steps = listOf(step("a", 0), step("b", 0), step("c", 60), step("d", 30))
        val plan = CookingPlan.build(steps, startIndex = 2)
        assertThat(plan).hasSize(2)
        assertThat(plan.map { it.originalStepNo }).containsExactly(3, 4)
        assertThat(plan.map { it.number }).containsExactly(1, 2)
    }

    @Test
    fun `trailing no-time steps after the last timed step are excluded`() {
        val steps = listOf(step("a", 60), step("b", 30), step("c", 0))
        val plan = CookingPlan.build(steps, startIndex = 0)
        assertThat(plan).hasSize(2)
        assertThat(plan.map { it.originalStepNo }).containsExactly(1, 2)
    }

    @Test
    fun `only one timed step produces a single step plan`() {
        val steps = listOf(step("only", 45))
        val plan = CookingPlan.build(steps, startIndex = 0)
        assertThat(plan).hasSize(1)
        assertThat(plan.single().number).isEqualTo(1)
        assertThat(plan.single().total).isEqualTo(1)
    }

    @Test
    fun `plan is empty when no step has time or start is after the last timed step`() {
        assertThat(CookingPlan.build(listOf(step("a", 0), step("b", 0)), 0)).isEmpty()
        assertThat(CookingPlan.build(listOf(step("a", 60), step("b", 0)), 1)).isEmpty()
    }

    @Test
    fun `progress counts no-time steps`() {
        assertThat(CookingPlan.progress(1, 5)).isEqualTo(0.2f)
        assertThat(CookingPlan.progress(5, 5)).isEqualTo(1f)
        assertThat(CookingPlan.progress(0, 0)).isEqualTo(0f)
    }
}
