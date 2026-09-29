package com.finngraph.web.internal

import com.finngraph.dailybriefing.BriefingGenerationOutcome
import com.finngraph.dailybriefing.BriefingGenerationService
import com.finngraph.web.common.DataResponse
import com.finngraph.web.common.InvalidParameterException
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.format.DateTimeParseException

data class BriefingGenerateResponse(
    val result: String,
    val baseDate: LocalDate?,
    val status: String?,
    val reason: String?,
    val issueCount: Int?,
    val droppedSentences: Int?,
    val durationMs: Long?,
)

@RestController
class InternalBriefingController(
    private val generationService: BriefingGenerationService,
) {

    @PostMapping("/internal/briefings/generate")
    fun generate(
        @RequestParam(required = false) date: String?,
        @RequestParam(required = false, defaultValue = "false") force: Boolean,
    ): DataResponse<BriefingGenerateResponse> {
        val parsed = date?.let(::parseDate)
        return when (val outcome = generationService.generate(parsed, force)) {
            is BriefingGenerationOutcome.Generated -> DataResponse(
                BriefingGenerateResponse(
                    result = "GENERATED",
                    baseDate = outcome.baseDate,
                    status = outcome.status.name,
                    reason = null,
                    issueCount = outcome.issueCount,
                    droppedSentences = outcome.droppedSentences,
                    durationMs = outcome.durationMs,
                ),
            )
            is BriefingGenerationOutcome.Skipped -> DataResponse(
                BriefingGenerateResponse(
                    result = "SKIPPED",
                    baseDate = outcome.baseDate,
                    status = null,
                    reason = outcome.reason,
                    issueCount = null,
                    droppedSentences = null,
                    durationMs = null,
                ),
            )
        }
    }

    private fun parseDate(raw: String): LocalDate = try {
        LocalDate.parse(raw)
    } catch (e: DateTimeParseException) {
        throw InvalidParameterException("date 형식이 올바르지 않습니다", mapOf("date" to "must be YYYY-MM-DD"))
    }
}
