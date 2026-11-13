package com.culinary.kitchen.archive

import com.culinary.core.data.kitchen.KitchenArchiveKind
import com.culinary.kitchen.KitchenCategory
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 厨房可见资料库契约：只允许酱汁、食材、菜谱三个入口。 */
class KitchenArchiveSectionTest {
    @Test
    fun `only the three core libraries are exposed in kitchen`() {
        val sections = KitchenArchiveSection.entries

        assertThat(sections).hasSize(3)
        assertThat(sections.map(KitchenArchiveSection::kind))
            .containsExactly(
                KitchenArchiveKind.SAUCE,
                KitchenArchiveKind.INGREDIENT,
                KitchenArchiveKind.RECIPE,
            ).inOrder()
        assertThat(sections.map(KitchenArchiveSection::routeValue)).containsNoDuplicates()
        assertThat(sections.map(KitchenArchiveSection::titleRes)).containsNoDuplicates()
    }

    @Test
    fun `every kitchen category maps to one of the three visible library sections`() {
        val sections = KitchenCategory.entries.map(KitchenCategory::toArchiveSection)

        assertThat(sections).containsExactlyElementsIn(KitchenArchiveSection.entries).inOrder()
    }

    @Test
    fun `hidden reference record kinds remain type-safe but are not library sections`() {
        assertThat(KitchenArchiveKind.TECHNIQUE.toReferenceType()).isNotNull()
        assertThat(KitchenArchiveKind.PRODUCTION_STANDARD.toReferenceType()).isNotNull()
        assertThat(KitchenArchiveKind.STORAGE_STANDARD.toReferenceType()).isNotNull()
    }
}
