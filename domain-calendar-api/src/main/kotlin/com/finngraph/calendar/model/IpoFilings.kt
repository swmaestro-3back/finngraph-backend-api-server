package com.finngraph.calendar.model

import java.math.BigDecimal
import java.time.LocalDate

enum class FilingStatus { FILED, PRICED }

data class Underwriter(
    val name: String,
    val role: String?,
    val shares: Long?,
    val amount: BigDecimal?,
    val method: String?,
)

data class FundUse(val purpose: String, val amount: BigDecimal)

data class Seller(
    val holder: String,
    val relation: String?,
    val before: Long?,
    val sold: Long?,
    val after: Long?,
)

data class Putback(
    val reason: String?,
    val investors: String?,
    val shares: String?,
    val period: String?,
    val price: String?,
)

data class IpoFiling(
    val corpCode: String,
    val corpName: String,
    val status: FilingStatus,
    val spac: Boolean,
    val firstRceptNo: String,
    val latestRceptNo: String,
    val latestReportName: String,
    val subscrStart: LocalDate?,
    val subscrEnd: LocalDate?,
    val payDate: LocalDate?,
    val offerPrice: BigDecimal?,
    val offerShares: Long?,
    val offerAmount: BigDecimal?,
    val offerMethod: String?,
    val underwriters: List<Underwriter>,
    val fundUses: List<FundUse>,
    val sellers: List<Seller>,
    val putback: Putback?,
    val ticker: String?,
)

data class CompanyProfile(
    val corpCode: String,
    val ceo: String?,
    val establishedOn: LocalDate?,
    val address: String?,
    val homepage: String?,
    val description: String?,
    val descriptionSource: String?,
    val descriptionRceptNo: String?,
)
