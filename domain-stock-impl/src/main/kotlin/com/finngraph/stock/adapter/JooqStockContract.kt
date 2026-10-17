package com.finngraph.stock.adapter

import com.finngraph.stock.adapter.jooq.tables.references.DISCLOSURES
import com.finngraph.stock.model.SupplyContract
import com.finngraph.stock.model.Ticker
import com.finngraph.stock.port.StockContractPort
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Record
import org.jooq.SortField
import org.jooq.impl.DSL
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class JooqStockContract(private val dsl: DSLContext) : StockContractPort {

    override fun findByParty(ticker: Ticker, limit: Int): List<SupplyContract> =
        selectContracts()
            .where(DISCLOSURES.TICKER.eq(ticker.value).or(DISCLOSURES.COUNTERPARTY_TICKER.eq(ticker.value)))
            .and(LATEST_IN_CHAIN)
            .orderBy(RECENT_FIRST)
            .limit(limit)
            .fetch { it.toContract() }

    override fun findLatestReceiptDate(): LocalDate? =
        dsl.select(DSL.max(DISCLOSURES.RCEPT_DT)).from(DISCLOSURES).fetchOne()?.value1()

    override fun findReceivedSince(from: LocalDate): List<SupplyContract> =
        selectContracts()
            .where(DISCLOSURES.RCEPT_DT.ge(from))
            .and(LATEST_IN_CHAIN)
            .orderBy(RECENT_FIRST)
            .fetch { it.toContract() }

    override fun findEndingBetween(from: LocalDate, to: LocalDate): List<SupplyContract> =
        selectContracts()
            .where(DISCLOSURES.END_DATE.between(from, to))
            .and(LATEST_IN_CHAIN)
            .orderBy(DISCLOSURES.END_DATE.asc(), DISCLOSURES.RCEPT_NO.desc())
            .fetch { it.toContract() }

    override fun findByRceptNos(rceptNos: Collection<String>): List<SupplyContract> {
        if (rceptNos.isEmpty()) return emptyList()
        return selectContracts()
            .where(DISCLOSURES.RCEPT_NO.`in`(rceptNos.distinct()))
            .fetch { it.toContract() }
    }

    private fun selectContracts() =
        dsl.select(
            DISCLOSURES.RCEPT_NO,
            DISCLOSURES.RCEPT_DT,
            DISCLOSURES.REPORT_NM,
            DISCLOSURES.TICKER,
            DISCLOSURES.FLR_NM,
            DISCLOSURES.CORP_CODE,
            DISCLOSURES.CORP_CLS,
            DISCLOSURES.CONTRACT_TYPE,
            DISCLOSURES.CONTRACT_NAME,
            DISCLOSURES.COUNTERPARTY,
            DISCLOSURES.COUNTERPARTY_CORP_NAME,
            DISCLOSURES.COUNTERPARTY_TICKER,
            CONTRACT_AMOUNT,
            SALES_RATIO,
            DISCLOSURES.START_DATE,
            DISCLOSURES.END_DATE,
            DISCLOSURES.LINK,
            DISCLOSURES.IS_CORRECTION,
            DISCLOSURES.CORRECTION_REASON,
        )
            .from(DISCLOSURES)

    private fun Record.toContract() = SupplyContract(
        rceptNo = requireNotNull(get(DISCLOSURES.RCEPT_NO)),
        rceptDate = requireNotNull(get(DISCLOSURES.RCEPT_DT)),
        reportName = requireNotNull(get(DISCLOSURES.REPORT_NM)),
        filerTicker = get(DISCLOSURES.TICKER),
        filerName = get(DISCLOSURES.FLR_NM),
        filerCorpCode = requireNotNull(get(DISCLOSURES.CORP_CODE)),
        filerMarket = get(DISCLOSURES.CORP_CLS),
        contractType = get(DISCLOSURES.CONTRACT_TYPE),
        contractName = get(DISCLOSURES.CONTRACT_NAME),
        counterparty = get(DISCLOSURES.COUNTERPARTY),
        counterpartyCorpName = get(DISCLOSURES.COUNTERPARTY_CORP_NAME),
        counterpartyTicker = get(DISCLOSURES.COUNTERPARTY_TICKER),
        contractAmountText = get(CONTRACT_AMOUNT),
        salesRatioText = get(SALES_RATIO),
        startDate = get(DISCLOSURES.START_DATE),
        endDate = get(DISCLOSURES.END_DATE),
        link = requireNotNull(get(DISCLOSURES.LINK)),
        isCorrection = get(DISCLOSURES.IS_CORRECTION) ?: false,
        correctionReason = get(DISCLOSURES.CORRECTION_REASON),
    )

    companion object {
        private const val LATER = "later"
        private const val CONTRACT_AMOUNT_KEY = "contract_amount"
        private const val SALES_RATIO_KEY = "sales_ratio"

        private val CONTRACT_AMOUNT: Field<String?> =
            DSL.jsonbGetAttributeAsText(DISCLOSURES.FIELDS, CONTRACT_AMOUNT_KEY).`as`(CONTRACT_AMOUNT_KEY)

        private val SALES_RATIO: Field<String?> =
            DSL.jsonbGetAttributeAsText(DISCLOSURES.FIELDS, SALES_RATIO_KEY).`as`(SALES_RATIO_KEY)

        private val LATEST_IN_CHAIN: Condition = DISCLOSURES.`as`(LATER).let { later ->
            DSL.notExists(
                DSL.selectOne()
                    .from(later)
                    .where(
                        later.ORIGINAL_RCEPT_NO.eq(DISCLOSURES.ORIGINAL_RCEPT_NO)
                            .and(later.RCEPT_NO.gt(DISCLOSURES.RCEPT_NO)),
                    ),
            )
        }

        private val RECENT_FIRST: List<SortField<*>> = listOf(
            DISCLOSURES.RCEPT_DT.desc(),
            DISCLOSURES.RCEPT_NO.desc(),
        )
    }
}
