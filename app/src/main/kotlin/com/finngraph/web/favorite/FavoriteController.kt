package com.finngraph.web.favorite

import com.finngraph.composition.FavoriteComposer
import com.finngraph.composition.FavoriteFeedComposer
import com.finngraph.favorite.model.AddFavoriteResult
import com.finngraph.favorite.model.FavoriteTarget
import com.finngraph.favorite.model.FavoriteType
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
        val target = parseTarget(type, key)
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
        favoriteComposer.remove(userId, parseTarget(type, key))
    }

    override fun news(userId: Long, page: Int, size: Int): PageResponse<NewsResponse> {
        validatePaging(page, size)
        return feedComposer.newsPage(userId, page, size).toResponse()
    }

    private fun parseTarget(type: String, key: String): FavoriteTarget {
        if (FavoriteType.entries.none { it.name == type }) {
            throw InvalidParameterException(
                "type은 STOCK 또는 THEME여야 합니다",
                mapOf("type" to "must be one of STOCK, THEME"),
            )
        }
        return runCatching { FavoriteTarget.of(type, key) }.getOrElse {
            val rule =
                if (type == FavoriteType.STOCK.name) "must be 1-20 alphanumeric characters"
                else "must be a positive integer"
            throw InvalidParameterException(it.message ?: "key가 올바르지 않습니다", mapOf("key" to rule))
        }
    }

    private fun PageResult<NewsView>.toResponse() = PageResponse(
        data = content.map(NewsResponse::from),
        pagination = Pagination(page, size, totalElements, totalPages),
    )
}
