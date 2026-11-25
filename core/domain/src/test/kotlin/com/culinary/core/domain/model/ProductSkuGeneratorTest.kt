package com.culinary.core.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProductSkuGeneratorTest {
    @Test
    fun `same category uses same category prefix`() {
        val first = ProductSkuGenerator.generate("乳制品与蛋类 / 奶制品", "product-00000001")
        val second = ProductSkuGenerator.generate("乳制品与蛋类 / 奶制品", "product-00000002")

        assertThat(first.substringBeforeLast('-')).isEqualTo(second.substringBeforeLast('-'))
        assertThat(first).isNotEqualTo(second)
    }

    @Test
    fun `different fine grained categories receive different category prefixes`() {
        val dairy = ProductSkuGenerator.generate("乳制品与蛋类 / 奶制品", "product-00000001")
        val produce = ProductSkuGenerator.generate("蔬菜与水果 / 根茎类", "product-00000001")

        assertThat(dairy.substringBeforeLast('-')).isNotEqualTo(produce.substringBeforeLast('-'))
    }

    @Test
    fun `blank category uses explicit uncategorized prefix`() {
        assertThat(ProductSkuGenerator.generate(" ", "product-00000001"))
            .startsWith("SKU-")
    }
}
