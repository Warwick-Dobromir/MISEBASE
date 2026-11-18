package com.culinary.kitchen.archive

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.abs

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
    fun firstItemUsesBoundedElasticResistanceWithoutMovingPastTop() {
        var moveCalls = 0
        val result = archiveDragOffsetAfterDelta(
            currentOffsetPx = 0f,
            currentEdgeOverdragPx = 0f,
            deltaPx = -80f,
            rowHeightPx = 100f,
            maxEdgeOffsetPx = 12f,
            canMove = { it > 0 },
            move = { moveCalls++; true },
        )

        assertThat(result.offsetPx).isLessThan(0f)
        assertThat(abs(result.offsetPx)).isAtMost(12f)
        assertThat(result.edgeOverdragPx).isEqualTo(-80f)
        assertThat(moveCalls).isEqualTo(0)
    }

    @Test
    fun lastItemUsesSymmetricElasticResistanceWithoutMovingPastBottom() {
        var moveCalls = 0
        val result = archiveDragOffsetAfterDelta(
            currentOffsetPx = 0f,
            currentEdgeOverdragPx = 0f,
            deltaPx = 80f,
            rowHeightPx = 100f,
            maxEdgeOffsetPx = 12f,
            canMove = { it < 0 },
            move = { moveCalls++; true },
        )

        assertThat(result.offsetPx).isGreaterThan(0f)
        assertThat(result.offsetPx).isAtMost(12f)
        assertThat(result.edgeOverdragPx).isEqualTo(80f)
        assertThat(moveCalls).isEqualTo(0)
    }

    @Test
    fun reachingFirstItemKeepsOnlyBoundedElasticResidual() {
        var index = 1
        val result = archiveDragOffsetAfterDelta(
            currentOffsetPx = 0f,
            currentEdgeOverdragPx = 0f,
            deltaPx = -250f,
            rowHeightPx = 100f,
            maxEdgeOffsetPx = 12f,
            canMove = { delta -> index + delta in 0..2 },
            move = { delta -> index += delta; true },
        )

        assertThat(index).isEqualTo(0)
        assertThat(result.offsetPx).isLessThan(0f)
        assertThat(abs(result.offsetPx)).isAtMost(12f)
        assertThat(result.edgeOverdragPx).isEqualTo(-60f)
    }

    @Test
    fun reversingAtBoundaryImmediatelyReleasesElasticOffset() {
        var index = 0
        val canMove: (Int) -> Boolean = { delta -> index + delta in 0..2 }
        val outward = archiveDragOffsetAfterDelta(
            currentOffsetPx = 0f,
            currentEdgeOverdragPx = 0f,
            deltaPx = -30f,
            rowHeightPx = 100f,
            maxEdgeOffsetPx = 12f,
            canMove = canMove,
            move = { delta -> index += delta; true },
        )
        val inward = archiveDragOffsetAfterDelta(
            currentOffsetPx = outward.offsetPx,
            currentEdgeOverdragPx = outward.edgeOverdragPx,
            deltaPx = 10f,
            rowHeightPx = 100f,
            maxEdgeOffsetPx = 12f,
            canMove = canMove,
            move = { delta -> index += delta; true },
        )

        assertThat(outward.offsetPx).isLessThan(0f)
        assertThat(inward.offsetPx).isGreaterThan(outward.offsetPx)
        assertThat(inward.offsetPx).isLessThan(0f)
        assertThat(inward.edgeOverdragPx).isEqualTo(-20f)
        assertThat(index).isEqualTo(0)
    }

    @Test
    fun reversingPastElasticOriginResumesNormalDragWithoutJump() {
        var index = 0
        val canMove: (Int) -> Boolean = { delta -> index + delta in 0..2 }
        val outward = archiveDragOffsetAfterDelta(
            currentOffsetPx = 0f,
            currentEdgeOverdragPx = 0f,
            deltaPx = -30f,
            rowHeightPx = 100f,
            maxEdgeOffsetPx = 12f,
            canMove = canMove,
            move = { delta -> index += delta; true },
        )
        val inward = archiveDragOffsetAfterDelta(
            currentOffsetPx = outward.offsetPx,
            currentEdgeOverdragPx = outward.edgeOverdragPx,
            deltaPx = 35f,
            rowHeightPx = 100f,
            maxEdgeOffsetPx = 12f,
            canMove = canMove,
            move = { delta -> index += delta; true },
        )

        assertThat(inward.offsetPx).isEqualTo(5f)
        assertThat(inward.edgeOverdragPx).isEqualTo(0f)
        assertThat(index).isEqualTo(0)
    }

    @Test
    fun continuedOutwardMovementDoesNotAccumulateUnboundedPointerSeparation() {
        val first = archiveDragOffsetAfterDelta(
            currentOffsetPx = 0f,
            currentEdgeOverdragPx = 0f,
            deltaPx = -2_000f,
            rowHeightPx = 100f,
            maxEdgeOffsetPx = 12f,
            canMove = { false },
            move = { false },
        )
        val reversed = archiveDragOffsetAfterDelta(
            currentOffsetPx = first.offsetPx,
            currentEdgeOverdragPx = first.edgeOverdragPx,
            deltaPx = 10f,
            rowHeightPx = 100f,
            maxEdgeOffsetPx = 12f,
            canMove = { false },
            move = { false },
        )

        assertThat(abs(first.offsetPx)).isAtMost(12f)
        assertThat(abs(first.edgeOverdragPx)).isAtMost(60f)
        assertThat(reversed.offsetPx).isGreaterThan(first.offsetPx)
        assertThat(reversed.edgeOverdragPx).isGreaterThan(first.edgeOverdragPx)
    }
}
