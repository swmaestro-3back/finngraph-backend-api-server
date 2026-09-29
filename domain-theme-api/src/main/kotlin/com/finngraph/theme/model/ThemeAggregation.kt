package com.finngraph.theme.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

data class StockObservation(
    val id: Long,
    val ticker: String,
    val name: String,
    val market: String,
    val isActive: Boolean,
    val tradingSuspended: Boolean,
    val underAdministration: Boolean,
    val delistingTrade: Boolean,
    val preferredStock: Boolean,
    val etp: Boolean,
    val spac: Boolean,
    val close: BigDecimal?,
    val volume: Long?,
    val tradeValue: Long?,
    val prevDate: LocalDate?,
    val prevClose: BigDecimal?,
    val marketCap: Long?,
    val r1w: BigDecimal?,
    val r1m: BigDecimal?,
    val r3m: BigDecimal?,
) {
    val inUniverse: Boolean
        get() = isActive && !delistingTrade && !preferredStock && !etp && !spac

    fun dailyChange(prevTradingDate: LocalDate?): DailyChange {
        if (close == null) return DailyChange(ChangeStatus.NO_CANDLE, null)
        if (tradingSuspended || volume == null || volume <= 0) return DailyChange(ChangeStatus.SUSPENDED, null)
        if (prevTradingDate == null || prevDate != prevTradingDate) return DailyChange(ChangeStatus.NO_PREV, null)
        if (prevClose == null || prevClose.signum() == 0) return DailyChange(ChangeStatus.NO_PREV, null)
        val change = close.subtract(prevClose)
            .multiply(HUNDRED)
            .divide(prevClose, ThemeMetrics.VALUE_SCALE, RoundingMode.HALF_UP)
        return DailyChange(ChangeStatus.PRICED, change)
    }

    private companion object {
        val HUNDRED = BigDecimal(100)
    }
}

data class DailyChange(
    val status: ChangeStatus,
    val change: BigDecimal?,
)

data class ThemeMember(
    val stock: StockObservation,
    val reason: String?,
)

data class ThemeIdentity(
    val id: Long,
    val name: String,
    val description: String?,
    val sources: List<String>,
)

object ThemeAggregation {

    const val TOP_STOCK_COUNT = 3
    const val LEADER_COUNT = 2
    const val RATIO_SCALE = 4

    fun summary(
        theme: ThemeIdentity,
        members: List<ThemeMember>,
        baseDate: LocalDate?,
        prevTradingDate: LocalDate?,
        averages: Map<Long, TradeValueAverage> = emptyMap(),
    ): ThemeSummary {
        val evaluation = evaluate(members, prevTradingDate)
        val universe = evaluation.universe
        val priced = universe.filter { it.change != null }
        val changes = priced.map { requireNotNull(it.change) }
        val metrics = evaluation.metrics
        val ratio = tradingValueRatio(priced, averages)
        val w1 = ThemeMetrics.of(priced.map { it.member.stock.r1w })
        val m1 = ThemeMetrics.of(priced.map { it.member.stock.r1m })
        val m3 = ThemeMetrics.of(priced.map { it.member.stock.r3m })

        return ThemeSummary(
            id = theme.id,
            name = theme.name,
            description = theme.description,
            baseDate = baseDate,
            change = metrics.value,
            tradingValue = priced.mapNotNull { it.member.stock.tradeValue }.sumOrNull(),
            avgTradingValue = ratio?.average,
            tradingValueRatio = ratio?.ratio,
            marketCap = universe.mapNotNull { it.member.stock.marketCap }.sumOrNull(),
            w1 = w1.value,
            m1 = m1.value,
            m3 = m3.value,
            stockCount = universe.size,
            pricedCount = priced.size,
            upCount = changes.count { it.signum() > 0 },
            downCount = changes.count { it.signum() < 0 },
            flatCount = changes.count { it.signum() == 0 },
            suspendedCount = universe.count { it.status == ChangeStatus.SUSPENDED },
            trimCount = metrics.trimCount,
            meanChange = metrics.mean,
            changeLower = metrics.lower,
            changeUpper = metrics.upper,
            sensitivity = metrics.sensitivity,
            w1Count = w1.count,
            m1Count = m1.count,
            m3Count = m3.count,
            leaders = leaders(priced, metrics.value),
            sources = theme.sources,
            hotSide = null,
            topStocks = universe
                .map { it.member.stock }
                .sortedWith(TOP_STOCK_ORDER)
                .take(TOP_STOCK_COUNT)
                .map { ThemeTopStock(it.ticker, it.name, it.marketCap) },
        )
    }

    fun stockViews(members: List<ThemeMember>, prevTradingDate: LocalDate?): List<ThemeStockView> {
        val evaluation = evaluate(members, prevTradingDate)
        val delisting = members
            .filter { it.stock.isActive && it.stock.delistingTrade }
            .map { Evaluated(it, ChangeStatus.DELISTING, null) }
        return (evaluation.universe + delisting)
            .sortedWith(STOCK_VIEW_ORDER)
            .map { it.toView() }
    }

    fun marketStats(baseDate: LocalDate?, stocks: List<StockObservation>, prevTradingDate: LocalDate?): MarketStats {
        val active = stocks.filter { it.isActive }
        val changes = active
            .filter { it.inUniverse }
            .mapNotNull { it.dailyChange(prevTradingDate).change }
        return MarketStats.of(
            baseDate = baseDate,
            changes = changes,
            candleCount = active.count { it.close != null },
            activeCount = active.size,
        )
    }

    private fun evaluate(members: List<ThemeMember>, prevTradingDate: LocalDate?): Evaluation {
        val universe = members
            .filter { it.stock.inUniverse }
            .map { member ->
                val daily = member.stock.dailyChange(prevTradingDate)
                Evaluated(member, daily.status, daily.change)
            }
        val metrics = ThemeMetrics.of(universe.map { it.change })
        val marked = universe.mapIndexed { index, evaluated ->
            if (index in metrics.trimmedIndexes) evaluated.copy(status = ChangeStatus.TRIMMED) else evaluated
        }
        return Evaluation(marked, metrics)
    }

    private fun tradingValueRatio(priced: List<Evaluated>, averages: Map<Long, TradeValueAverage>): TradingValueRatio? {
        val matched = priced.mapNotNull { evaluated ->
            val stock = evaluated.member.stock
            val tradeValue = stock.tradeValue ?: return@mapNotNull null
            val average = averages[stock.id] ?: return@mapNotNull null
            tradeValue to average.average
        }
        if (matched.isEmpty()) return null

        val numerator = BigDecimal(matched.sumOf { it.first })
        val denominator = matched.fold(BigDecimal.ZERO) { acc, pair -> acc.add(pair.second) }
        if (denominator.signum() <= 0) return null

        return TradingValueRatio(
            average = denominator.setScale(0, RoundingMode.HALF_UP).toLong(),
            ratio = numerator.divide(denominator, RATIO_SCALE, RoundingMode.HALF_UP),
        )
    }

    private fun leaders(priced: List<Evaluated>, themeChange: BigDecimal?): List<ThemeLeader> {
        val ordered = when (themeChange?.signum()) {
            1 -> priced.sortedByDescending { it.change }
            -1 -> priced.sortedBy { it.change }
            else -> priced.sortedByDescending { requireNotNull(it.change).abs() }
        }
        return ordered
            .take(LEADER_COUNT)
            .map { ThemeLeader(it.member.stock.ticker, it.member.stock.name, requireNotNull(it.change)) }
    }

    private fun List<Long>.sumOrNull(): Long? = if (isEmpty()) null else sum()

    private data class Evaluated(
        val member: ThemeMember,
        val status: ChangeStatus,
        val change: BigDecimal?,
    ) {
        fun toView(): ThemeStockView {
            val stock = member.stock
            return ThemeStockView(
                ticker = stock.ticker,
                name = stock.name,
                market = stock.market,
                price = stock.close,
                change = change,
                changeStatus = status,
                tradingSuspended = stock.tradingSuspended,
                underAdministration = stock.underAdministration,
                delistingTrade = stock.delistingTrade,
                tradingValue = stock.tradeValue,
                marketCap = stock.marketCap,
                reason = member.reason,
            )
        }
    }

    private data class Evaluation(
        val universe: List<Evaluated>,
        val metrics: ThemeMetrics,
    )

    private data class TradingValueRatio(
        val average: Long,
        val ratio: BigDecimal,
    )

    private val TOP_STOCK_ORDER: Comparator<StockObservation> =
        compareBy<StockObservation> { it.marketCap == null }
            .thenByDescending { it.marketCap ?: 0L }
            .thenBy { it.id }

    private val STOCK_VIEW_ORDER: Comparator<Evaluated> =
        compareBy<Evaluated> { it.status == ChangeStatus.DELISTING }
            .thenBy { it.member.stock.marketCap == null }
            .thenByDescending { it.member.stock.marketCap ?: 0L }
            .thenBy { it.member.stock.ticker }
}
