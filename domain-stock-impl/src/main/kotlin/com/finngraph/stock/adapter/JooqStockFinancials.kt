package com.finngraph.stock.adapter

import com.finngraph.stock.adapter.jooq.tables.references.COMPANY_FINANCIALS
import com.finngraph.stock.adapter.jooq.tables.references.STOCK_DIVIDENDS
import com.finngraph.stock.adapter.jooq.tables.references.STOCKS
import com.finngraph.stock.adapter.jooq.tables.references.STOCK_VALUATIONS_DAILY
import com.finngraph.stock.model.AnnualFinancials
import com.finngraph.stock.model.QuarterlyFinancials
import com.finngraph.stock.model.Ticker
import com.finngraph.stock.port.StockFinancialsPort
import org.jooq.DSLContext
import org.jooq.DatePart
import org.jooq.Field
import org.jooq.Table
import org.jooq.impl.DSL
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

@Component
class JooqStockFinancials(private val dsl: DSLContext) : StockFinancialsPort {

    override fun findAnnual(ticker: Ticker): List<AnnualFinancials> {
        val stock = dsl.select(STOCKS.ID, STOCKS.COMPANY_ID)
            .from(STOCKS)
            .where(STOCKS.TICKER.eq(ticker.value).and(STOCKS.IS_ACTIVE.eq(true)))
            .fetchOne() ?: return emptyList()

        val stockId = requireNotNull(stock.get(STOCKS.ID))
        val companyId = stock.get(STOCKS.COMPANY_ID) ?: return emptyList()

        val statements = annualStatements(companyId)
        if (statements.isEmpty()) return emptyList()

        val valuations = yearEndValuations(stockId)
        val dividends = annualDps(stockId)
        val reported = reportedAnnual(companyId)

        return statements.keys.sorted().map { year ->
            val perFsDiv = statements.getValue(year)
            val body = perFsDiv.consolidated ?: perFsDiv.separate
            val valuation = valuations[year]
            val kis = reported[year]
            val eps = body?.eps ?: kis?.eps ?: valuation?.eps
            val dps = dividends[year]

            AnnualFinancials(
                year = year,
                revenue = body?.revenue,
                operatingProfit = body?.operatingIncome,
                netIncome = body?.netIncome,
                operatingMargin = percentage(body?.operatingIncome?.toDecimal(), body?.revenue?.toDecimal()),
                roe = body?.roe ?: kis?.roe,
                debtRatio = percentage(body?.totalLiabilities?.toDecimal(), body?.totalEquity?.toDecimal()),
                totalAssets = body?.totalAssets,
                separateAssets = perFsDiv.separate?.totalAssets,
                totalEquity = body?.totalEquity,
                totalDebt = body?.totalLiabilities,
                eps = eps,
                per = valuation?.per,
                pbr = valuation?.pbr,
                dps = dps,
                payoutRatio = if (eps != null && eps.signum() > 0) percentage(dps, eps) else null,
                bps = body?.bps ?: kis?.bps,
            )
        }
    }

    override fun findQuarterly(ticker: Ticker, limit: Int): List<QuarterlyFinancials> {
        val companyId = dsl.select(STOCKS.COMPANY_ID)
            .from(STOCKS)
            .where(STOCKS.TICKER.eq(ticker.value).and(STOCKS.IS_ACTIVE.eq(true)))
            .fetchOne()
            ?.value1() ?: return emptyList()

        val rows = dsl.select(
            COMPANY_FINANCIALS.FISCAL_YYMM,
            COMPANY_FINANCIALS.FS_DIV,
            COMPANY_FINANCIALS.REVENUE,
            COMPANY_FINANCIALS.OPERATING_INCOME,
            COMPANY_FINANCIALS.NET_INCOME,
            COMPANY_FINANCIALS.BPS,
        )
            .from(COMPANY_FINANCIALS)
            .where(COMPANY_FINANCIALS.COMPANY_ID.eq(companyId))
            .and(COMPANY_FINANCIALS.PERIOD_TYPE.eq(QUARTER))
            .orderBy(
                COMPANY_FINANCIALS.FISCAL_YYMM.asc(),
                COMPANY_FINANCIALS.DISCLOSED_AT.desc().nullsLast(),
                COMPANY_FINANCIALS.UPDATED_AT.desc().nullsLast(),
            )
            .fetch()

        val periods = rows
            .mapNotNull { record ->
                val yymm = record.get(COMPANY_FINANCIALS.FISCAL_YYMM)?.takeIf { it.length == YYMM_LENGTH } ?: return@mapNotNull null
                QuarterRow(
                    year = yymm.take(4).toInt(),
                    quarter = (yymm.takeLast(2).toInt() - 1) / MONTHS_PER_QUARTER + 1,
                    periodEnd = "${yymm.take(4)}-${yymm.takeLast(2)}",
                    fsDiv = record.get(COMPANY_FINANCIALS.FS_DIV),
                    revenue = record.get(COMPANY_FINANCIALS.REVENUE),
                    operatingIncome = record.get(COMPANY_FINANCIALS.OPERATING_INCOME),
                    netIncome = record.get(COMPANY_FINANCIALS.NET_INCOME),
                    bps = record.get(COMPANY_FINANCIALS.BPS),
                )
            }
            .groupBy { it.year to it.quarter }
            .mapValues { (_, found) -> found.firstOrNull { it.fsDiv == CONSOLIDATED } ?: found.first() }

        return periods.values
            .sortedWith(compareBy({ it.year }, { it.quarter }))
            .takeLast(limit)
            .map { row ->
                val lastYear = periods[row.year - 1 to row.quarter]
                QuarterlyFinancials(
                    year = row.year,
                    quarter = row.quarter,
                    periodEnd = row.periodEnd,
                    fsDiv = row.fsDiv,
                    revenue = row.revenue,
                    operatingProfit = row.operatingIncome,
                    netIncome = row.netIncome,
                    operatingMargin = percentage(row.operatingIncome?.toDecimal(), row.revenue?.toDecimal()),
                    bps = row.bps,
                    revenueYoY = growth(row.revenue, lastYear?.revenue),
                    operatingProfitYoY = growth(row.operatingIncome, lastYear?.operatingIncome),
                )
            }
    }

    private fun growth(current: Long?, previous: Long?): BigDecimal? {
        if (current == null || previous == null || previous <= 0) return null
        return percentage((current - previous).toDecimal(), previous.toDecimal())
    }

    private fun annualStatements(companyId: Long): Map<Int, YearStatements> {
        val fiscalYear = DSL.substring(COMPANY_FINANCIALS.FISCAL_YYMM, 1, 4)
        val year = fiscalYear.`as`(FISCAL_YEAR)
        val rank = DSL.rowNumber().over(
            DSL.partitionBy(fiscalYear, COMPANY_FINANCIALS.FS_DIV)
                .orderBy(
                    COMPANY_FINANCIALS.DISCLOSED_AT.desc().nullsLast(),
                    COMPANY_FINANCIALS.RCEPT_NO.desc().nullsLast(),
                ),
        ).`as`(RANK)

        val ranked = dsl.select(
            year,
            COMPANY_FINANCIALS.FS_DIV,
            COMPANY_FINANCIALS.REVENUE,
            COMPANY_FINANCIALS.OPERATING_INCOME,
            COMPANY_FINANCIALS.NET_INCOME,
            COMPANY_FINANCIALS.ROE,
            COMPANY_FINANCIALS.EPS,
            COMPANY_FINANCIALS.BPS,
            COMPANY_FINANCIALS.TOTAL_ASSETS,
            COMPANY_FINANCIALS.TOTAL_LIABILITIES,
            COMPANY_FINANCIALS.TOTAL_EQUITY,
            rank,
        )
            .from(COMPANY_FINANCIALS)
            .where(COMPANY_FINANCIALS.COMPANY_ID.eq(companyId))
            .and(COMPANY_FINANCIALS.PERIOD_TYPE.eq(ANNUAL))
            .asTable(RANKED)

        val yearColumn = ranked.column(year)
        val fsDivColumn = ranked.column(COMPANY_FINANCIALS.FS_DIV)
        val revenueColumn = ranked.column(COMPANY_FINANCIALS.REVENUE)
        val operatingIncomeColumn = ranked.column(COMPANY_FINANCIALS.OPERATING_INCOME)
        val netIncomeColumn = ranked.column(COMPANY_FINANCIALS.NET_INCOME)
        val roeColumn = ranked.column(COMPANY_FINANCIALS.ROE)
        val epsColumn = ranked.column(COMPANY_FINANCIALS.EPS)
        val bpsColumn = ranked.column(COMPANY_FINANCIALS.BPS)
        val totalAssetsColumn = ranked.column(COMPANY_FINANCIALS.TOTAL_ASSETS)
        val totalLiabilitiesColumn = ranked.column(COMPANY_FINANCIALS.TOTAL_LIABILITIES)
        val totalEquityColumn = ranked.column(COMPANY_FINANCIALS.TOTAL_EQUITY)

        return dsl.selectFrom(ranked)
            .where(ranked.column(rank).eq(1))
            .fetch { record ->
                Statement(
                    year = requireNotNull(record.get(yearColumn)).toInt(),
                    fsDiv = record.get(fsDivColumn),
                    revenue = record.get(revenueColumn),
                    operatingIncome = record.get(operatingIncomeColumn),
                    netIncome = record.get(netIncomeColumn),
                    roe = record.get(roeColumn),
                    eps = record.get(epsColumn),
                    bps = record.get(bpsColumn),
                    totalAssets = record.get(totalAssetsColumn),
                    totalLiabilities = record.get(totalLiabilitiesColumn),
                    totalEquity = record.get(totalEquityColumn),
                )
            }
            .groupBy { it.year }
            .mapValues { (_, rows) ->
                YearStatements(
                    consolidated = rows.firstOrNull { it.fsDiv == CONSOLIDATED },
                    separate = rows.firstOrNull { it.fsDiv == SEPARATE },
                )
            }
    }

    private fun reportedAnnual(companyId: Long): Map<Int, ReportedAnnual> =
        dsl.select(COMPANY_FINANCIALS.FISCAL_YYMM, COMPANY_FINANCIALS.ROE, COMPANY_FINANCIALS.BPS, COMPANY_FINANCIALS.EPS)
            .from(COMPANY_FINANCIALS)
            .where(COMPANY_FINANCIALS.COMPANY_ID.eq(companyId))
            .and(COMPANY_FINANCIALS.PERIOD_TYPE.eq(ANNUAL))
            .and(COMPANY_FINANCIALS.SOURCE.eq(KIS))
            .orderBy(
                COMPANY_FINANCIALS.FISCAL_YYMM.asc(),
                DSL.case_().`when`(COMPANY_FINANCIALS.FS_DIV.eq(CONSOLIDATED), 1).otherwise(0).asc(),
            )
            .fetch()
            .groupBy { requireNotNull(it.get(COMPANY_FINANCIALS.FISCAL_YYMM)).take(4).toInt() }
            .mapValues { (_, rows) ->
                ReportedAnnual(
                    roe = rows.lastNotNullOf { it.get(COMPANY_FINANCIALS.ROE) },
                    bps = rows.lastNotNullOf { it.get(COMPANY_FINANCIALS.BPS) },
                    eps = rows.lastNotNullOf { it.get(COMPANY_FINANCIALS.EPS) },
                )
            }

    private fun <T, R : Any> List<T>.lastNotNullOf(pick: (T) -> R?): R? = asReversed().firstNotNullOfOrNull(pick)

    private fun yearEndValuations(stockId: Long): Map<Int, Valuation> {
        val tradeYear = DSL.extract(STOCK_VALUATIONS_DAILY.TRADE_DATE, DatePart.YEAR)
        val year = tradeYear.`as`(TRADE_YEAR)
        val rank = DSL.rowNumber().over(
            DSL.partitionBy(tradeYear).orderBy(STOCK_VALUATIONS_DAILY.TRADE_DATE.desc()),
        ).`as`(RANK)

        val ranked = dsl.select(
            year,
            STOCK_VALUATIONS_DAILY.EPS,
            STOCK_VALUATIONS_DAILY.PER,
            STOCK_VALUATIONS_DAILY.PBR,
            rank,
        )
            .from(STOCK_VALUATIONS_DAILY)
            .where(STOCK_VALUATIONS_DAILY.LISTING_ID.eq(stockId))
            .asTable(RANKED)

        val yearColumn = ranked.column(year)
        val epsColumn = ranked.column(STOCK_VALUATIONS_DAILY.EPS)
        val perColumn = ranked.column(STOCK_VALUATIONS_DAILY.PER)
        val pbrColumn = ranked.column(STOCK_VALUATIONS_DAILY.PBR)

        return dsl.selectFrom(ranked)
            .where(ranked.column(rank).eq(1))
            .fetch()
            .associate { record ->
                requireNotNull(record.get(yearColumn)) to Valuation(
                    eps = record.get(epsColumn),
                    per = record.get(perColumn),
                    pbr = record.get(pbrColumn),
                )
            }
    }

    private fun annualDps(stockId: Long): Map<Int, BigDecimal> {
        val recordYear = DSL.extract(STOCK_DIVIDENDS.RECORD_DATE, DatePart.YEAR)

        return dsl.select(recordYear, STOCK_DIVIDENDS.DPS)
            .from(STOCK_DIVIDENDS)
            .where(STOCK_DIVIDENDS.LISTING_ID.eq(stockId))
            .fetch()
            .mapNotNull { record ->
                val year = record.get(recordYear) ?: return@mapNotNull null
                val dps = record.get(STOCK_DIVIDENDS.DPS) ?: return@mapNotNull null
                year to dps
            }
            .groupingBy { it.first }
            .fold(BigDecimal.ZERO) { sum, (_, dps) -> sum.add(dps) }
    }

    private fun percentage(numerator: BigDecimal?, denominator: BigDecimal?): BigDecimal? {
        if (numerator == null || denominator == null || denominator.signum() == 0) return null
        return numerator.multiply(HUNDRED).divide(denominator, SCALE, RoundingMode.HALF_UP)
    }

    private fun Long.toDecimal(): BigDecimal = BigDecimal.valueOf(this)

    private fun <T> Table<*>.column(source: Field<T>): Field<T> =
        requireNotNull(field(source.name, source.dataType))

    private data class Statement(
        val year: Int,
        val fsDiv: String?,
        val revenue: Long?,
        val operatingIncome: Long?,
        val netIncome: Long?,
        val roe: BigDecimal?,
        val eps: BigDecimal?,
        val bps: BigDecimal?,
        val totalAssets: Long?,
        val totalLiabilities: Long?,
        val totalEquity: Long?,
    )

    private data class ReportedAnnual(
        val roe: BigDecimal?,
        val bps: BigDecimal?,
        val eps: BigDecimal?,
    )

    private data class QuarterRow(
        val year: Int,
        val quarter: Int,
        val periodEnd: String,
        val fsDiv: String?,
        val revenue: Long?,
        val operatingIncome: Long?,
        val netIncome: Long?,
        val bps: BigDecimal?,
    )

    private data class YearStatements(
        val consolidated: Statement?,
        val separate: Statement?,
    )

    private data class Valuation(
        val eps: BigDecimal?,
        val per: BigDecimal?,
        val pbr: BigDecimal?,
    )

    private companion object {
        const val ANNUAL = "A"
        const val QUARTER = "Q"
        const val YYMM_LENGTH = 6
        const val MONTHS_PER_QUARTER = 3
        const val CONSOLIDATED = "CFS"
        const val SEPARATE = "OFS"
        const val KIS = "KIS"
        const val RANKED = "ranked"
        const val RANK = "rn"
        const val FISCAL_YEAR = "fiscal_year"
        const val TRADE_YEAR = "trade_year"
        const val SCALE = 4
        val HUNDRED: BigDecimal = BigDecimal.valueOf(100)
    }
}
