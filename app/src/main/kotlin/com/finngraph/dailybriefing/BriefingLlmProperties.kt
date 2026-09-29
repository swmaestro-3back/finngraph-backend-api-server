package com.finngraph.dailybriefing

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("app.briefing.bedrock")
data class BriefingLlmProperties(
    val region: String = "",
    val modelId: String = "",
    val bearerToken: String = "",
    val timeout: Duration = Duration.ofSeconds(60),
    val maxRetries: Int = 2,
) {
    val configured: Boolean
        get() = region.isNotBlank() && modelId.isNotBlank() && bearerToken.isNotBlank()
}
