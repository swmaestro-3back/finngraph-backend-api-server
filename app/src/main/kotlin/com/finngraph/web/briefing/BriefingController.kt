package com.finngraph.web.briefing

import com.finngraph.briefing.port.BriefingStorePort
import com.finngraph.web.common.DataResponse
import com.finngraph.web.common.ErrorCode
import com.finngraph.web.common.InvalidParameterException
import com.finngraph.web.common.ResourceNotFoundException
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.format.DateTimeParseException

@RestController
class BriefingController(
    private val store: BriefingStorePort,
) : BriefingApi {

    override fun list(limit: Int): DataResponse<List<BriefingSummaryResponse>> {
        if (limit !in 1..MAX_LIMIT) {
            throw InvalidParameterException("브리핑 목록 파라미터가 올바르지 않습니다", mapOf("limit" to "must be between 1 and $MAX_LIMIT"))
        }
        return DataResponse(store.listSummaries(limit).map(BriefingSummaryResponse::from))
    }

    override fun latest(userId: Long?): DataResponse<BriefingResponse> {
        val briefing = store.findLatest()
            ?: throw ResourceNotFoundException(ErrorCode.BRIEFING_NOT_FOUND, "생성된 브리핑이 없습니다")
        return DataResponse(BriefingResponse.from(briefing, member = userId != null))
    }

    override fun byDate(date: String, userId: Long?): DataResponse<BriefingResponse> {
        val parsed = try {
            LocalDate.parse(date)
        } catch (e: DateTimeParseException) {
            throw InvalidParameterException("date 형식이 올바르지 않습니다", mapOf("date" to "must be YYYY-MM-DD"))
        }
        val briefing = store.findByDate(parsed)
            ?: throw ResourceNotFoundException(ErrorCode.BRIEFING_NOT_FOUND, "브리핑을 찾을 수 없습니다: $date")
        return DataResponse(BriefingResponse.from(briefing, member = userId != null))
    }

    private companion object {
        const val MAX_LIMIT = 90
    }
}
