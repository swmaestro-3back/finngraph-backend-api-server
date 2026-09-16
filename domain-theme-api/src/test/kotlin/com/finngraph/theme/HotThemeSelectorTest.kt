package com.finngraph.theme

import com.finngraph.theme.model.ThemeSummary
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

class HotThemeSelectorTest {

    @Test
    fun `상승 절반 하락 절반을 등락률 순으로 뽑는다`() {
        val themes = listOf(
            summary("완만상승", "1.5"),
            summary("급등", "5.0"),
            summary("급락", "-4.0"),
            summary("완만하락", "-0.5"),
            summary("상승", "3.0"),
            summary("하락", "-2.0"),
        )

        val selected = HotThemeSelector.select(themes, 4)

        assertEquals(listOf("급등", "상승", "급락", "하락"), selected.map { it.name })
    }

    @Test
    fun `등락률이 null이거나 0이면 제외한다`() {
        val themes = listOf(
            summary("널", null),
            summary("보합", "0"),
            summary("상승", "1.0"),
            summary("하락", "-1.0"),
        )

        val selected = HotThemeSelector.select(themes, 4)

        assertEquals(listOf("상승", "하락"), selected.map { it.name })
    }

    @Test
    fun `홀수 count는 상승 쪽이 한 개 더 많다`() {
        val themes = listOf(
            summary("상1", "3.0"),
            summary("상2", "2.0"),
            summary("상3", "1.0"),
            summary("하1", "-3.0"),
            summary("하2", "-2.0"),
            summary("하3", "-1.0"),
        )

        val selected = HotThemeSelector.select(themes, 5)

        assertEquals(listOf("상1", "상2", "상3", "하1", "하2"), selected.map { it.name })
    }

    @Test
    fun `한쪽이 부족하면 채우지 않고 있는 만큼만 반환한다`() {
        val themes = listOf(
            summary("상1", "3.0"),
            summary("상2", "2.0"),
            summary("상3", "1.0"),
            summary("하1", "-1.0"),
        )

        val selected = HotThemeSelector.select(themes, 6)

        assertEquals(listOf("상1", "상2", "상3", "하1"), selected.map { it.name })
    }

    @Test
    fun `동률이면 입력 순서를 유지한다`() {
        val themes = listOf(
            summary("먼저", "1.0"),
            summary("나중", "1.0"),
            summary("하락", "-1.0"),
        )

        val selected = HotThemeSelector.select(themes, 4)

        assertEquals(listOf("먼저", "나중", "하락"), selected.map { it.name })
    }

    @Test
    fun `count보다 후보가 많으면 상하 각각 잘라낸다`() {
        val themes = (1..30).map { summary("상$it", "$it.0") } +
            (1..30).map { summary("하$it", "-$it.0") }

        val selected = HotThemeSelector.select(themes, 40)

        assertEquals(40, selected.size)
        assertEquals("상30", selected.first().name)
        assertEquals("하30", selected[20].name)
    }

    private fun summary(name: String, change: String?) = ThemeSummary(
        id = name.hashCode().toLong(),
        name = name,
        description = null,
        baseDate = null,
        change = change?.let(::BigDecimal),
        tradingValue = null,
        marketCap = null,
        w1 = null,
        m1 = null,
        m3 = null,
        stockCount = 0,
        topStocks = emptyList(),
    )
}
