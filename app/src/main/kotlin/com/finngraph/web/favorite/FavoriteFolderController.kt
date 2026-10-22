package com.finngraph.web.favorite

import com.finngraph.composition.favorite.FavoriteFolderComposer
import com.finngraph.composition.favorite.FavoriteFolderNotFoundException
import com.finngraph.composition.favorite.FavoriteFolderWriter
import com.finngraph.composition.favorite.FavoriteFeedComposer
import com.finngraph.composition.favorite.FolderWrite
import com.finngraph.favorite.model.FolderName
import com.finngraph.favorite.port.FavoriteFolderPort
import com.finngraph.web.common.AuthenticationFailedException
import com.finngraph.web.common.DataResponse
import com.finngraph.web.common.ErrorCode
import com.finngraph.web.common.InvalidParameterException
import org.springframework.web.bind.annotation.RestController

@RestController
class FavoriteFolderController(
    private val writer: FavoriteFolderWriter,
    private val composer: FavoriteFolderComposer,
    private val feed: FavoriteFeedComposer,
) : FavoriteFolderApi {

    override fun create(userId: Long, request: FolderNameRequest): DataResponse<FavoriteFolderResponse> {
        val name = validateName(request.name)
        return DataResponse(FavoriteFolderResponse.from(done(writer.create(userId, name), userId, null, name)))
    }

    override fun rename(userId: Long, id: Long, request: FolderNameRequest): DataResponse<FavoriteFolderResponse> {
        val name = validateName(request.name)
        return DataResponse(FavoriteFolderResponse.from(done(writer.rename(userId, id, name), userId, id, name)))
    }

    override fun delete(userId: Long, id: Long) {
        writer.delete(userId, id)
    }

    override fun reorder(userId: Long, request: FolderOrderRequest) {
        val ids = request.ids ?: throw invalidOrder()
        when (writer.reorder(userId, ids)) {
            null -> throw unauthorized()
            false -> throw invalidOrder()
            true -> Unit
        }
    }

    override fun addItem(userId: Long, id: Long, type: String, key: String) {
        done(composer.addItem(userId, id, parseFolderTarget(type, key)), userId, id, null)
    }

    override fun removeItem(userId: Long, id: Long, type: String, key: String) {
        done(writer.removeItem(userId, id, parseFolderTarget(type, key)), userId, id, null)
    }

    override fun suggest(userId: Long, type: String, key: String): DataResponse<FolderSuggestResponse> =
        DataResponse(FolderSuggestResponse.from(composer.suggest(userId, parseFolderTarget(type, key))))

    override fun returns(userId: Long, id: Long, period: String): DataResponse<FolderReturnsResponse> {
        val parsed = parsePeriod(period) ?: throw InvalidParameterException(
            "period가 올바르지 않습니다",
            mapOf("period" to "must be one of 1W, 1M, 3M"),
        )
        return DataResponse(FolderReturnsResponse.from(composer.returns(userId, id, parsed)))
    }

    private fun done(result: FolderWrite?, userId: Long, folderId: Long?, name: FolderName?) = when (result) {
        null -> throw unauthorized()
        is FolderWrite.Done -> result.folder
        FolderWrite.NotFound -> throw FavoriteFolderNotFoundException(folderId ?: 0)
        FolderWrite.Duplicate -> throw FavoriteFolderNameDuplicateException(name?.value.orEmpty())
        FolderWrite.LimitExceeded -> throw FavoriteFolderLimitExceededException(FavoriteFolderPort.LIMIT)
        FolderWrite.FavoriteLimitExceeded -> {
            val current = feed.list(userId)
            throw FavoriteLimitExceededException(current.limit, current.count)
        }
    }

    private fun validateName(raw: String?): FolderName =
        runCatching { FolderName.of(raw.orEmpty()) }.getOrElse {
            throw InvalidParameterException(
                "폴더 이름이 올바르지 않습니다",
                mapOf("name" to "must be 1-${FolderName.MAX_LENGTH} characters"),
            )
        }

    private fun invalidOrder() = InvalidParameterException(
        "폴더 순서가 올바르지 않습니다",
        mapOf("ids" to "must list every folder id exactly once"),
    )

    private fun unauthorized() = AuthenticationFailedException(ErrorCode.UNAUTHORIZED, "인증이 필요합니다")
}
