package com.finngraph.stock.adapter

import com.finngraph.stock.adapter.jooq.tables.references.COMPANY_ALIASES
import com.finngraph.stock.adapter.jooq.tables.references.STOCKS
import com.finngraph.stock.adapter.jooq.tables.references.STOCK_VALUATIONS_DAILY
import com.finngraph.stock.model.StockSearchHit
import com.finngraph.stock.port.StockSearchPort
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import org.springframework.stereotype.Component

@Component
class JooqStockSearch(private val dsl: DSLContext) : StockSearchPort {

    override fun search(query: String, limit: Int): List<StockSearchHit> {
        val dates = StockPricing.dates(dsl)
        val tier = DSL.case_()
            .`when`(STOCKS.NAME.equalIgnoreCase(query).or(STOCKS.TICKER.eq(query)).or(alias(COMPANY_ALIASES.ALIAS.equalIgnoreCase(query))), 0)
            .`when`(STOCKS.NAME.startsWithIgnoreCase(query).or(alias(COMPANY_ALIASES.ALIAS.startsWithIgnoreCase(query))), 1)
            .otherwise(2)
        val rows = dsl.select(STOCKS.TICKER, STOCKS.NAME, STOCKS.MARKET, STOCKS.COMPANY_ID)
            .from(STOCKS)
            .leftJoin(STOCK_VALUATIONS_DAILY)
            .on(
                STOCK_VALUATIONS_DAILY.LISTING_ID.eq(STOCKS.ID)
                    .and(STOCK_VALUATIONS_DAILY.TRADE_DATE.eq(DSL.`val`(dates.valuation, SQLDataType.LOCALDATE))),
            )
            .where(STOCKS.IS_ACTIVE.eq(true))
            .and(
                STOCKS.NAME.containsIgnoreCase(query)
                    .or(STOCKS.TICKER.startsWith(query))
                    .or(alias(COMPANY_ALIASES.ALIAS.containsIgnoreCase(query))),
            )
            .orderBy(tier.asc(), STOCK_VALUATIONS_DAILY.MARKET_CAP.desc().nullsLast(), STOCKS.TICKER.asc())
            .limit(limit)
            .fetch()

        val aliasNeeded = rows.filterNot { matchesDirectly(it.get(STOCKS.NAME), it.get(STOCKS.TICKER), query) }
            .mapNotNull { it.get(STOCKS.COMPANY_ID) }
            .distinct()
        val aliases = if (aliasNeeded.isEmpty()) {
            emptyMap()
        } else {
            dsl.select(COMPANY_ALIASES.COMPANY_ID, COMPANY_ALIASES.ALIAS)
                .from(COMPANY_ALIASES)
                .where(COMPANY_ALIASES.COMPANY_ID.`in`(aliasNeeded).and(COMPANY_ALIASES.ALIAS.containsIgnoreCase(query)))
                .fetch()
                .groupBy({ requireNotNull(it.value1()) }, { requireNotNull(it.value2()) })
        }

        return rows.map {
            val name = requireNotNull(it.get(STOCKS.NAME))
            val ticker = requireNotNull(it.get(STOCKS.TICKER))
            StockSearchHit(
                ticker = ticker,
                name = name,
                market = requireNotNull(it.get(STOCKS.MARKET)),
                matchedAlias = it.get(STOCKS.COMPANY_ID)?.let(aliases::get)?.let { found -> bestAlias(found, query) },
            )
        }
    }

    private fun alias(match: Condition): Condition =
        DSL.exists(DSL.selectOne().from(COMPANY_ALIASES).where(COMPANY_ALIASES.COMPANY_ID.eq(STOCKS.COMPANY_ID).and(match)))

    private fun matchesDirectly(name: String?, ticker: String?, query: String): Boolean =
        name.orEmpty().contains(query, ignoreCase = true) || ticker.orEmpty().startsWith(query)

    private fun bestAlias(aliases: List<String>, query: String): String? =
        aliases.minWithOrNull(
            compareBy<String> {
                when {
                    it.equals(query, ignoreCase = true) -> 0
                    it.startsWith(query, ignoreCase = true) -> 1
                    else -> 2
                }
            }.thenBy { it.length }.thenBy { it },
        )
}
