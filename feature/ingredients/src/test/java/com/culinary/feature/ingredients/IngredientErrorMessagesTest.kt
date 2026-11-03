package com.culinary.feature.ingredients

import com.culinary.core.common.CulinaryError
import com.culinary.core.common.ErrorCode
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IngredientErrorMessagesTest {
    @Test
    fun `blank name gets a specific message`() {
        val message =
            IngredientErrorMessages.describe(
                CulinaryError.Validation(field = "ingredient.name", rule = "must not be blank"),
            )

        assertThat(message).isEqualTo("名称不能为空。")
    }

    @Test
    fun `over long name gets a length message`() {
        val message =
            IngredientErrorMessages.describe(
                CulinaryError.Validation(field = "ingredient.name", rule = "must be at most 120 characters"),
            )

        assertThat(message).isEqualTo("名称过长，请控制在 120 个字符以内。")
    }

    @Test
    fun `unexpected error keeps the code instead of pretending to be validation`() {
        val message =
            IngredientErrorMessages.describe(
                CulinaryError.Data(message = "disk full", code = ErrorCode.DB_001),
            )

        assertThat(message).contains("CUL-DB-001")
    }
}
