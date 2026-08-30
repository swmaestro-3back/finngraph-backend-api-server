package com.finngraph.web.theme

import com.finngraph.theme.model.NewsRef
import com.finngraph.theme.model.PageResult
import com.finngraph.theme.model.ThemeName
import com.finngraph.theme.port.ThemeNewsPort
import com.finngraph.theme.port.ThemeQueryPort
import com.finngraph.theme.port.ThemeStockPort
import com.finngraph.web.common.DataResponse
import com.finngraph.web.common.ErrorCode
import com.finngraph.web.common.InvalidParameterException
import com.finngraph.web.common.PageResponse
import com.finngraph.web.common.Pagination
import com.finngraph.web.common.ResourceNotFoundException
import com.finngraph.web.news.NewsResponse
import org.springframework.web.bind.annotation.RestController

@RestController
class ThemeController(
    private val themeQuery: ThemeQueryPort,
    private val themeStock: ThemeStockPort,
    private val themeNews: ThemeNewsPort,
) : ThemeApi {

    override fun list(): DataResponse<List<ThemeSummaryResponse>> =
        DataResponse(themeQuery.findAll().map(ThemeSummaryResponse::from))

    override fun detail(name: String): DataResponse<ThemeSummaryResponse> {
        val target = toThemeName(name)
        val found = themeQuery.findByName(target) ?: throw notFound(name)
        return DataResponse(ThemeSummaryResponse.from(found))
    }

    override fun stocks(name: String): DataResponse<List<ThemeStockResponse>> {
        val target = toThemeName(name)
        requireTheme(target, name)
        return DataResponse(themeStock.findStocks(target).map(ThemeStockResponse::from))
    }

    override fun news(name: String, page: Int, size: Int): PageResponse<NewsResponse> {
        val target = toThemeName(name)
        validatePaging(page, size)
        requireTheme(target, name)
        return themeNews.findNews(target, page, size).toResponse()
    }

    private fun toThemeName(raw: String): ThemeName {
        if (raw.isBlank()) {
            throw InvalidParameterException(
                "테마 이름이 올바르지 않습니다",
                mapOf("name" to "must not be blank"),
            )
        }
        return ThemeName(raw)
    }

    private fun requireTheme(target: ThemeName, raw: String) {
        themeQuery.findByName(target) ?: throw notFound(raw)
    }

    private fun notFound(raw: String) =
        ResourceNotFoundException(ErrorCode.THEME_NOT_FOUND, "테마를 찾을 수 없습니다: $raw")

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

    private fun PageResult<NewsRef>.toResponse() = PageResponse(
        data = content.map { it.toNewsResponse() },
        pagination = Pagination(page, size, totalElements, totalPages),
    )

    private fun NewsRef.toNewsResponse() = NewsResponse(
        id = id,
        title = title,
        summary = summary,
        url = url,
        publishedAt = publishedAt,
        collectedAt = collectedAt,
    )
}
