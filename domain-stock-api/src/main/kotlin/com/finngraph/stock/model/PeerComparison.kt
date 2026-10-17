package com.finngraph.stock.model

import java.math.BigDecimal
import java.time.LocalDate

data class PeerMetric<T : Number>(
    val value: T?,
    val rank: Int?,
    val count: Int,
    val median: T?,
)

data class PeerComparison(
    val memberCount: Int,
    val baseDate: LocalDate?,
    val valuationDate: LocalDate?,
    val change: PeerMetric<BigDecimal>,
    val marketCap: PeerMetric<Long>,
    val tradeValue: PeerMetric<Long>,
    val per: PeerMetric<BigDecimal>,
    val pbr: PeerMetric<BigDecimal>,
    val roe: PeerMetric<BigDecimal>,
    val dividendYield: PeerMetric<BigDecimal>,
)
