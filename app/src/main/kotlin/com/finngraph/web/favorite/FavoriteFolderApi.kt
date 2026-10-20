package com.finngraph.web.favorite

import com.finngraph.web.common.DataResponse
import com.finngraph.web.common.ErrorResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus

@Tag(name = "FavoriteFolder", description = "나만의 테마(관심 폴더). 한 관심을 여러 폴더에 담을 수 있다")
@RequestMapping("/api/v1/me/favorite-folders")
interface FavoriteFolderApi {

    @Operation(summary = "폴더 만들기", description = "이름 1~20자(앞뒤 공백 제거), 사용자당 20개, 같은 이름 불가. 맨 뒤에 붙는다")
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "만들어짐"),
        ApiResponse(
            responseCode = "400",
            description = "이름이 비었거나 20자 초과",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "409",
            description = "폴더 상한(20) 초과 또는 같은 이름",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @RequestBody request: FolderNameRequest,
    ): DataResponse<FavoriteFolderResponse>

    @Operation(summary = "폴더 이름 바꾸기")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "변경됨"),
        ApiResponse(
            responseCode = "400",
            description = "이름이 비었거나 20자 초과",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "404",
            description = "없거나 다른 사용자의 폴더",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "409",
            description = "같은 이름의 다른 폴더가 있음",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @PatchMapping("/{id}")
    fun rename(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable id: Long,
        @RequestBody request: FolderNameRequest,
    ): DataResponse<FavoriteFolderResponse>

    @Operation(summary = "폴더 삭제", description = "멱등. 담겨 있던 관심은 그대로 남는다")
    @ApiResponse(responseCode = "204", description = "삭제됨")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable id: Long,
    )

    @Operation(summary = "폴더 순서 바꾸기", description = "사용자의 모든 폴더 id를 원하는 순서대로 한 번씩")
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "변경됨"),
        ApiResponse(
            responseCode = "400",
            description = "빠진 폴더나 중복·남의 폴더 id가 있음",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @PutMapping("/order")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun reorder(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @RequestBody request: FolderOrderRequest,
    )

    @Operation(
        summary = "폴더에 담기",
        description = "멱등. 종목만 담는다. 아직 관심이 아니면 관심에도 함께 담는다(관심 50개 상한 적용)",
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "담김 또는 이미 담겨 있음"),
        ApiResponse(
            responseCode = "400",
            description = "type이 STOCK이 아니거나 key 형식 오류",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "404",
            description = "폴더가 없거나 존재하지 않는 종목",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "409",
            description = "관심 목록 상한(50) 초과",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @PutMapping("/{id}/items/{type}/{key}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun addItem(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable id: Long,
        @Parameter(description = "STOCK만") @PathVariable type: String,
        @Parameter(description = "종목 ticker") @PathVariable key: String,
    )

    @Operation(summary = "폴더에서 빼기", description = "멱등. 관심 자체는 해제되지 않는다")
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "빠짐"),
        ApiResponse(
            responseCode = "404",
            description = "없거나 다른 사용자의 폴더",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @DeleteMapping("/{id}/items/{type}/{key}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeItem(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable id: Long,
        @Parameter(description = "STOCK만") @PathVariable type: String,
        @Parameter(description = "종목 ticker") @PathVariable key: String,
    )

    @Operation(
        summary = "담기 추천",
        description = "대상 종목과 같은 테마(60종목 넘는 넓은 테마 제외)로 묶이는 종목이 많은 폴더부터. 좁은 테마로 겹칠수록 앞선다",
    )
    @GetMapping("/suggest")
    fun suggest(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @Parameter(description = "STOCK만") @RequestParam type: String,
        @Parameter(description = "종목 ticker") @RequestParam key: String,
    ): DataResponse<FolderSuggestResponse>

    @Operation(
        summary = "폴더 수익률",
        description = "담긴 종목을 같은 비중으로 묶은 누적 수익률(%) 시계열. 테마 항목은 계산에서 빠진다",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "조회 성공"),
        ApiResponse(
            responseCode = "400",
            description = "period가 1W·1M·3M이 아님",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "404",
            description = "없거나 다른 사용자의 폴더",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @GetMapping("/{id}/returns")
    fun returns(
        @Parameter(hidden = true) @AuthenticationPrincipal userId: Long,
        @PathVariable id: Long,
        @Parameter(description = "1W, 1M, 3M") @RequestParam(required = false, defaultValue = "1M") period: String,
    ): DataResponse<FolderReturnsResponse>
}
