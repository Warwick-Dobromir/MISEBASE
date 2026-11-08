package com.culinary.home

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 首页本地状态：默认四条计划、三条采购记录，勾选可完成/取消且计数实时变化。 */
class HomeViewModelTest {
    @Test
    fun `starts with four incomplete plans and three purchase records`() {
        val viewModel = HomeViewModel()

        val state = viewModel.uiState.value
        assertThat(state.plans).hasSize(4)
        assertThat(state.plans.map { it.completed }).containsExactly(false, false, false, false)
        assertThat(state.purchases).hasSize(3)
        assertThat(state.completedPlanCount).isEqualTo(0)
        assertThat(state.totalPlanCount).isEqualTo(4)
        assertThat(state.planProgress).isEqualTo(0f)
    }

    @Test
    fun `toggle marks a plan complete and updates count and progress`() {
        val viewModel = HomeViewModel()

        viewModel.togglePlan("stock")

        val state = viewModel.uiState.value
        assertThat(state.plans.first { it.id == "stock" }.completed).isTrue()
        assertThat(state.completedPlanCount).isEqualTo(1)
        assertThat(state.planProgress).isEqualTo(0.25f)
    }

    @Test
    fun `toggle again cancels completion`() {
        val viewModel = HomeViewModel()

        viewModel.togglePlan("stock")
        viewModel.togglePlan("stock")

        val state = viewModel.uiState.value
        assertThat(state.plans.first { it.id == "stock" }.completed).isFalse()
        assertThat(state.completedPlanCount).isEqualTo(0)
        assertThat(state.planProgress).isEqualTo(0f)
    }

    @Test
    fun `unknown plan id does not change state`() {
        val viewModel = HomeViewModel()

        viewModel.togglePlan("not-a-plan")

        assertThat(viewModel.uiState.value.completedPlanCount).isEqualTo(0)
    }
}
