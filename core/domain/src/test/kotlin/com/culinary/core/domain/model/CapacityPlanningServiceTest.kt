package com.culinary.core.domain.model

import com.google.common.truth.Truth.assertThat
import java.math.BigDecimal
import org.junit.Test

class CapacityPlanningServiceTest {
    @Test
    fun `expiry thresholds use remaining shelf life percentages`() {
        assertThat(ExpiryCapacityZone.fromRemainingRatio(BigDecimal("0.5001"))).isEqualTo(ExpiryCapacityZone.NORMAL)
        assertThat(ExpiryCapacityZone.fromRemainingRatio(BigDecimal("0.5"))).isEqualTo(ExpiryCapacityZone.ATTENTION)
        assertThat(ExpiryCapacityZone.fromRemainingRatio(BigDecimal("0.2"))).isEqualTo(ExpiryCapacityZone.NEAR_EXPIRY)
        assertThat(ExpiryCapacityZone.fromRemainingRatio(BigDecimal.ZERO)).isEqualTo(ExpiryCapacityZone.EXPIRED)
        assertThat(ExpiryCapacityZone.fromRemainingRatio(null)).isNull()
    }

    @Test
    fun `capacity cells are scoped to base location and expiry zone`() {
        val result = CapacityPlanningService.plan(CapacityPlanningRequest(
            baseLocationId = LocationId("cold-a"),
            baseLocationName = "冷藏-A",
            expiryZone = ExpiryCapacityZone.ATTENTION,
            requestedCu = BigDecimal("12"),
            cellCapacityCu = BigDecimal("10"),
            existingCells = listOf(CapacityCellSnapshot(1, BigDecimal("10"), BigDecimal("4"))),
        ))
        assertThat(result.map { it.displayName }).containsExactly("冷藏-A（关注）-01", "冷藏-A（关注）-02").inOrder()
        assertThat(result.map { it.createCell }).containsExactly(false, true).inOrder()
        assertThat(result.map { it.allocatedCu }).containsExactly(BigDecimal("6"), BigDecimal("6")).inOrder()
        assertThat(result.all { it.identity.baseLocationId == LocationId("cold-a") }).isTrue()
        assertThat(result.all { it.identity.expiryZone == ExpiryCapacityZone.ATTENTION }).isTrue()
    }

    @Test
    fun `missing production or expiry date uses unclassified capacity group`() {
        val identity = CapacityCellIdentity(LocationId("ambient-a"), ExpiryCapacityZone.UNCLASSIFIED, 1)
        assertThat(identity.displayName("常温-A")).isEqualTo("常温-A（待补充效期）-01")
    }

    @Test
    fun `normal zone omits redundant zone label`() {
        val identity = CapacityCellIdentity(LocationId("ambient-a"), ExpiryCapacityZone.NORMAL, 3)
        assertThat(identity.displayName("常温-A")).isEqualTo("常温-A-03")
    }
}
