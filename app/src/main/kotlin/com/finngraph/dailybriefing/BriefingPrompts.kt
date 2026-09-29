package com.finngraph.dailybriefing

import com.finngraph.composition.briefing.BriefingValidator
import com.finngraph.composition.port.CommentaryInput
import com.finngraph.composition.port.HeadlineInput
import com.finngraph.composition.port.WatchInput
import java.time.format.DateTimeFormatter

object BriefingPrompts {

    private val TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm")

    private val COMMON_RULES = """
        You are the editor of a daily briefing on the Korean stock market. Follow every rule below.
        - Never state a fact that is not in the input. Copy numbers exactly as given.
        - Do not predict returns or price direction. Do not use buy, sell, or recommendation language.
        - Never use these words: ${BriefingValidator.FORBIDDEN.joinToString(", ")}
        - Write every sentence in Korean, in the polite "~습니다" style.
        - In the citations field, use only citation keys that appear in the input (e.g. NEWS:123, RELATION:45, DISCLOSURE:2026..., CLUSTER:7), copied verbatim.
        - Do not put citation keys or parenthesized sources inside text. The text contains facts only.
    """.trimIndent()

    val HEADLINE_SYSTEM: String = """
        $COMMON_RULES
        - Summarize today's market in one sentence. Aim for 60-80 characters including spaces and never exceed 90.
        - Always include the number of advancing and declining stocks. Omit filler such as the date, "국내 증시는", or "마감하였으며".
        - If you mention an issue, put that issue's CLUSTER key in citations. Citations may be empty.
    """.trimIndent()

    fun shortenUser(text: String, maxChars: Int): String =
        "Shorten the following into one sentence of at most $maxChars characters including spaces. Keep the numbers and facts; trim modifiers. Answer in Korean.\n$text"

    val COMMENTARY_SYSTEM: String = """
        $COMMON_RULES
        - Explain in 2-3 sentences what this issue is and which companies are involved. At most 90 characters per sentence.
        - Every sentence must carry at least one NEWS, RELATION, or DISCLOSURE key in citations that supports it.
        - When a relation's tense is future_or_planned, state explicitly that it is a plan (계획).
    """.trimIndent()

    val WATCH_SYSTEM: String = """
        $COMMON_RULES
        - Rewrite only facts from the candidate list as observation sentences in the form "what to check next" (at most 80 characters).
        - Do not invent items that are not in the candidates. You may select none. At most 4 items.
        - candidateIndex is the candidate number, starting at 0.
    """.trimIndent()

    fun headlineUser(input: HeadlineInput): String = buildString {
        appendLine("Base date: ${input.baseDate}")
        appendLine("Market breadth: up ${input.market.upCount} · down ${input.market.downCount} · flat ${input.market.flatCount} (${input.market.pricedCount} stocks priced)")
        input.market.medianChange?.let { appendLine("Median change: ${it.toPlainString()}%") }
        if (input.themes.isNotEmpty()) {
            appendLine("Hot themes:")
            input.themes.forEach { theme ->
                val leaders = theme.leaders.joinToString(", ") { "${it.name} ${it.change.toPlainString()}%" }
                appendLine("- ${theme.name} (${theme.hotSide}, ${theme.change?.toPlainString() ?: "-"}%, up ${theme.upCount}/down ${theme.downCount}) leaders: $leaders")
            }
        }
        if (input.issues.isNotEmpty()) {
            appendLine("Today's issues:")
            input.issues.forEach { appendLine("- [CLUSTER:${it.clusterId}] ${it.title} (${it.newsCount} articles)") }
        }
    }

    fun commentaryUser(input: CommentaryInput): String = buildString {
        appendLine("Issue: ${input.title}")
        if (input.keywords.isNotEmpty()) appendLine("Keywords: ${input.keywords.joinToString(", ")}")
        if (input.stocks.isNotEmpty()) {
            appendLine("Related stocks: " + input.stocks.joinToString(", ") { "${it.name}(${it.change?.toPlainString() ?: "-"}%)" })
        }
        appendLine("Articles:")
        input.articles.forEach { article ->
            val time = article.publishedAt?.format(TIME) ?: "-"
            appendLine("- [NEWS:${article.newsId}] $time ${article.title}")
            if (article.text.isNotBlank()) appendLine("  ${article.text.take(TEXT_LIMIT)}")
        }
        if (input.relations.isNotEmpty()) {
            appendLine("Extracted relations:")
            input.relations.forEach { appendLine("- [RELATION:${it.id}] ${it.sentence} (polarity ${it.polarity}, tense ${it.tense})") }
        }
        if (input.contracts.isNotEmpty()) {
            appendLine("Disclosures:")
            input.contracts.forEach { contract ->
                val amount = contract.contractAmountText?.let { "amount $it" } ?: ""
                val ratio = contract.salesRatioText?.let { "sales ratio $it%" } ?: ""
                appendLine("- [DISCLOSURE:${contract.rceptNo}] ${contract.reportName} ${contract.contractName ?: ""} $amount $ratio".trimEnd())
            }
        }
    }

    fun watchUser(input: WatchInput): String = buildString {
        appendLine("Candidates:")
        input.candidates.forEachIndexed { index, candidate ->
            appendLine("$index. [${candidate.kind}] ${candidate.factText} (cite ${candidate.citation.key()})")
        }
    }

    private const val TEXT_LIMIT = 600
}
