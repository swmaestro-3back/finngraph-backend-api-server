package com.finngraph.theme

import com.finngraph.theme.model.HotSide
import com.finngraph.theme.model.MarketStats
import com.finngraph.theme.model.ThemeSummary
import java.math.BigDecimal

object HotThemeSelector {

    const val MIN_PRICED_STOCKS = 5
    const val MIN_PRICED_PERCENT = 70

    val MIN_COVERAGE: BigDecimal = BigDecimal("0.8")
    val MIN_EXCESS: BigDecimal = BigDecimal("0.5")

    fun select(themes: List<ThemeSummary>, market: MarketStats, count: Int): List<ThemeSummary> {
        val annotated = annotate(themes, market)
        val median = market.medianChange ?: return emptyList()
        val ups = annotated
            .filter { it.hotSide == HotSide.UP }
            .sortedByDescending { requireNotNull(it.changeLower).subtract(median) }
        val downs = annotated
            .filter { it.hotSide == HotSide.DOWN }
            .sortedBy { requireNotNull(it.changeUpper).subtract(median) }
        return ups.take((count + 1) / 2) + downs.take(count / 2)
    }

    fun annotate(themes: List<ThemeSummary>, market: MarketStats): List<ThemeSummary> =
        themes.map { it.copy(hotSide = hotSide(it, market)) }

    fun hotSide(theme: ThemeSummary, market: MarketStats): HotSide? {
        if (!covered(market)) return null
        val median = market.medianChange ?: return null
        val change = theme.change ?: return null
        if (theme.pricedCount < MIN_PRICED_STOCKS) return null
        if (theme.pricedCount * 100 < theme.stockCount * MIN_PRICED_PERCENT) return null

        return when {
            change.signum() > 0 &&
                theme.weightedChange?.let { it.signum() > 0 } == true &&
                majority(theme.upCount, theme.pricedCount) &&
                broader(theme.upCount, theme.pricedCount, market.upCount, market.pricedCount) &&
                theme.changeLower?.subtract(median)?.let { it >= MIN_EXCESS } == true -> HotSide.UP

            change.signum() < 0 &&
                theme.weightedChange?.let { it.signum() < 0 } == true &&
                majority(theme.downCount, theme.pricedCount) &&
                broader(theme.downCount, theme.pricedCount, market.downCount, market.pricedCount) &&
                theme.changeUpper?.subtract(median)?.let { it <= MIN_EXCESS.negate() } == true -> HotSide.DOWN

            else -> null
        }
    }

    private fun covered(market: MarketStats): Boolean =
        market.coverage?.let { it >= MIN_COVERAGE } == true

    private fun majority(aligned: Int, priced: Int): Boolean = aligned * 2 >= priced

    private fun broader(aligned: Int, priced: Int, marketAligned: Int, marketPriced: Int): Boolean =
        aligned.toLong() * marketPriced >= priced.toLong() * marketAligned
}
