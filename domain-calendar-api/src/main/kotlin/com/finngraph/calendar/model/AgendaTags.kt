package com.finngraph.calendar.model

object AgendaTags {

    private val RESERVE_CUTS = listOf("감액", "감소", "이익잉여금전입")

    private val RULES: List<Pair<String, (String) -> Boolean>> = listOf(
        "합병" to { text -> "합병" in text },
        "액면분할" to { text -> isStockSplit(text) },
        "분할" to { text -> "분할" in text && !isStockSplit(text) },
        "주식병합" to { text -> "병합" in text },
        "감자" to { text -> "자본감소" in text || "감자" in text },
        "결손 보전" to { text -> "결손" in text },
        "자본준비금 감액" to { text -> "자본준비금" in text && RESERVE_CUTS.any { it in text } && "결손" !in text },
        "스톡옵션" to { text -> "주식매수선택권" in text },
        "자사주" to { text -> "자기주식" in text },
        "임원 해임" to { text -> "해임" in text },
        "주주제안" to { text -> "주주제안" in text },
        "영업양수도" to { text -> "영업양수" in text || "영업양도" in text },
        "액면변경" to { text -> "액면변경" in text },
    )

    fun of(agendum: String): List<String> {
        val text = agendum.filterNot(Char::isWhitespace)
        return RULES.filter { (_, matches) -> matches(text) }.map { (tag, _) -> tag }
    }

    private fun isStockSplit(text: String): Boolean = "액면분할" in text || "주식분할" in text
}
