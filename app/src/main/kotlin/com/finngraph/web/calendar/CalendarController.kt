package com.finngraph.web.calendar

import com.finngraph.composition.calendar.CalendarComposer
import com.finngraph.web.common.DataResponse
import com.finngraph.web.common.InvalidParameterException
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

@RestController
class CalendarController(private val composer: CalendarComposer) : CalendarApi {

    override fun calendar(from: String?, to: String?): DataResponse<CalendarResponse> {
        val (start, end) = range(from, to)
        return DataResponse(CalendarResponse.from(composer.publicCalendar(start, end)))
    }

    override fun myCalendar(userId: Long, from: String?, to: String?): DataResponse<CalendarResponse> {
        val (start, end) = range(from, to)
        return DataResponse(CalendarResponse.from(composer.memberCalendar(userId, start, end)))
    }

    override fun ipos(): DataResponse<IpoListResponse> = DataResponse(IpoListResponse.from(composer.ipoBoard()))

    private fun range(from: String?, to: String?): Pair<LocalDate, LocalDate> {
        val start = parse("from", from)
        val end = parse("to", to)
        if (end < start) {
            throw InvalidParameterException("to는 from과 같거나 뒤여야 합니다", mapOf("to" to "must not be before from"))
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_RANGE_DAYS) {
            throw InvalidParameterException(
                "기간은 최대 ${MAX_RANGE_DAYS}일입니다",
                mapOf("to" to "range must be at most $MAX_RANGE_DAYS days"),
            )
        }
        return start to end
    }

    private fun parse(name: String, raw: String?): LocalDate {
        if (raw.isNullOrBlank()) throw InvalidParameterException("${name}가 필요합니다", mapOf(name to "required"))
        return try {
            LocalDate.parse(raw)
        } catch (e: DateTimeParseException) {
            throw InvalidParameterException("${name}는 YYYY-MM-DD 형식이어야 합니다", mapOf(name to "must be YYYY-MM-DD"))
        }
    }

    private companion object {
        const val MAX_RANGE_DAYS = 62L
    }
}
