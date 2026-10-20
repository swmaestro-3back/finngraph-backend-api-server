package com.finngraph.web.favorite

import com.finngraph.composition.favorite.FavoriteComposer
import com.finngraph.composition.favorite.FavoriteFeedComposer
import com.finngraph.favorite.model.AddFavoriteResult
import com.finngraph.news.model.NewsView
import com.finngraph.news.model.PageResult
import com.finngraph.web.common.AuthenticationFailedException
import com.finngraph.web.common.DataResponse
import com.finngraph.web.common.ErrorCode
import com.finngraph.web.common.InvalidParameterException
import com.finngraph.web.common.PageResponse
import com.finngraph.web.common.Pagination
import com.finngraph.web.common.validatePaging
import com.finngraph.web.news.NewsResponse
import org.springframework.web.bind.annotation.RestController

@RestController
class FavoriteController(
    private val favoriteComposer: FavoriteComposer,
    private val feedComposer: FavoriteFeedComposer,
) : FavoriteApi {

    override fun list(userId: Long): DataResponse<FavoriteListResponse> =
        DataResponse(FavoriteListResponse.from(feedComposer.list(userId)))

    override fun add(userId: Long, type: String, key: String): DataResponse<FavoriteResponse> {
        val target = parseFavoriteTarget(type, key)
        val result = favoriteComposer.add(userId, target)
            ?: throw AuthenticationFailedException(ErrorCode.UNAUTHORIZED, "인증이 필요합니다")
        return when (result) {
            is AddFavoriteResult.Added -> DataResponse(FavoriteResponse.from(result.view))
            is AddFavoriteResult.AlreadyExists -> DataResponse(FavoriteResponse.from(result.view))
            AddFavoriteResult.LimitExceeded -> {
                val current = feedComposer.list(userId)
                throw FavoriteLimitExceededException(current.limit, current.count)
            }
        }
    }

    override fun remove(userId: Long, type: String, key: String) {
        favoriteComposer.remove(userId, parseFavoriteTarget(type, key))
    }

    override fun returns(userId: Long, period: String): DataResponse<FavoriteReturnsResponse> {
        val parsed = parsePeriod(period) ?: throw InvalidParameterException(
            "period가 올바르지 않습니다",
            mapOf("period" to "must be one of 1W, 1M, 3M"),
        )
        return DataResponse(FavoriteReturnsResponse.from(feedComposer.returns(userId, parsed)))
    }

    override fun news(userId: Long, page: Int, size: Int): PageResponse<NewsResponse> {
        validatePaging(page, size)
        return feedComposer.newsPage(userId, page, size).toResponse()
    }

    private fun PageResult<NewsView>.toResponse() = PageResponse(
        data = content.map(NewsResponse::from),
        pagination = Pagination(page, size, totalElements, totalPages),
    )
}
