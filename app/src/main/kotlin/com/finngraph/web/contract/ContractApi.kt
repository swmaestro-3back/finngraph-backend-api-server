package com.finngraph.web.contract

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
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@Tag(name = "Contract", description = "단일판매 및 공급계약 공시 조회")
@RequestMapping("/api/v1/contracts")
interface ContractApi {

    @Operation(
        summary = "최근 공급계약 공시",
        description = "기준일로부터 days일 이내의 유효 공시를 시장 전체에서 sort 기준 내림차순.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(
            responseCode = "400",
            description = "days가 1~90 범위 밖, limit이 1~100 범위 밖.",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "503",
            description = "DB 접속 실패",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @GetMapping("/recent")
    fun recent(
        @Parameter(description = "기준일로부터 거슬러 올라갈 일수")
        @RequestParam(required = false, defaultValue = "7")
        days: Int,
        @Parameter(description = "개수 (1~100)")
        @RequestParam(required = false, defaultValue = "20")
        limit: Int,
        @Parameter(description = "정렬 기준 (salesRatio | contractAmount)")
        @RequestParam(required = false, defaultValue = "salesRatio")
        sort: String,
    ): DataResponse<List<RecentContractResponse>>
}
