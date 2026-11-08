package com.culinary.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * 导航契约（厨房首页改造）。
 *
 * 底部严格五项：首页 / 厨房 / 扫码 / 库存 / 我的。
 * 厨房复用原 library 路由；三个档案库仍保留独立 feature 路由，但不占底部入口。
 */
class CulinaryDestinationTest {
    @Test
    fun `bottom entries keep the accepted N3 order`() {
        val routes = CulinaryDestination.entries.map { it.route }

        assertThat(routes)
            .containsExactly("home", "library", "scan", "inventory", "settings")
            .inOrder()
        assertThat(routes).containsNoDuplicates()
    }

    @Test
    fun `home is the cold start destination`() {
        assertThat(APP_START_ROUTE).isEqualTo(HOME_ROUTE)
        assertThat(CulinaryDestination.START).isEqualTo(CulinaryDestination.HOME)
        assertThat(CulinaryDestination.entries.first().route).isEqualTo(HOME_ROUTE)
    }

    @Test
    fun `kitchen is one bottom entry with an internal archive tab route`() {
        assertThat(CulinaryDestination.LIBRARY.route).isEqualTo(LIBRARY_ROUTE)
        assertThat(CulinaryDestination.LIBRARY.matches(LIBRARY_ROUTE)).isTrue()
        assertThat(CulinaryDestination.LIBRARY.matches(LIBRARY_TAB_ROUTE)).isTrue()
        assertThat(CulinaryDestination.forRoute(LIBRARY_TAB_ROUTE)).isEqualTo(CulinaryDestination.LIBRARY)
        assertThat(LIBRARY_TAB_ROUTE).isEqualTo("library/{tab}")
    }

    @Test
    fun `legacy archive list routes are no longer top level entries`() {
        val routes = CulinaryDestination.entries.map { it.route }

        assertThat(routes).doesNotContain("sauces")
        assertThat(routes).doesNotContain("ingredients")
        assertThat(routes).doesNotContain("recipes")
        assertThat(CulinaryDestination.forRoute("sauces")).isNull()
        assertThat(CulinaryDestination.forRoute("ingredients")).isNull()
        assertThat(CulinaryDestination.forRoute("recipes")).isNull()
    }

    @Test
    fun `settings is a bottom entry and not a top bar action route`() {
        assertThat(CulinaryDestination.SETTINGS.route).isEqualTo(SETTINGS_ROUTE)
        assertThat(CulinaryDestination.SETTINGS.matches(SETTINGS_ROUTE)).isTrue()
        assertThat(CulinaryDestination.entries.map { it.route }).contains(SETTINGS_ROUTE)
    }

    @Test
    fun `every entry has a label resource and an icon`() {
        CulinaryDestination.entries.forEach { destination ->
            assertThat(destination.labelRes).isNotEqualTo(0)
            assertThat(destination.icon.name).isNotEmpty()
        }
    }
}
