package com.finngraph.calendar.model

import kotlin.test.Test
import kotlin.test.assertEquals

class AgendaTagsTest {

    @Test
    fun `주목 안건에 태그를 붙인다`() {
        assertEquals(listOf("합병"), AgendaTags.of("합병승인"))
        assertEquals(listOf("분할"), AgendaTags.of("분할계획서 승인의 건"))
        assertEquals(listOf("액면분할"), AgendaTags.of("주식분할(액면분할) 승인의 건"))
        assertEquals(listOf("감자"), AgendaTags.of("자본감소승인"))
        assertEquals(listOf("스톡옵션"), AgendaTags.of("주식매수선택권부여 승인"))
        assertEquals(listOf("임원 해임"), AgendaTags.of("사내이사해임"))
        assertEquals(listOf("영업양수도"), AgendaTags.of("영업양수"))
        assertEquals(listOf("액면변경"), AgendaTags.of("액면변경"))
    }

    @Test
    fun `주식병합은 합병으로 잡지 않는다`() {
        assertEquals(listOf("주식병합"), AgendaTags.of("주식병합 승인의 건"))
        assertEquals(listOf("주식병합"), AgendaTags.of("주식(액면) 병합 승인의 건"))
    }

    @Test
    fun `결손 보전 목적의 준비금 감액은 결손 보전만 붙인다`() {
        assertEquals(listOf("결손 보전"), AgendaTags.of("자본준비금 감액 및 결손금 보전의 건"))
        assertEquals(listOf("자본준비금 감액"), AgendaTags.of("자본준비금의 이익잉여금 전입의 건"))
        assertEquals(listOf("자본준비금 감액"), AgendaTags.of("자본준비금 감소 및 이익잉여금 전입의 건"))
    }

    @Test
    fun `한 안건에 태그가 여럿 붙을 수 있다`() {
        assertEquals(listOf("자사주", "주주제안"), AgendaTags.of("자기주식 보유ㆍ처분 계획 승인의 건(주주제안)"))
    }

    @Test
    fun `정례 안건은 태그가 없다`() {
        listOf("정관변경", "사내이사 선임", "감사선임", "이사 보수한도액 승인", "본점 소재지 변경의 건", "안건 미정").forEach {
            assertEquals(emptyList(), AgendaTags.of(it), it)
        }
    }
}
