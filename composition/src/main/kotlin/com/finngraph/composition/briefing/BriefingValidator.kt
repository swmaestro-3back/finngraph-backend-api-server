package com.finngraph.composition.briefing

import com.finngraph.briefing.model.BriefingHeadline
import com.finngraph.briefing.model.BriefingIssue
import com.finngraph.briefing.model.BriefingSentence
import com.finngraph.briefing.model.BriefingStatus
import com.finngraph.briefing.model.Citation
import com.finngraph.briefing.model.WatchPoint

object BriefingValidator {

    const val HEADLINE_MAX_CHARS = 90
    const val SENTENCE_MAX_CHARS = 90
    const val WATCH_MAX_CHARS = 80
    const val COMMENTARY_MAX_SENTENCES = 3
    const val WATCH_MAX_POINTS = 4

    val FORBIDDEN: List<String> = listOf(
        "매수", "매도", "추천", "목표가", "전망", "예상", "예측", "급등", "급락",
        "상승할", "하락할", "오를", "내릴", "확실", "반드시", "유망", "저평가", "고평가", "수혜주", "대박", "기회",
    )

    private val INLINE_KEY = Regex("(NEWS|DISCLOSURE|RELATION|CLUSTER):[^\\s,()]+")
    private val EMPTY_PARENS = Regex("\\s*\\([\\s,]*\\)")
    private val DOUBLE_SPACE = Regex("\\s{2,}")

    data class ValidatedSentences(val sentences: List<BriefingSentence>, val dropped: Int)
    data class ValidatedHeadline(val headline: BriefingHeadline?, val dropped: Int)
    data class ValidatedWatch(val points: List<WatchPoint>, val dropped: Int)

    fun sentences(
        drafts: List<SentenceDraft>,
        allowed: Map<String, Citation>,
        maxChars: Int,
        requireCitation: Boolean,
    ): ValidatedSentences {
        val kept = mutableListOf<BriefingSentence>()
        var dropped = 0
        for (draft in drafts) {
            val citations = resolve(draft.citationKeys, allowed)
            val valid = acceptable(draft.text, maxChars) &&
                citations != null &&
                (!requireCitation || citations.isNotEmpty())
            if (valid) kept += BriefingSentence(clean(draft.text), checkNotNull(citations)) else dropped += 1
        }
        return ValidatedSentences(kept, dropped)
    }

    fun commentary(drafts: List<SentenceDraft>, allowed: Map<String, Citation>): ValidatedSentences {
        val validated = sentences(drafts, allowed, SENTENCE_MAX_CHARS, requireCitation = true)
        val kept = validated.sentences.take(COMMENTARY_MAX_SENTENCES)
        return ValidatedSentences(kept, validated.dropped + (validated.sentences.size - kept.size))
    }

    fun headline(draft: HeadlineDraft?, allowed: Map<String, Citation>): ValidatedHeadline {
        if (draft == null) return ValidatedHeadline(null, 0)
        val citations = resolve(draft.citationKeys, allowed)
        if (!acceptable(draft.text, HEADLINE_MAX_CHARS) || citations == null) return ValidatedHeadline(null, 1)
        return ValidatedHeadline(BriefingHeadline(clean(draft.text), citations), 0)
    }

    fun watchPoints(drafts: List<WatchDraft>?, candidates: List<WatchCandidate>): ValidatedWatch {
        if (drafts == null) return ValidatedWatch(emptyList(), 0)
        val used = mutableSetOf<Int>()
        val kept = mutableListOf<WatchPoint>()
        var dropped = 0
        for (draft in drafts) {
            val candidate = candidates.getOrNull(draft.candidateIndex)
            val valid = candidate != null && used.add(draft.candidateIndex) && acceptable(draft.text, WATCH_MAX_CHARS)
            if (valid) {
                kept += WatchPoint(checkNotNull(candidate).kind, clean(draft.text), listOf(candidate.citation), candidate.stocks)
            } else {
                dropped += 1
            }
        }
        val limited = kept.take(WATCH_MAX_POINTS)
        return ValidatedWatch(limited, dropped + (kept.size - limited.size))
    }

    fun status(headline: BriefingHeadline?, issues: List<BriefingIssue>): BriefingStatus {
        if (headline == null) return BriefingStatus.PARTIAL
        if (issues.isEmpty() || issues.any { it.commentary != null }) return BriefingStatus.READY
        return BriefingStatus.PARTIAL
    }

    private fun resolve(keys: List<String>, allowed: Map<String, Citation>): List<Citation>? {
        val citations = LinkedHashMap<String, Citation>()
        for (key in keys) {
            val citation = allowed[key] ?: return null
            citations[key] = citation
        }
        return citations.values.toList()
    }

    fun clean(text: String): String =
        text.replace(INLINE_KEY, "").replace(EMPTY_PARENS, "").replace(DOUBLE_SPACE, " ").trim()

    private fun acceptable(text: String, maxChars: Int): Boolean {
        val trimmed = clean(text)
        return trimmed.isNotEmpty() && trimmed.length <= maxChars && FORBIDDEN.none { trimmed.contains(it) }
    }
}
