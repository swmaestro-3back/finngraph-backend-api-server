package com.finngraph.calendar.adapter

import com.finngraph.calendar.model.FundUse
import com.finngraph.calendar.model.Putback
import com.finngraph.calendar.model.Seller
import com.finngraph.calendar.model.Underwriter
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IpoFilingJsonTest {

    @Test
    fun `인수인은 숫자와 쉼표 문자열을 모두 읽고 하이픈은 비우며 이름 없는 행은 뺀다`() {
        val json = """
            [{"name": "삼성증권", "role": "대표", "shares": 2030000, "amount": 46690000000, "method": "총액인수"},
             {"name": "테스트증권", "role": "인수", "shares": "530,000", "amount": "-", "method": null},
             {"role": "공동", "shares": 1}]
        """.trimIndent()

        assertEquals(
            listOf(
                Underwriter("삼성증권", "대표", 2030000, BigDecimal("46690000000"), "총액인수"),
                Underwriter("테스트증권", "인수", 530000, null, null),
            ),
            IpoFilingJson.underwriters(json),
        )
    }

    @Test
    fun `자금 용도는 목적이나 금액이 없는 행을 뺀다`() {
        val json = """[{"purpose": "시설자금", "amount": "31,320,000,000"}, {"purpose": "운영자금", "amount": "-"}, {"amount": 100}]"""

        assertEquals(listOf(FundUse("시설자금", BigDecimal("31320000000"))), IpoFilingJson.fundUses(json))
    }

    @Test
    fun `매출인과 환매청구권을 읽는다`() {
        assertEquals(
            listOf(Seller("최대주주", "최대주주", 1000000, 300000, 700000)),
            IpoFilingJson.sellers(
                """[{"holder": "최대주주", "relation": "최대주주", "before": 1000000, "sold": 300000, "after": 700000}]""",
            ),
        )
        assertEquals(
            Putback("공모가 하락", "일반청약자", "2030000", "상장일부터 1개월", "공모가격의 90%"),
            IpoFilingJson.putback(
                """{"reason": "공모가 하락", "investors": "일반청약자", "shares": 2030000, "period": "상장일부터 1개월", "price": "공모가격의 90%"}""",
            ),
        )
    }

    @Test
    fun `값이 배열이나 객체면 그 칸만 비운다`() {
        assertEquals(
            listOf(Underwriter("삼성증권", null, null, BigDecimal("100"), "총액인수")),
            IpoFilingJson.underwriters(
                """[{"name": "삼성증권", "role": ["대표"], "shares": {"n": 1}, "amount": 100, "method": "총액인수"}, {"name": ["x"]}]""",
            ),
        )
        assertEquals(
            Putback(null, "일반청약자", null, null, null),
            IpoFilingJson.putback("""{"reason": {"a": 1}, "investors": "일반청약자"}"""),
        )
    }

    @Test
    fun `값이 없으면 빈 목록이나 null이다`() {
        assertEquals(emptyList(), IpoFilingJson.underwriters(null))
        assertEquals(emptyList(), IpoFilingJson.fundUses("[]"))
        assertEquals(emptyList(), IpoFilingJson.sellers("{}"))
        assertNull(IpoFilingJson.putback(null))
        assertNull(IpoFilingJson.putback("null"))
    }
}
