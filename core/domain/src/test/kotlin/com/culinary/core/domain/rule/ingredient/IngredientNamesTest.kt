package com.culinary.core.domain.rule.ingredient

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IngredientNamesTest {
    @Test
    fun `applies NFKC so full width letters match`() {
        assertThat(IngredientNames.normalize("Ｃｕｌｉｎａｒｙ")).isEqualTo("culinary")
    }

    @Test
    fun `trims and collapses whitespace`() {
        assertThat(IngredientNames.normalize("  soy   sauce ")).isEqualTo("soy sauce")
    }

    @Test
    fun `lowercases with the root locale`() {
        assertThat(IngredientNames.normalize("Soy Sauce")).isEqualTo("soy sauce")
    }

    @Test
    fun `leaves Chinese names unchanged`() {
        assertThat(IngredientNames.normalize("酱油")).isEqualTo("酱油")
    }
}
