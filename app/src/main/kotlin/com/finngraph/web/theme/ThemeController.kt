package com.finngraph.web.theme

import com.finngraph.composition.ThemeNewsComposer
import com.finngraph.news.model.NewsView
import com.finngraph.news.model.PageResult
import com.finngraph.theme.model.ThemeId
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
    private val themeNewsComposer: ThemeNewsComposer,
) : ThemeApi {

    override fun list(): DataResponse<List<ThemeSummaryResponse>> =
        DataResponse(themeQuery.findAll().map(ThemeSummaryResponse::from))

    override fun detail(id: Long): DataResponse<ThemeSummaryResponse> {
        val target = toThemeId(id)
        val found = themeQuery.findById(target) ?: throw notFound(id)
        return DataResponse(ThemeSummaryResponse.from(found))
    }

    override fun stocks(id: Long): DataResponse<List<ThemeStockResponse>> {
        val target = toThemeId(id)
        requireTheme(target, id)
        return DataResponse(themeStock.findStocks(target).map(ThemeStockResponse::from))
    }

    override fun news(id: Long, page: Int, size: Int): PageResponse<NewsResponse> {
        val target = toThemeId(id)
        validatePaging(page, size)
        requireTheme(target, id)
        return themeNewsComposer.newsPage(target, page, size).toResponse()
    }

    private fun toThemeId(raw: Long): ThemeId = try {
        ThemeId(raw)
    } catch (e: IllegalArgumentException) {
        throw InvalidParameterException(
            e.message ?: "id가 올바르지 않습니다",
            mapOf("id" to "must be positive"),
        )
    }

    private fun requireTheme(target: ThemeId, raw: Long) {
        if (!themeQuery.exists(target)) throw notFound(raw)
    }

    private fun notFound(raw: Long) =
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

    private fun PageResult<NewsView>.toResponse() = PageResponse(
        data = content.map(NewsResponse::from),
        pagination = Pagination(page, size, totalElements, totalPages),
    )
}
