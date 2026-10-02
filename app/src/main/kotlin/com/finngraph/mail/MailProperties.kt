package com.finngraph.mail

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

enum class MailProvider { LOG, SES }

@ConfigurationProperties("app.mail")
data class MailProperties(
    val provider: MailProvider = MailProvider.LOG,
    val from: String = "",
    val timeout: Duration = Duration.ofSeconds(5),
    val region: String = "ap-northeast-2",
)
