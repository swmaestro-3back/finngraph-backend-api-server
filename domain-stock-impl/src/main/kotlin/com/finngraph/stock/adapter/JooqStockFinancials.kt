package com.finngraph.stock.adapter

import com.finngraph.stock.AnnualFinancials
import com.finngraph.stock.StockFinancialsPort
import com.finngraph.stock.Ticker
import com.finngraph.stock.adapter.jooq.tables.references.COMPANY_FINANCIALS
import com.finngraph.stock.adapter.jooq.tables.references.DIVIDENDS
import com.finngraph.stock.adapter.jooq.tables.references.STOCKS
import com.finngraph.stock.adapter.jooq.tables.references.VALUATION_DAILY
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

        return statements.keys.sorted().map { year ->
            val perFsDiv = statements.getValue(year)
            val body = perFsDiv.consolidated ?: perFsDiv.separate
            val valuation = valuations[year]
            val eps = body?.eps ?: valuation?.eps
            val dps = dividends[year]

            AnnualFinancials(
                year = year,
                revenue = body?.revenue,
                operatingProfit = body?.operatingIncome,
                netIncome = body?.netIncome,
                operatingMargin = percentage(body?.operatingIncome?.toDecimal(), body?.revenue?.toDecimal()),
                roe = body?.roe,
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
            )
        }
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

    private fun yearEndValuations(stockId: Long): Map<Int, Valuation> {
        val tradeYear = DSL.extract(VALUATION_DAILY.TRADE_DATE, DatePart.YEAR)
        val year = tradeYear.`as`(TRADE_YEAR)
        val rank = DSL.rowNumber().over(
            DSL.partitionBy(tradeYear).orderBy(VALUATION_DAILY.TRADE_DATE.desc()),
        ).`as`(RANK)

        val ranked = dsl.select(
            year,
            VALUATION_DAILY.EPS,
            VALUATION_DAILY.PER,
            VALUATION_DAILY.PBR,
            rank,
        )
            .from(VALUATION_DAILY)
            .where(VALUATION_DAILY.LISTING_ID.eq(stockId))
            .asTable(RANKED)

        val yearColumn = ranked.column(year)
        val epsColumn = ranked.column(VALUATION_DAILY.EPS)
        val perColumn = ranked.column(VALUATION_DAILY.PER)
        val pbrColumn = ranked.column(VALUATION_DAILY.PBR)

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
        val recordYear = DSL.extract(DIVIDENDS.RECORD_DATE, DatePart.YEAR)

        return dsl.select(recordYear, DIVIDENDS.DPS)
            .from(DIVIDENDS)
            .where(DIVIDENDS.LISTING_ID.eq(stockId))
            .fetch()
            .mapNotNull { record ->
                val year = record.get(recordYear) ?: return@mapNotNull null
                val dps = record.get(DIVIDENDS.DPS) ?: return@mapNotNull null
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
        val totalAssets: Long?,
        val totalLiabilities: Long?,
        val totalEquity: Long?,
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
        const val CONSOLIDATED = "CFS"
        const val SEPARATE = "OFS"
        const val RANKED = "ranked"
        const val RANK = "rn"
        const val FISCAL_YEAR = "fiscal_year"
        const val TRADE_YEAR = "trade_year"
        const val SCALE = 4
        val HUNDRED: BigDecimal = BigDecimal.valueOf(100)
    }
}
