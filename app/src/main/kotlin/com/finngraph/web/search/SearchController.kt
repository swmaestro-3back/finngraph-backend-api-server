package com.finngraph.web.search

import com.finngraph.composition.search.SearchComposer
import com.finngraph.web.common.DataResponse
import com.finngraph.web.common.InvalidParameterException
import org.springframework.web.bind.annotation.RestController

@RestController
class SearchController(
    private val composer: SearchComposer,
) : SearchApi {

    override fun search(q: String?, limit: Int): DataResponse<SearchResponse> {
        val query = q?.trim().orEmpty()
        val errors = buildMap {
            when {
                query.isEmpty() -> put("q", "must not be blank")
                query.length > MAX_QUERY_LENGTH -> put("q", "must be at most $MAX_QUERY_LENGTH characters")
            }
            if (limit !in 1..MAX_LIMIT) put("limit", "must be between 1 and $MAX_LIMIT")
        }
        if (errors.isNotEmpty()) {
            throw InvalidParameterException("검색 파라미터가 올바르지 않습니다", errors)
        }
        return DataResponse(SearchResponse.from(query, composer.search(query, limit)))
    }

    private companion object {
        const val MAX_QUERY_LENGTH = 50
        const val MAX_LIMIT = 20
    }
}
