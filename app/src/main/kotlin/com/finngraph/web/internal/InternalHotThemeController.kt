package com.finngraph.web.internal

import com.finngraph.hotthemes.HotThemePublishOutcome
import com.finngraph.hotthemes.HotThemePublishService
import com.finngraph.web.common.DataResponse
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

data class HotThemePublishResponse(
    val result: String,
    val themes: Int?,
    val tradeDate: LocalDate?,
)

@RestController
class InternalHotThemeController(
    private val publishService: HotThemePublishService,
) {

    @PostMapping("/internal/hot-themes/publish")
    fun publish(): DataResponse<HotThemePublishResponse> =
        when (val outcome = publishService.publish()) {
            is HotThemePublishOutcome.Published ->
                DataResponse(HotThemePublishResponse("published", outcome.themes, outcome.tradeDate))
            HotThemePublishOutcome.Skipped ->
                DataResponse(HotThemePublishResponse("skipped", null, null))
        }
}
