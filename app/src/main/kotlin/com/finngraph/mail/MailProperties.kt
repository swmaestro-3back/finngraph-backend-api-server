package com.finngraph.mail

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

enum class MailProvider { LOG, SMTP }

@ConfigurationProperties("app.mail")
data class MailProperties(
    val provider: MailProvider = MailProvider.LOG,
    val from: String = "",
    val host: String = "",
    val port: Int = 587,
    val username: String = "",
    val password: String = "",
    val timeout: Duration = Duration.ofSeconds(5),
)
