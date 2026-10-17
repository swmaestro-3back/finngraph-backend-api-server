package com.finngraph.stock.model

import java.time.LocalDate

data class SupplyContract(
    val rceptNo: String,
    val rceptDate: LocalDate,
    val reportName: String,
    val filerTicker: String?,
    val filerName: String?,
    val filerCorpCode: String,
    val filerMarket: String?,
    val contractType: String?,
    val contractName: String?,
    val counterparty: String?,
    val counterpartyCorpName: String?,
    val counterpartyTicker: String?,
    val contractAmountText: String?,
    val salesRatioText: String?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val link: String,
    val isCorrection: Boolean,
    val correctionReason: String?,
)
