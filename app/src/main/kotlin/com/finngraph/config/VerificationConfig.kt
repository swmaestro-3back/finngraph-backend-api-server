package com.finngraph.config

import com.finngraph.auth.model.CodePolicy
import com.finngraph.auth.model.ConfirmRatePolicy
import com.finngraph.auth.model.SendRatePolicy
import com.finngraph.composition.VerificationPolicies
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@ConfigurationProperties("app.verification")
data class VerificationProperties(
    val codeTtl: Duration,
    val verifiedTtl: Duration,
    val maxAttempts: Int,
    val send: Send,
    val confirm: Confirm,
) {
    data class Send(
        val cooldown: Duration,
        val emailMax: Int,
        val globalMax: Int,
        val window: Duration,
    )

    data class Confirm(
        val ipMax: Int,
        val window: Duration,
    )
}

@Configuration
@EnableConfigurationProperties(VerificationProperties::class)
class VerificationConfig {

    @Bean
    fun verificationPolicies(properties: VerificationProperties): VerificationPolicies =
        VerificationPolicies(
            code = CodePolicy(
                codeTtl = properties.codeTtl,
                maxAttempts = properties.maxAttempts,
                verifiedTtl = properties.verifiedTtl,
            ),
            send = SendRatePolicy(
                cooldown = properties.send.cooldown,
                emailLimit = properties.send.emailMax,
                emailWindow = properties.send.window,
                globalLimit = properties.send.globalMax,
                globalWindow = properties.send.window,
            ),
            confirm = ConfirmRatePolicy(
                ipLimit = properties.confirm.ipMax,
                ipWindow = properties.confirm.window,
            ),
        )
}
