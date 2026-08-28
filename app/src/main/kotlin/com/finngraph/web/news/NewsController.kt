package com.finngraph.web.news

import com.finngraph.news.model.NewsId
import com.finngraph.news.model.NewsView
import com.finngraph.news.model.PageResult
import com.finngraph.news.port.NewsCompanyPort
import com.finngraph.news.port.NewsQueryPort
import com.finngraph.web.common.DataResponse
import com.finngraph.web.common.ErrorCode
import com.finngraph.web.common.InvalidParameterException
import com.finngraph.web.common.PageResponse
import com.finngraph.web.common.Pagination
import com.finngraph.web.common.ResourceNotFoundException
import org.springframework.web.bind.annotation.RestController

@RestController
class NewsController(
    private val newsQuery: NewsQueryPort,
    private val newsCompany: NewsCompanyPort,
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

    override fun companies(id: Long): DataResponse<List<CompanyResponse>> {
        val target = toNewsId(id)
        val refs = newsCompany.findByNewsIds(listOf(target))[target] ?: emptyList()
        return DataResponse(refs.map(CompanyResponse::from))
    }

    private fun toNewsId(raw: Long): NewsId = try {
        NewsId(raw)
    } catch (e: IllegalArgumentException) {
        throw InvalidParameterException(
            e.message ?: "id가 올바르지 않습니다",
            mapOf("id" to "must be positive"),
        )
    }

    private fun validatePaging(page: Int, size: Int) {
        val errors = buildMap {
            if (page < 0) put("page", "must be >= 0")
            if (size < 1) put("size", "must be >= 1")
            if (size > PageResult.MAX_SIZE) put("size", "must be <= ${PageResult.MAX_SIZE}")
        }
        if (errors.isEmpty()) return

        val message =
            if (size > PageResult.MAX_SIZE) "size는 ${PageResult.MAX_SIZE} 이하여야 합니다"
            else "페이징 파라미터가 올바르지 않습니다"
        throw InvalidParameterException(message, errors)
    }

    private fun PageResult<NewsView>.toResponse() = PageResponse(
        data = content.map(NewsResponse::from),
        pagination = Pagination(page, size, totalElements, totalPages),
    )
}
