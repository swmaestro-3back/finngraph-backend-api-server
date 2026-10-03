package com.finngraph.calendar.adapter

import com.finngraph.calendar.model.FundUse
import com.finngraph.calendar.model.Putback
import com.finngraph.calendar.model.Seller
import com.finngraph.calendar.model.Underwriter
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal

internal object IpoFilingJson {

    private val mapper: JsonMapper = JsonMapper.shared()
    private val ABSENT = setOf("", "-")

    fun underwriters(raw: String?): List<Underwriter> = rows(raw).mapNotNull { row ->
        val name = text(row, "name") ?: return@mapNotNull null
        Underwriter(name, text(row, "role"), whole(row, "shares"), decimal(row, "amount"), text(row, "method"))
    }

    fun fundUses(raw: String?): List<FundUse> = rows(raw).mapNotNull { row ->
        val purpose = text(row, "purpose") ?: return@mapNotNull null
        val amount = decimal(row, "amount") ?: return@mapNotNull null
        FundUse(purpose, amount)
    }

    fun sellers(raw: String?): List<Seller> = rows(raw).mapNotNull { row ->
        val holder = text(row, "holder") ?: return@mapNotNull null
        Seller(holder, text(row, "relation"), whole(row, "before"), whole(row, "sold"), whole(row, "after"))
    }

    fun putback(raw: String?): Putback? {
        val node = tree(raw)?.takeIf { it.isObject } ?: return null
        return Putback(
            reason = text(node, "reason"),
            investors = text(node, "investors"),
            shares = text(node, "shares"),
            period = text(node, "period"),
            price = text(node, "price"),
        )
    }

    private fun tree(raw: String?): JsonNode? = raw?.let { mapper.readTree(it) }

    private fun rows(raw: String?): List<JsonNode> =
        tree(raw)?.takeIf { it.isArray }?.filter { it.isObject }.orEmpty()

    private fun text(node: JsonNode, key: String): String? {
        val value = node.get(key)?.takeIf { it.isValueNode && !it.isNull } ?: return null
        return value.asString().trim().takeUnless { it in ABSENT }
    }

    private fun decimal(node: JsonNode, key: String): BigDecimal? {
        val value = node.get(key) ?: return null
        return when {
            value.isNumber -> value.decimalValue()
            value.isString -> value.asString().replace(",", "").trim().toBigDecimalOrNull()
            else -> null
        }
    }

    private fun whole(node: JsonNode, key: String): Long? =
        decimal(node, key)?.let { runCatching { it.longValueExact() }.getOrNull() }
}
