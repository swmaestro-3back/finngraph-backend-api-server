package com.finngraph.web.calendar

import com.finngraph.composition.calendar.CalendarComposer
import com.finngraph.composition.calendar.StockCalendarComposer
import com.finngraph.stock.model.Ticker
import com.finngraph.web.common.DataResponse
import com.finngraph.web.common.ErrorCode
import com.finngraph.web.common.InvalidParameterException
import com.finngraph.web.common.ResourceNotFoundException
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

@RestController
class CalendarController(
    private val composer: CalendarComposer,
    private val stockComposer: StockCalendarComposer,
) : CalendarApi {

    override fun calendar(from: String?, to: String?): DataResponse<CalendarResponse> {
        val (start, end) = range(from, to, MAX_RANGE_DAYS)
        return DataResponse(CalendarResponse.from(composer.publicCalendar(start, end)))
    }

    override fun myCalendar(userId: Long, from: String?, to: String?): DataResponse<CalendarResponse> {
        val (start, end) = range(from, to, MAX_RANGE_DAYS)
        return DataResponse(CalendarResponse.from(composer.memberCalendar(userId, start, end)))
    }

    override fun stockCalendar(ticker: String, from: String?, to: String?): DataResponse<StockCalendarResponse> {
        val target = toTicker(ticker)
        val (start, end) = range(from, to, MAX_STOCK_RANGE_DAYS)
        val view = stockComposer.forStock(target, start, end)
            ?: throw ResourceNotFoundException(ErrorCode.STOCK_NOT_FOUND, "종목을 찾을 수 없습니다: $ticker")
        return DataResponse(StockCalendarResponse.from(view))
    }

    override fun ipos(): DataResponse<IpoListResponse> = DataResponse(IpoListResponse.from(composer.ipoBoard()))

    private fun toTicker(raw: String): Ticker {
        val errors = buildMap {
            if (raw.isBlank()) put("ticker", "must not be blank")
            else if (raw.length > MAX_TICKER_LENGTH) put("ticker", "must be <= $MAX_TICKER_LENGTH characters")
        }
        if (errors.isNotEmpty()) {
            throw InvalidParameterException("ticker가 올바르지 않습니다", errors)
        }
        return Ticker(raw)
    }

    private fun range(from: String?, to: String?, maxDays: Long): Pair<LocalDate, LocalDate> {
        val start = parse("from", from)
        val end = parse("to", to)
        if (end < start) {
            throw InvalidParameterException("to는 from과 같거나 뒤여야 합니다", mapOf("to" to "must not be before from"))
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > maxDays) {
            throw InvalidParameterException(
                "기간은 최대 ${maxDays}일입니다",
                mapOf("to" to "range must be at most $maxDays days"),
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
        const val MAX_STOCK_RANGE_DAYS = 366L
        const val MAX_TICKER_LENGTH = 20
    }
}
