package com.finngraph.web

import com.finngraph.news.NewsCompanyPort
import com.finngraph.news.NewsId
import com.finngraph.news.NewsQueryPort
import com.finngraph.news.NewsView
import com.finngraph.news.PageResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/news")
class NewsController(
    private val newsQuery: NewsQueryPort,
    private val newsCompany: NewsCompanyPort,
) {
    @GetMapping
    fun list(
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "20") size: Int,
        @RequestParam(required = false) impl: String?,
    ): PageResponse<NewsResponse> {
        validatePaging(page, size)
        return newsQuery.findPage(page, size).toResponse()
    }

    @GetMapping("/{id}")
    fun detail(@PathVariable id: Long): DataResponse<NewsDetailResponse> {
        val target = toNewsId(id)
        val found = newsQuery.findByIds(listOf(target))[target]
            ?: throw NewsNotFoundException(id)
        return DataResponse(NewsDetailResponse.from(found))
    }

    @GetMapping("/{id}/companies")
    fun companies(
        @PathVariable id: Long,
        @RequestParam(required = false) impl: String?,
    ): DataResponse<List<CompanyResponse>> {
        val target = toNewsId(id)
        val refs = newsCompany.findByNewsIds(listOf(target))[target] ?: emptyList()
        return DataResponse(refs.map(CompanyResponse::from))
    }

    @GetMapping("/companies")
    fun companiesBulk(
        @RequestParam ids: List<Long?>,
        @RequestParam(required = false) impl: String?,
    ): DataResponse<Map<String, List<CompanyResponse>>> {
        if (ids.isEmpty()) {
            throw InvalidParameterException(
                "ids는 비어 있을 수 없습니다",
                mapOf("ids" to "must not be empty"),
            )
        }

        val parsed = ids.filterNotNull()
        if (parsed.size != ids.size) {
            throw InvalidParameterException(
                "ids에 빈 값이 있습니다",
                mapOf("ids" to "must not contain empty items"),
            )
        }

        if (parsed.size > PageResult.MAX_SIZE) {
            throw InvalidParameterException(
                "ids는 ${PageResult.MAX_SIZE}개 이하여야 합니다",
                mapOf("ids" to "must contain <= ${PageResult.MAX_SIZE} items"),
            )
        }

        val result = newsCompany.findByNewsIds(parsed.map { toNewsId(it) })
        return DataResponse(
            result.entries.associate { entry ->
                entry.key.value.toString() to entry.value.map(CompanyResponse::from)
            },
        )
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
