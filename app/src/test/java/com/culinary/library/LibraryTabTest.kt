package com.culinary.library

import com.culinary.R
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 知识库 Tab 契约：顺序、路由值与无效输入。 */
class LibraryTabTest {
    @Test
    fun `tabs keep the three archive modules in order`() {
        assertThat(LibraryTab.entries.map { it.route })
            .containsExactly("sauces", "ingredients", "recipes")
            .inOrder()
    }

    @Test
    fun `tab labels come from resources`() {
        assertThat(LibraryTab.SAUCES.labelRes).isEqualTo(R.string.destination_sauces)
        assertThat(LibraryTab.INGREDIENTS.labelRes).isEqualTo(R.string.destination_ingredients)
        assertThat(LibraryTab.RECIPES.labelRes).isEqualTo(R.string.destination_recipes)
    }

    @Test
    fun `route values map to tabs and unknown values fall back to null`() {
        assertThat(LibraryTab.fromRoute("sauces")).isEqualTo(LibraryTab.SAUCES)
        assertThat(LibraryTab.fromRoute("ingredients")).isEqualTo(LibraryTab.INGREDIENTS)
        assertThat(LibraryTab.fromRoute("recipes")).isEqualTo(LibraryTab.RECIPES)
        assertThat(LibraryTab.fromRoute("unknown")).isNull()
        assertThat(LibraryTab.fromRoute(null)).isNull()
    }
}
