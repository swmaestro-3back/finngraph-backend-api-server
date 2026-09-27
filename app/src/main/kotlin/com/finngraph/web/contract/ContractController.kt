package com.finngraph.web.contract

import com.finngraph.composition.ContractComposer
import com.finngraph.composition.ContractSort
import com.finngraph.web.common.DataResponse
import com.finngraph.web.common.InvalidParameterException
import org.springframework.web.bind.annotation.RestController

@RestController
class ContractController(
    private val contractComposer: ContractComposer,
) : ContractApi {

    override fun recent(days: Int, limit: Int, sort: String): DataResponse<List<RecentContractResponse>> {
        val resolvedSort = validateRecentParams(days, limit, sort)
        return DataResponse(
            contractComposer.recent(days, limit, resolvedSort).map(RecentContractResponse::from),
        )
    }

    private fun validateRecentParams(days: Int, limit: Int, sort: String): ContractSort {
        val parsed = SORT_KEYS[sort]
        val errors = buildMap {
            if (days !in 1..MAX_DAYS) put("days", "must be between 1 and $MAX_DAYS")
            if (limit !in 1..MAX_LIMIT) put("limit", "must be between 1 and $MAX_LIMIT")
            if (parsed == null) put("sort", "must be one of ${SORT_KEYS.keys.joinToString(", ")}")
        }
        if (errors.isNotEmpty()) {
            throw InvalidParameterException("최근 공급계약 파라미터가 올바르지 않습니다", errors)
        }
        return checkNotNull(parsed)
    }

    private companion object {
        const val MAX_DAYS = 90
        const val MAX_LIMIT = 100
        val SORT_KEYS = mapOf(
            "salesRatio" to ContractSort.SALES_RATIO,
            "contractAmount" to ContractSort.CONTRACT_AMOUNT,
        )
    }
}
