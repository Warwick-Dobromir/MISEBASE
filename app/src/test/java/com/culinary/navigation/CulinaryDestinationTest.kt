package com.culinary.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * 导航契约（MB-D-009 方案 N1）。
 *
 * 顺序、数量与路由是信息架构的一部分：允许调图标/文案，不允许增删或重排一级入口。
 */
class CulinaryDestinationTest {
    @Test
    fun `routes are unique and do not collide with settings`() {
        val routes = CulinaryDestination.entries.map { it.route }

        assertThat(routes).containsNoDuplicates()
        assertThat(routes).doesNotContain(SETTINGS_ROUTE)
    }

    @Test
    fun `five bottom entries keep the accepted N1 order`() {
        val routes = CulinaryDestination.entries.map { it.route }

        assertThat(routes)
            .containsExactly("sauces", "ingredients", "recipes", "inventory", "scan")
            .inOrder()
    }

    @Test
    fun `sauces is the start destination`() {
        assertThat(CulinaryDestination.START).isEqualTo(CulinaryDestination.SAUCES)
        assertThat(CulinaryDestination.entries.first().route).isEqualTo("sauces")
    }

    @Test
    fun `settings stays in the top bar and is not a bottom entry`() {
        assertThat(SETTINGS_ROUTE).isEqualTo("settings")
        assertThat(CulinaryDestination.entries.map { it.route }).doesNotContain("settings")
    }

    @Test
    fun `every entry has a label resource and an icon`() {
        CulinaryDestination.entries.forEach { destination ->
            assertThat(destination.labelRes).isNotEqualTo(0)
            assertThat(destination.icon.name).isNotEmpty()
        }
    }
}
