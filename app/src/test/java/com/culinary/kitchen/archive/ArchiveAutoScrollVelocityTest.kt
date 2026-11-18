package com.culinary.kitchen.archive

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ArchiveAutoScrollVelocityTest {
    @Test
    fun autoScrollVelocityAcceleratesTowardEdge() {
        val nearEdge = archiveAutoScrollVelocityPxPerSecond(0.5f)
        val atEdge = archiveAutoScrollVelocityPxPerSecond(1f)
        val beyondEdge = archiveAutoScrollVelocityPxPerSecond(2f)

        assertThat(nearEdge).isGreaterThan(500f)
        assertThat(atEdge).isEqualTo(2_400f)
        assertThat(beyondEdge).isEqualTo(atEdge)
    }
}
