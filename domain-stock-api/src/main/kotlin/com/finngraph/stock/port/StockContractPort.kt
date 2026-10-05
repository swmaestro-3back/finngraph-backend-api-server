package com.finngraph.stock.port

import com.finngraph.stock.model.SupplyContract
import com.finngraph.stock.model.Ticker
import java.time.LocalDate

interface StockContractPort {

    fun findByParty(ticker: Ticker, limit: Int): List<SupplyContract>
    fun findLatestReceiptDate(): LocalDate?
    fun findReceivedSince(from: LocalDate): List<SupplyContract>
    fun findEndingBetween(from: LocalDate, to: LocalDate): List<SupplyContract>
    fun findByRceptNos(rceptNos: Collection<String>): List<SupplyContract>
}
