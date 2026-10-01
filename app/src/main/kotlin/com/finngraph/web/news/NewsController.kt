package com.finngraph.web.news

import com.finngraph.composition.NewsStockComposer
import com.finngraph.news.model.IssueTimeline
import com.finngraph.news.model.NewsId
import com.finngraph.news.model.NewsView
import com.finngraph.news.model.PageResult
import com.finngraph.news.port.NewsClusterPort
import com.finngraph.news.port.NewsQueryPort
import com.finngraph.web.common.DataResponse
import com.finngraph.web.common.ErrorCode
import com.finngraph.web.common.InvalidParameterException
import com.finngraph.web.common.PageResponse
import com.finngraph.web.common.Pagination
import com.finngraph.web.common.ResourceNotFoundException
import com.finngraph.web.common.validatePaging
import org.springframework.web.bind.annotation.RestController

@RestController
class NewsController(
    private val newsQuery: NewsQueryPort,
    private val newsClusters: NewsClusterPort,
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

    override fun timeline(clusterId: Long, limit: Int): DataResponse<IssueTimelineResponse> {
        val errors = buildMap {
            if (clusterId <= 0) put("clusterId", "must be positive")
            if (limit !in 1..IssueTimeline.MAX_LIMIT) put("limit", "must be between 1 and ${IssueTimeline.MAX_LIMIT}")
        }
        if (errors.isNotEmpty()) {
            throw InvalidParameterException("타임라인 파라미터가 올바르지 않습니다", errors)
        }
        val timeline = newsClusters.findTimeline(clusterId, limit)
            ?: throw ResourceNotFoundException(ErrorCode.NEWS_CLUSTER_NOT_FOUND, "이슈를 찾을 수 없습니다: $clusterId")
        return DataResponse(IssueTimelineResponse.from(timeline))
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
