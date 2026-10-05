package com.finngraph.web.relation

import com.finngraph.web.common.DataResponse
import com.finngraph.web.common.ErrorResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

@Tag(name = "Relation", description = "기업 간 관계 근거(ETL relation_sources) 조회 — 회원 전용")
interface RelationApi {

    @Operation(
        summary = "두 회사 사이 관계 근거",
        description = "a & b 사이 관계의 근거를 양방향 전부 내려준다. a&b는 종목코드(해외 티커 포함) 또는 회사명이며 " +
            "relation_sources의 code 또는 name과 정확히 같으면 맞는다. type을 주면 그 관계만 준다. " +
            "뉴스 근거는 공개 기사(관계 추출 완료)만이다. 정렬은 mentionedAt 내림차순. " +
            "url은 뉴스면 원문 링크(없으면 포털 링크), 공시면 DART 원문 링크이고 title은 기사 제목 또는 공시 보고서명이다",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공. 근거가 없으면 data가 빈 배열"),
        ApiResponse(
            responseCode = "400",
            description = "a·b가 없거나 공백, 100자 초과, type이 SUPPLIES_TO·INVESTS_IN·ACQUIRES가 아님",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "401",
            description = "토큰 부재·만료·위조",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @GetMapping("/api/v1/relations/evidence")
    fun evidence(
        @Parameter(description = "한쪽 회사의 종목코드 또는 회사명") @RequestParam(required = false) a: String?,
        @Parameter(description = "다른 쪽 회사의 종목코드 또는 회사명") @RequestParam(required = false) b: String?,
        @Parameter(
            description = "관계 유형. 생략하면 전부",
            schema = Schema(allowableValues = ["SUPPLIES_TO", "INVESTS_IN", "ACQUIRES"]),
        )
        @RequestParam(required = false)
        type: String?,
    ): DataResponse<List<RelationEvidenceResponse>>
}
