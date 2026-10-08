package com.finngraph.web.stock

import com.finngraph.composition.StockThemeComparison
import com.finngraph.composition.StockThemeEntry
import com.finngraph.stock.model.PeerMetric
import java.math.BigDecimal
import java.time.LocalDate

data class StockThemeResponse(
    val id: Long,
    val name: String,
    val stockCount: Int,
    val change: BigDecimal?,
    val primary: Boolean,
) {
    companion object {
        fun from(entry: StockThemeEntry) = StockThemeResponse(
            id = entry.id,
            name = entry.name,
            stockCount = entry.stockCount,
            change = entry.change,
            primary = entry.primary,
        )
    }
}

data class PeerMetricResponse<T : Number>(
    val value: T?,
    val rank: Int?,
    val count: Int,
    val median: T?,
) {
    companion object {
        fun <T : Number> from(metric: PeerMetric<T>) = PeerMetricResponse(
            value = metric.value,
            rank = metric.rank,
            count = metric.count,
            median = metric.median,
        )
    }
}

data class ThemeCompareMetricsResponse(
    val change: PeerMetricResponse<BigDecimal>,
    val marketCap: PeerMetricResponse<Long>,
    val tradeValue: PeerMetricResponse<Long>,
    val per: PeerMetricResponse<BigDecimal>,
    val pbr: PeerMetricResponse<BigDecimal>,
    val roe: PeerMetricResponse<BigDecimal>,
    val dividendYield: PeerMetricResponse<BigDecimal>,
)

data class StockThemeCompareResponse(
    val themeId: Long,
    val themeName: String,
    val memberCount: Int,
    val baseDate: LocalDate?,
    val valuationDate: LocalDate?,
    val metrics: ThemeCompareMetricsResponse,
) {
    companion object {
        fun from(found: StockThemeComparison): StockThemeCompareResponse {
            val comparison = found.comparison
            return StockThemeCompareResponse(
                themeId = found.themeId,
                themeName = found.themeName,
                memberCount = comparison.memberCount,
                baseDate = comparison.baseDate,
                valuationDate = comparison.valuationDate,
                metrics = ThemeCompareMetricsResponse(
                    change = PeerMetricResponse.from(comparison.change),
                    marketCap = PeerMetricResponse.from(comparison.marketCap),
                    tradeValue = PeerMetricResponse.from(comparison.tradeValue),
                    per = PeerMetricResponse.from(comparison.per),
                    pbr = PeerMetricResponse.from(comparison.pbr),
                    roe = PeerMetricResponse.from(comparison.roe),
                    dividendYield = PeerMetricResponse.from(comparison.dividendYield),
                ),
            )
        }
    }
}
