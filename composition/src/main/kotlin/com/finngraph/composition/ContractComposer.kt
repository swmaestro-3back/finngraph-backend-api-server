package com.finngraph.composition

import com.finngraph.stock.model.SupplyContract
import com.finngraph.stock.model.Ticker
import com.finngraph.stock.port.StockContractPort
import org.springframework.stereotype.Component
import java.math.BigDecimal

enum class ContractRole { FILER, COUNTERPARTY }

enum class ContractSort { SALES_RATIO, CONTRACT_AMOUNT }

data class ContractItem(
    val contract: SupplyContract,
    val contractAmount: Long?,
    val salesRatio: BigDecimal?,
)

data class StockContract(
    val item: ContractItem,
    val role: ContractRole,
    val counterpartyName: String?,
    val counterpartyTicker: String?,
)

@Component
class ContractComposer(private val stockContract: StockContractPort) {

    fun forStock(ticker: Ticker, limit: Int): List<StockContract> =
        stockContract.findByParty(ticker, limit).map { toStockContract(it, ticker) }

    fun recent(days: Int, limit: Int, sort: ContractSort): List<ContractItem> {
        val latest = stockContract.findLatestReceiptDate() ?: return emptyList()
        return stockContract.findReceivedSince(latest.minusDays(days.toLong()))
            .map(::parse)
            .sortedWith(comparator(sort))
            .take(limit)
    }

    private fun toStockContract(contract: SupplyContract, ticker: Ticker): StockContract {
        val item = parse(contract)
        return if (contract.filerTicker == ticker.value) {
            StockContract(
                item = item,
                role = ContractRole.FILER,
                counterpartyName = contract.counterpartyCorpName ?: contract.counterparty,
                counterpartyTicker = contract.counterpartyTicker,
            )
        } else {
            StockContract(
                item = item,
                role = ContractRole.COUNTERPARTY,
                counterpartyName = contract.filerName,
                counterpartyTicker = contract.filerTicker,
            )
        }
    }

    private fun parse(contract: SupplyContract) = ContractItem(
        contract = contract,
        contractAmount = contract.contractAmountText?.trim()?.toLongOrNull(),
        salesRatio = contract.salesRatioText?.trim()?.toBigDecimalOrNull(),
    )

    private fun comparator(sort: ContractSort): Comparator<ContractItem> = when (sort) {
        ContractSort.SALES_RATIO ->
            compareBy<ContractItem, BigDecimal?>(nullsLast(reverseOrder())) { it.salesRatio }
        ContractSort.CONTRACT_AMOUNT ->
            compareBy<ContractItem, Long?>(nullsLast(reverseOrder())) { it.contractAmount }
    }
}
