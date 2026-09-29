package com.finngraph.support

import com.finngraph.composition.briefing.HeadlineDraft
import com.finngraph.composition.briefing.SentenceDraft
import com.finngraph.composition.briefing.WatchDraft
import com.finngraph.composition.port.BriefingWriterPort
import com.finngraph.composition.port.CommentaryInput
import com.finngraph.composition.port.HeadlineInput
import com.finngraph.composition.port.WatchInput
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

class FakeBriefingWriter : BriefingWriterPort {

    val headlineInputs = mutableListOf<HeadlineInput>()
    val commentaryInputs = mutableListOf<CommentaryInput>()
    val watchInputs = mutableListOf<WatchInput>()

    override var enabled: Boolean = true

    override fun headline(input: HeadlineInput): HeadlineDraft {
        headlineInputs += input
        val cluster = input.issues.firstOrNull()?.let { listOf("CLUSTER:${it.clusterId}") } ?: emptyList()
        return HeadlineDraft("상승 ${input.market.upCount}·하락 ${input.market.downCount} 종목으로 마감했습니다.", cluster)
    }

    override fun commentary(input: CommentaryInput): List<SentenceDraft> {
        commentaryInputs += input
        val news = "NEWS:${input.articles.first().newsId}"
        val relation = input.relations.firstOrNull()?.let { "RELATION:${it.id}" }
        return listOf(
            SentenceDraft("${input.title} 관련 보도가 ${input.articles.size}건 이어졌습니다.", listOf(news)),
            SentenceDraft("추출된 관계가 확인되었습니다.", listOfNotNull(relation ?: news)),
            SentenceDraft("급등이 예상됩니다.", listOf(news)),
            SentenceDraft("근거 없는 문장입니다.", listOf("NEWS:1")),
        )
    }

    override fun watchPoints(input: WatchInput): List<WatchDraft> {
        watchInputs += input
        return input.candidates.mapIndexed { index, candidate -> WatchDraft(index, "${candidate.factText} 확인", emptyList()) }
    }
}

@TestConfiguration
class FakeBriefingWriterConfig {

    @Bean
    @Primary
    fun fakeBriefingWriter(): FakeBriefingWriter = FakeBriefingWriter()
}
