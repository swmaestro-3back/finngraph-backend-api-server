package com.finngraph.web.relation

import com.finngraph.composition.relation.RelationEvidenceComposer
import com.finngraph.web.common.DataResponse
import com.finngraph.web.common.InvalidParameterException
import org.springframework.web.bind.annotation.RestController

@RestController
class RelationController(
    private val composer: RelationEvidenceComposer,
) : RelationApi {

    override fun evidence(a: String?, b: String?, type: String?): DataResponse<List<RelationEvidenceResponse>> {
        val left = a?.trim().orEmpty()
        val right = b?.trim().orEmpty()
        val relation = type?.trim()?.takeIf { it.isNotEmpty() }
        val errors = buildMap {
            keyError(left)?.let { put("a", it) }
            keyError(right)?.let { put("b", it) }
            if (relation != null && relation !in RELATION_TYPES) {
                put("type", "must be one of ${RELATION_TYPES.joinToString(", ")}")
            }
        }
        if (errors.isNotEmpty()) {
            throw InvalidParameterException("관계 근거 파라미터가 올바르지 않습니다", errors)
        }
        return DataResponse(composer.between(left, right, relation).map(RelationEvidenceResponse::from))
    }

    private fun keyError(key: String): String? = when {
        key.isEmpty() -> "must not be blank"
        key.length > MAX_KEY_LENGTH -> "must be <= $MAX_KEY_LENGTH characters"
        else -> null
    }

    private companion object {
        const val MAX_KEY_LENGTH = 100
        val RELATION_TYPES = listOf("SUPPLIES_TO", "INVESTS_IN", "ACQUIRES")
    }
}
