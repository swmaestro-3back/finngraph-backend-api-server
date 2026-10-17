package com.finngraph.web.issue

import com.finngraph.support.IssueMentionSeed
import com.finngraph.support.TestContainers
import com.finngraph.web.common.ErrorCode
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import tools.jackson.databind.ObjectMapper
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ThemeIssueApiTest {

    @Autowired
    lateinit var rest: TestRestTemplate

    @Autowired
    lateinit var mapper: ObjectMapper

    @Test
    fun `테마 이슈는 그날 목록 이슈 중 활성 구성 종목이 나온 이슈를 매체 수 기사 수 마지막 보도 순으로 낸다`() {
        val main = board(IssueMentionSeed.THEME_MAIN).single()

        assertEquals(IssueMentionSeed.THEME_MAIN.toInt(), main["themeId"])
        assertEquals(5, main["issueCount"])
        assertEquals(
            listOf(
                IssueMentionSeed.CROWDED,
                IssueMentionSeed.TWO_MEDIA_MORE_ARTICLES,
                IssueMentionSeed.TWO_MEDIA,
                IssueMentionSeed.SINGLE_LATE,
                IssueMentionSeed.SINGLE_EARLY,
            ),
            main.ids("issueIds"),
        )
        assertEquals(
            listOf(IssueMentionSeed.CROWDED, IssueMentionSeed.TWO_MEDIA_MORE_ARTICLES, IssueMentionSeed.TWO_MEDIA),
            main.issues().map { it.long("id") },
        )
    }

    @Test
    fun `표시용 상위 5개 밖에서 언급돼도 테마 이슈이고 비공개 언급과 다른 날 이슈와 비활성 종목은 뺀다`() {
        val main = board(IssueMentionSeed.THEME_MAIN).single()
        val crowded = main.issues().first()

        assertEquals(IssueMentionSeed.CROWDED, crowded.long("id"))
        assertEquals(IssueMentionSeed.CROWD, (crowded["companies"] as List<*>).map { (it as Map<*, *>)["ticker"] })
        assertTrue(IssueMentionSeed.HIDDEN_THEME_MENTION !in main.ids("issueIds"))
        assertTrue(IssueMentionSeed.UNEXTRACTED_THEME !in main.ids("issueIds"))
        assertTrue(IssueMentionSeed.PREV_DAY_ONLY !in main.ids("issueIds"))
        assertTrue(IssueMentionSeed.INACTIVE_THEME !in main.ids("issueIds"))
    }

    @Test
    fun `항목은 그날 이슈 목록 항목과 같다`() {
        val listItems = fetch("/api/v1/issues?date=${IssueMentionSeed.THEME_DAY}").items().associateBy { it.long("id") }
        val themeItems = board(IssueMentionSeed.THEME_MAIN).single().issues()

        themeItems.forEach { assertEquals(listItems.getValue(it.long("id")), it) }
    }

    @Test
    fun `요청 순서대로 중복 없이 내고 없는 테마와 비활성 종목만 있는 테마는 빈 항목이다`() {
        val payload = fetch(
            "/api/v1/themes/issues?date=${IssueMentionSeed.THEME_DAY}&ids=" +
                listOf(
                    IssueMentionSeed.THEME_UNKNOWN,
                    IssueMentionSeed.THEME_SECOND,
                    IssueMentionSeed.THEME_INACTIVE,
                    IssueMentionSeed.THEME_SECOND,
                ).joinToString(","),
        )
        val data = payload.items()

        assertEquals(mapOf("date" to IssueMentionSeed.THEME_DAY), payload["meta"])
        assertEquals(
            listOf(IssueMentionSeed.THEME_UNKNOWN, IssueMentionSeed.THEME_SECOND, IssueMentionSeed.THEME_INACTIVE),
            data.map { it.long("themeId") },
        )
        assertEquals(empty(IssueMentionSeed.THEME_UNKNOWN), data[0])
        assertEquals(listOf(IssueMentionSeed.SECOND_THEME), data[1].ids("issueIds"))
        assertEquals(1, data[1]["issueCount"])
        assertEquals(empty(IssueMentionSeed.THEME_INACTIVE), data[2])
    }

    @Test
    fun `date 를 빼면 이슈 목록 기본 날짜를 쓴다`() {
        val payload = fetch("/api/v1/themes/issues?ids=${IssueMentionSeed.THEME_TODAY},${IssueMentionSeed.THEME_MAIN}")
        val listMeta = fetch("/api/v1/issues")["meta"] as Map<*, *>

        assertEquals(IssueMentionSeed.TODAY.toString(), (payload["meta"] as Map<*, *>)["date"])
        assertEquals(listMeta["date"], (payload["meta"] as Map<*, *>)["date"])
        val data = payload.items()
        assertEquals(listOf(IssueMentionSeed.RECENT), data[0].ids("issueIds"))
        assertEquals(empty(IssueMentionSeed.THEME_MAIN), data[1])
    }

    @Test
    fun `ids 가 없거나 양의 정수가 아니거나 50개를 넘거나 date 가 틀리면 400`() {
        mapOf(
            "" to "ids",
            "?ids=" to "ids",
            "?ids=abc" to "ids",
            "?ids=1,0" to "ids",
            "?ids=-3" to "ids",
            "?ids=1.5" to "ids",
            "?ids=${(1..51).joinToString(",")}" to "ids",
            "?ids=1&date=2020-02-30" to "date",
            "?ids=1&date=20200203" to "date",
        ).forEach { (query, field) ->
            val response = rest.getForEntity("/api/v1/themes/issues$query", String::class.java)
            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode, query)
            assertTrue(response.body!!.contains(ErrorCode.INVALID_PARAMETER), query)
            assertTrue(response.body!!.contains("\"$field\""), query)
        }
        val fifty = (1..50).joinToString(",")
        assertEquals(50, fetch("/api/v1/themes/issues?ids=$fifty,1,2").items().size)
    }

    private fun board(vararg ids: Long): List<Map<*, *>> =
        fetch("/api/v1/themes/issues?ids=${ids.joinToString(",")}&date=${IssueMentionSeed.THEME_DAY}").items()

    private fun empty(themeId: Long) = mapOf(
        "themeId" to themeId.toInt(),
        "issueCount" to 0,
        "issueIds" to emptyList<Any>(),
        "issues" to emptyList<Any>(),
    )

    private fun fetch(path: String): Map<*, *> {
        val response = rest.getForEntity(path, String::class.java)
        assertEquals(HttpStatus.OK, response.statusCode, path)
        return mapper.readValue(response.body, Map::class.java)
    }

    private fun Map<*, *>.items(): List<Map<*, *>> = (this["data"] as List<*>).map { it as Map<*, *> }

    private fun Map<*, *>.issues(): List<Map<*, *>> = (this["issues"] as List<*>).map { it as Map<*, *> }

    private fun Map<*, *>.ids(key: String): List<Long> = (this[key] as List<*>).map { (it as Number).toLong() }

    private fun Map<*, *>.long(key: String): Long = (this[key] as Number).toLong()

    companion object {
        @JvmStatic
        @BeforeAll
        fun seed() = IssueMentionSeed.seed()

        @JvmStatic
        @AfterAll
        fun cleanup() = IssueMentionSeed.cleanup()

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) = TestContainers.register(registry)
    }
}
