package com.finngraph.config

import com.finngraph.auth.model.PasswordAttemptPolicy
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@ConfigurationProperties("app.password-attempt")
data class PasswordAttemptProperties(
    val max: Int,
    val window: Duration,
)

@Configuration
@EnableConfigurationProperties(PasswordAttemptProperties::class)
class PasswordAttemptConfig {

    @Bean
    fun passwordAttemptPolicy(properties: PasswordAttemptProperties): PasswordAttemptPolicy =
        PasswordAttemptPolicy(limit = properties.max, window = properties.window)
}
