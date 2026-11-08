package com.culinary.kitchen.archive

import com.culinary.core.data.kitchen.KitchenArchiveKind
import com.culinary.kitchen.KitchenCategory
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 六库配置契约：每个厨房分类都有独立路由、类型与文案。 */
class KitchenArchiveSectionTest {
    @Test
    fun `six sections cover six distinct archive kinds and routes`() {
        val sections = KitchenArchiveSection.entries

        assertThat(sections).hasSize(6)
        assertThat(sections.map(KitchenArchiveSection::kind))
            .containsExactlyElementsIn(KitchenArchiveKind.entries)
        assertThat(sections.map(KitchenArchiveSection::routeValue)).containsNoDuplicates()
        assertThat(sections.map(KitchenArchiveSection::titleRes)).containsNoDuplicates()
    }

    @Test
    fun `every kitchen category maps to its own library section`() {
        val sections = KitchenCategory.entries.map(KitchenCategory::toArchiveSection)

        assertThat(sections).containsExactlyElementsIn(KitchenArchiveSection.entries).inOrder()
    }

    @Test
    fun `only the three new reference kinds map to an editor type`() {
        assertThat(KitchenArchiveKind.SAUCE.toReferenceType()).isNull()
        assertThat(KitchenArchiveKind.INGREDIENT.toReferenceType()).isNull()
        assertThat(KitchenArchiveKind.RECIPE.toReferenceType()).isNull()
        assertThat(KitchenArchiveKind.TECHNIQUE.toReferenceType()).isNotNull()
        assertThat(KitchenArchiveKind.PRODUCTION_STANDARD.toReferenceType()).isNotNull()
        assertThat(KitchenArchiveKind.STORAGE_STANDARD.toReferenceType()).isNotNull()
    }
}
