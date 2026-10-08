package com.finngraph.theme

import com.finngraph.theme.model.HotSide
import com.finngraph.theme.model.MarketStats
import com.finngraph.theme.model.ThemeSummary
import java.math.BigDecimal

object HotThemeSelector {

    const val MIN_PRICED_STOCKS = 5
    const val MIN_PRICED_PERCENT = 70
    const val MAX_OVERLAP_PERCENT = 50

    val MIN_COVERAGE: BigDecimal = BigDecimal("0.8")
    val MIN_EXCESS: BigDecimal = BigDecimal("0.5")

    fun select(
        themes: List<ThemeSummary>,
        market: MarketStats,
        count: Int,
        members: Map<Long, Collection<String>> = emptyMap(),
    ): List<ThemeSummary> {
        val annotated = candidates(themes, market)
        val ups = annotated
            .filter { it.hotSide == HotSide.UP }
            .sortedByDescending { requireNotNull(it.change) }
        val downs = annotated
            .filter { it.hotSide == HotSide.DOWN }
            .sortedBy { requireNotNull(it.change) }
        return withoutOverlap(ups, (count + 1) / 2, members) + withoutOverlap(downs, count / 2, members)
    }

    fun candidates(themes: List<ThemeSummary>, market: MarketStats): List<ThemeSummary> =
        annotate(themes, market).filter { it.hotSide != null }

    fun overlaps(a: Set<String>, b: Set<String>): Boolean {
        val smaller = minOf(a.size, b.size)
        if (smaller == 0) return false
        return a.count { it in b } * 100 > smaller * MAX_OVERLAP_PERCENT
    }

    private fun withoutOverlap(
        ranked: List<ThemeSummary>,
        quota: Int,
        members: Map<Long, Collection<String>>,
    ): List<ThemeSummary> {
        val picked = mutableListOf<Pair<ThemeSummary, Set<String>>>()
        for (theme in ranked) {
            if (picked.size >= quota) break
            val stocks = members[theme.id].orEmpty().toSet()
            if (picked.none { (_, taken) -> overlaps(taken, stocks) }) picked += theme to stocks
        }
        return picked.map { it.first }
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
