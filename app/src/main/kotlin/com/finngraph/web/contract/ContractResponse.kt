package com.finngraph.web.contract

import com.fasterxml.jackson.annotation.JsonProperty
import com.finngraph.composition.ContractItem
import java.math.BigDecimal
import java.time.LocalDate

data class RecentContractResponse(
    val rceptNo: String,
    val rceptDate: LocalDate,
    val reportName: String,
    val filerTicker: String?,
    val filerName: String,
    val filerMarket: String?,
    val contractType: String?,
    val contractName: String?,
    val counterpartyName: String?,
    val counterpartyTicker: String?,
    val contractAmount: Long?,
    val salesRatio: BigDecimal?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val link: String,
    @get:JsonProperty("isCorrection") val isCorrection: Boolean,
) {
    companion object {
        fun from(item: ContractItem): RecentContractResponse {
            val contract = item.contract
            return RecentContractResponse(
                rceptNo = contract.rceptNo,
                rceptDate = contract.rceptDate,
                reportName = contract.reportName,
                filerTicker = contract.filerTicker,
                filerName = contract.filerName ?: contract.filerCorpCode,
                filerMarket = contract.filerMarket,
                contractType = contract.contractType,
                contractName = contract.contractName,
                counterpartyName = contract.counterpartyCorpName ?: contract.counterparty,
                counterpartyTicker = contract.counterpartyTicker,
                contractAmount = item.contractAmount,
                salesRatio = item.salesRatio,
                startDate = contract.startDate,
                endDate = contract.endDate,
                link = contract.link,
                isCorrection = contract.isCorrection,
            )
        }
    }
}
