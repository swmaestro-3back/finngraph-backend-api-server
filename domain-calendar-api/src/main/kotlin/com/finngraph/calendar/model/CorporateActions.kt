package com.finngraph.calendar.model

import java.math.BigDecimal
import java.time.LocalDate

enum class EventFamily {
    DIV,
    BONUS,
    RIGHTS,
    AGM,
    ;

    companion object {
        fun of(kind: EventKind): EventFamily = when (kind) {
            EventKind.DIV_EX, EventKind.DIV_RECORD, EventKind.DIV_PAY -> DIV
            EventKind.BONUS_EX, EventKind.BONUS_LIST -> BONUS
            EventKind.RIGHTS_EX, EventKind.RIGHTS_SUBSCRIBE, EventKind.RIGHTS_LIST -> RIGHTS
            EventKind.AGM -> AGM
        }
    }
}

data class ActionStep(
    val kind: EventKind,
    val date: LocalDate,
    val endDate: LocalDate?,
    val estimated: Boolean,
)

data class AgendaItem(val text: String, val tags: List<String>)

data class CorporateAction(
    val ticker: String,
    val family: EventFamily,
    val label: String?,
    val basisDate: LocalDate,
    val steps: List<ActionStep>,
    val amount: BigDecimal?,
    val ratio: BigDecimal?,
    val agenda: List<AgendaItem>,
    val agendaTruncated: Boolean,
) {
    fun dateOf(kind: EventKind): LocalDate? = steps.firstOrNull { it.kind == kind }?.date
}

object CorporateActions {

    private data class Key(val ticker: String, val family: EventFamily, val basisDate: LocalDate, val label: String?)

    private val STEP_ORDER: Comparator<CalendarEvent> = compareBy({ it.date }, { it.kind.ordinal })

    private val ACTION_ORDER: Comparator<CorporateAction> =
        compareBy({ it.steps.first().date }, { it.family.ordinal }, { it.basisDate })

    fun group(events: List<CalendarEvent>): List<CorporateAction> =
        events.groupBy { Key(it.ticker, EventFamily.of(it.kind), it.basisDate, it.label) }
            .map { (key, members) -> toAction(key, members.sortedWith(STEP_ORDER)) }
            .sortedWith(ACTION_ORDER)

    private fun toAction(key: Key, members: List<CalendarEvent>) = CorporateAction(
        ticker = key.ticker,
        family = key.family,
        label = key.label,
        basisDate = key.basisDate,
        steps = members.map { ActionStep(it.kind, it.date, it.endDate, it.estimated) },
        amount = members.firstNotNullOfOrNull { it.amount },
        ratio = members.firstNotNullOfOrNull { it.ratio },
        agenda = members.firstOrNull { it.agenda.isNotEmpty() }?.agenda.orEmpty()
            .map { AgendaItem(it, AgendaTags.of(it)) },
        agendaTruncated = members.any { it.agendaTruncated },
    )
}
