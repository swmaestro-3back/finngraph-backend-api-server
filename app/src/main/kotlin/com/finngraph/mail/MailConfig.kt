package com.finngraph.mail

import com.finngraph.composition.port.MailSenderPort
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.mail.javamail.JavaMailSenderImpl

@Configuration
@EnableConfigurationProperties(MailProperties::class)
class MailConfig {

    @Bean
    fun mailSenderPort(properties: MailProperties, environment: Environment): MailSenderPort =
        when (properties.provider) {
            MailProvider.LOG -> {
                check(!environment.acceptsProfiles(Profiles.of(PROD))) {
                    "prod 프로파일에서 app.mail.provider=log는 허용되지 않습니다. MAIL_PROVIDER=smtp와 MAIL_FROM·MAIL_HOST·MAIL_USERNAME·MAIL_PASSWORD를 설정하세요"
                }
                LoggingMailSender(environment)
            }

            MailProvider.SMTP -> {
                check(properties.from.isNotBlank()) { "app.mail.from(MAIL_FROM)이 비어 있습니다" }
                check(properties.host.isNotBlank()) { "app.mail.host(MAIL_HOST)가 비어 있습니다" }
                SmtpMailSender(javaMailSender(properties), properties.from)
            }
        }

    private fun javaMailSender(properties: MailProperties): JavaMailSenderImpl =
        JavaMailSenderImpl().apply {
            host = properties.host
            port = properties.port
            username = properties.username
            password = properties.password
            val millis = properties.timeout.toMillis().toString()
            javaMailProperties["mail.smtp.auth"] = "true"
            javaMailProperties["mail.smtp.starttls.enable"] = "true"
            javaMailProperties["mail.smtp.starttls.required"] = "true"
            javaMailProperties["mail.smtp.connectiontimeout"] = millis
            javaMailProperties["mail.smtp.timeout"] = millis
            javaMailProperties["mail.smtp.writetimeout"] = millis
        }

    private companion object {
        const val PROD = "prod"
    }
}
