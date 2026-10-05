package com.finngraph.web.issue

import com.finngraph.composition.issue.IssueComposer
import com.finngraph.composition.issue.IssueSort
import com.finngraph.web.common.DataResponse
import com.finngraph.web.common.ErrorCode
import com.finngraph.web.common.InvalidParameterException
import com.finngraph.web.common.ResourceNotFoundException
import com.finngraph.web.common.validatePaging
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.format.DateTimeParseException

@RestController
class IssueController(
    private val composer: IssueComposer,
) : IssueApi {

    override fun list(date: String?, sort: String, page: Int, size: Int): IssuePageResponse {
        val day = parseDate(date)
        val order = SORT_KEYS[sort]
        val errors = buildMap {
            if (!date.isNullOrBlank() && day == null) put("date", "must be YYYY-MM-DD")
            if (order == null) put("sort", "must be one of ${SORT_KEYS.keys.joinToString(", ")}")
        }
        if (errors.isNotEmpty()) {
            throw InvalidParameterException("이슈 목록 파라미터가 올바르지 않습니다", errors)
        }
        validatePaging(page, size)
        return IssuePageResponse.from(composer.page(day, checkNotNull(order), page, size))
    }

    override fun detail(id: Long): DataResponse<IssueDetailResponse> {
        if (id <= 0) {
            throw InvalidParameterException("id는 양수여야 합니다: $id", mapOf("id" to "must be positive"))
        }
        val found = composer.detail(id)
            ?: throw ResourceNotFoundException(ErrorCode.ISSUE_NOT_FOUND, "이슈를 찾을 수 없습니다: $id")
        return DataResponse(IssueDetailResponse.from(found))
    }

    private fun parseDate(raw: String?): LocalDate? {
        if (raw.isNullOrBlank()) return null
        return try {
            LocalDate.parse(raw)
        } catch (e: DateTimeParseException) {
            null
        }
    }

    private companion object {
        val SORT_KEYS = mapOf(
            "media" to IssueSort.MEDIA,
            "recent" to IssueSort.RECENT,
        )
    }
}
