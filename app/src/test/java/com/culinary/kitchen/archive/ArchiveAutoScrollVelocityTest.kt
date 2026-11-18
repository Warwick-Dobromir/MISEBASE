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
    @Test
    fun dragAtFirstItemCannotMoveThroughTopBoundary() {
        var moveCalls = 0
        val offset = archiveDragOffsetAfterDelta(
            currentOffsetPx = 0f,
            deltaPx = -80f,
            rowHeightPx = 100f,
            canMove = { it > 0 },
            move = { moveCalls++; true },
        )
        assertThat(offset).isEqualTo(0f)
        assertThat(moveCalls).isEqualTo(0)
    }

    @Test
    fun dragAtLastItemCannotMoveThroughBottomBoundary() {
        var moveCalls = 0
        val offset = archiveDragOffsetAfterDelta(
            currentOffsetPx = 0f,
            deltaPx = 80f,
            rowHeightPx = 100f,
            canMove = { it < 0 },
            move = { moveCalls++; true },
        )
        assertThat(offset).isEqualTo(0f)
        assertThat(moveCalls).isEqualTo(0)
    }

    @Test
    fun dragClampsResidualDistanceWhenItReachesFirstItem() {
        var index = 1
        val offset = archiveDragOffsetAfterDelta(
            currentOffsetPx = 0f,
            deltaPx = -250f,
            rowHeightPx = 100f,
            canMove = { delta -> index + delta in 0..2 },
            move = { delta -> index += delta; true },
        )
        assertThat(index).isEqualTo(0)
        assertThat(offset).isEqualTo(0f)
    }

    @Test
    fun dragCanReverseAwayFromBoundaryWithoutJumping() {
        var index = 0
        val canMove: (Int) -> Boolean = { delta -> index + delta in 0..2 }
        val outwardOffset = archiveDragOffsetAfterDelta(
            currentOffsetPx = 0f,
            deltaPx = -30f,
            rowHeightPx = 100f,
            canMove = canMove,
            move = { delta -> index += delta; true },
        )
        val inwardOffset = archiveDragOffsetAfterDelta(
            currentOffsetPx = outwardOffset,
            deltaPx = 25f,
            rowHeightPx = 100f,
            canMove = canMove,
            move = { delta -> index += delta; true },
        )
        assertThat(outwardOffset).isEqualTo(0f)
        assertThat(inwardOffset).isEqualTo(25f)
        assertThat(index).isEqualTo(0)
    }
}
