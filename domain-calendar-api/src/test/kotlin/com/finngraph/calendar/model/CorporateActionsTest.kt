package com.finngraph.calendar.model

import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class CorporateActionsTest {

    private fun event(
        date: String,
        kind: EventKind,
        basis: String,
        label: String? = null,
        amount: String? = null,
        agenda: List<String> = emptyList(),
    ) = CalendarEvent(
        date = LocalDate.parse(date),
        kind = kind,
        ticker = "961101",
        stockName = "예탁원배당",
        basisDate = LocalDate.parse(basis),
        endDate = null,
        amount = amount?.let(::BigDecimal),
        ratio = null,
        label = label,
        agenda = agenda,
        agendaTruncated = false,
        estimated = false,
    )

    @Test
    fun `같은 기준일의 배당 일정을 한 묶음으로 날짜순 정렬한다`() {
        val actions = CorporateActions.group(
            listOf(
                event("2027-04-20", EventKind.DIV_PAY, "2027-03-26", "분기", "100"),
                event("2027-03-26", EventKind.DIV_RECORD, "2027-03-26", "분기", "100"),
                event("2027-03-25", EventKind.DIV_EX, "2027-03-26", "분기", "100"),
            ),
        )

        val action = actions.single()
        assertEquals(EventFamily.DIV, action.family)
        assertEquals(listOf(EventKind.DIV_EX, EventKind.DIV_RECORD, EventKind.DIV_PAY), action.steps.map { it.kind })
        assertEquals(BigDecimal("100"), action.amount)
        assertEquals(LocalDate.parse("2027-03-25"), action.dateOf(EventKind.DIV_EX))
    }

    @Test
    fun `기준일이 같아도 라벨이 다르면 다른 묶음이다`() {
        val actions = CorporateActions.group(
            listOf(
                event("2027-03-25", EventKind.DIV_EX, "2027-03-26", "분기"),
                event("2027-03-25", EventKind.DIV_EX, "2027-03-26", "결산"),
            ),
        )

        assertEquals(setOf("분기", "결산"), actions.map { it.label }.toSet())
    }

    @Test
    fun `묶음은 첫 단계 날짜순이고 주총 안건에 태그가 붙는다`() {
        val actions = CorporateActions.group(
            listOf(
                event("2027-03-25", EventKind.DIV_EX, "2027-03-26", "분기"),
                event("2027-03-10", EventKind.AGM, "2027-02-15", "임시총회", agenda = listOf("합병승인", "사내이사 선임")),
            ),
        )

        assertEquals(listOf(EventFamily.AGM, EventFamily.DIV), actions.map { it.family })
        assertEquals(
            listOf(AgendaItem("합병승인", listOf("합병")), AgendaItem("사내이사 선임", emptyList())),
            actions.first().agenda,
        )
    }
}
