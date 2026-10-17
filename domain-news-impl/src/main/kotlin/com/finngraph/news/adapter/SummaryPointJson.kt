package com.finngraph.news.adapter

import com.finngraph.news.model.SummaryPoint
import tools.jackson.databind.json.JsonMapper

internal object SummaryPointJson {

    private val mapper: JsonMapper = JsonMapper.shared()

    fun parse(raw: String?): List<SummaryPoint> {
        if (raw.isNullOrBlank()) return emptyList()
        val node = runCatching { mapper.readTree(raw) }.getOrNull()?.takeIf { it.isArray } ?: return emptyList()
        return node.mapNotNull { item ->
            val kind = item.get("kind")?.asString()?.trim().orEmpty()
            val text = item.get("text")?.asString()?.trim().orEmpty()
            if (kind.isEmpty() || text.isEmpty()) null else SummaryPoint(kind, text)
        }
    }
}
