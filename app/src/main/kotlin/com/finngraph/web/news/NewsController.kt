package com.finngraph.web.news

import com.finngraph.news.model.NewsId
import com.finngraph.news.model.NewsView
import com.finngraph.news.model.PageResult
import com.finngraph.news.port.NewsQueryPort
import com.finngraph.web.common.DataResponse
import com.finngraph.web.common.ErrorCode
import com.finngraph.web.common.InvalidParameterException
import com.finngraph.web.common.PageResponse
import com.finngraph.web.common.Pagination
import com.finngraph.web.common.ResourceNotFoundException
import com.finngraph.web.common.validatePaging
import com.finngraph.composition.NewsStockComposer
import org.springframework.web.bind.annotation.RestController

@RestController
class NewsController(
    private val newsQuery: NewsQueryPort,
    private val newsStockComposer: NewsStockComposer,
) : NewsApi {

    override fun list(page: Int, size: Int): PageResponse<NewsResponse> {
        validatePaging(page, size)
        return newsQuery.findPage(page, size).toResponse()
    }

    override fun detail(id: Long): DataResponse<NewsDetailResponse> {
        val target = toNewsId(id)
        val found = newsQuery.findByIds(listOf(target))[target]
            ?: throw ResourceNotFoundException(ErrorCode.NEWS_NOT_FOUND, "뉴스를 찾을 수 없습니다: $id")
        return DataResponse(NewsDetailResponse.from(found))
    }

    override fun companies(id: Long): DataResponse<List<NewsRelatedStockResponse>> {
        val target = toNewsId(id)
        val related = newsStockComposer.relatedStocks(target)
            ?: throw ResourceNotFoundException(ErrorCode.NEWS_NOT_FOUND, "뉴스를 찾을 수 없습니다: $id")
        return DataResponse(related.map(NewsRelatedStockResponse::from))
    }

    private fun toNewsId(raw: Long): NewsId = try {
        NewsId(raw)
    } catch (e: IllegalArgumentException) {
        throw InvalidParameterException(
            e.message ?: "id가 올바르지 않습니다",
            mapOf("id" to "must be positive"),
        )
    }

    private fun PageResult<NewsView>.toResponse() = PageResponse(
        data = content.map(NewsResponse::from),
        pagination = Pagination(page, size, totalElements, totalPages),
    )
}
