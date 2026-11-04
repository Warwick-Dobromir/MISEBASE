package com.culinary.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 导航契约：路由唯一、首页为起点、覆盖六项入口（五项功能 + 设置）。 */
class CulinaryDestinationTest {
    @Test
    fun `routes are unique`() {
        val routes = CulinaryDestination.entries.map { it.route }

        assertThat(routes).containsNoDuplicates()
    }

    @Test
    fun `home is the start destination`() {
        assertThat(CulinaryDestination.entries.first()).isEqualTo(CulinaryDestination.HOME)
        assertThat(CulinaryDestination.HOME.route).isEqualTo("home")
    }

    @Test
    fun `covers the six entries five features plus settings`() {
        val modules = CulinaryDestination.entries.filter { it != CulinaryDestination.HOME }

        assertThat(modules).hasSize(6)
        assertThat(modules.map { it.route }).containsExactly(
            "ingredients",
            "sauces",
            "recipes",
            "products",
            "inventory",
            "settings",
        )
    }
}
