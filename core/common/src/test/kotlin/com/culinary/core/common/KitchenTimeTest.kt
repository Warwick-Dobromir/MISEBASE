package com.culinary.core.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 步骤时间的显示/输入规则（任务书第五阶段 §5.3）。 */
class KitchenTimeTest {
    @Test
    fun `continuous digits fill from the seconds position`() {
        assertThat(KitchenTime.secondsOfDigits("155")).isEqualTo(115)
        assertThat(KitchenTime.format(KitchenTime.secondsOfDigits("155"))).isEqualTo("00/01/55")
    }

    @Test
    fun `empty input is zero time`() {
        assertThat(KitchenTime.secondsOfDigits("")).isEqualTo(0)
        assertThat(KitchenTime.format(0)).isEqualTo("00/00/00")
    }

    @Test
    fun `six digits carry into hours minutes seconds`() {
        assertThat(KitchenTime.secondsOfDigits("013000")).isEqualTo(5400)
        assertThat(KitchenTime.format(5400)).isEqualTo("01/30/00")
        assertThat(KitchenTime.digitsOf(5400)).isEqualTo("013000")
    }

    @Test
    fun `more than six digits keeps the newest six`() {
        assertThat(KitchenTime.digits("1234567")).isEqualTo("234567")
        assertThat(KitchenTime.secondsOfDigits("1234567")).isEqualTo(
            KitchenTime.secondsOfDigits("234567"),
        )
    }

    @Test
    fun `describe renders readable preset labels`() {
        assertThat(KitchenTime.describe(1800)).isEqualTo("30分钟")
        assertThat(KitchenTime.describe(5400)).isEqualTo("1小时30分钟")
        assertThat(KitchenTime.describe(45)).isEqualTo("45秒")
        assertThat(KitchenTime.describe(0)).isEqualTo("0秒")
    }

    @Test
    fun `countdown switches to hours only when needed`() {
        assertThat(KitchenTime.countdown(65)).isEqualTo("01:05")
        assertThat(KitchenTime.countdown(3661)).isEqualTo("1:01:01")
    }

    @Test
    fun `format clamps to the supported range`() {
        assertThat(KitchenTime.format(-10)).isEqualTo("00/00/00")
        assertThat(KitchenTime.format(Int.MAX_VALUE)).isEqualTo("99/59/59")
    }
}
