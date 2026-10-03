package com.finngraph.mail

import com.finngraph.composition.port.MailSenderPort
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sesv2.SesV2Client

@Configuration
@EnableConfigurationProperties(MailProperties::class)
class MailConfig {

    @Bean
    fun mailSenderPort(properties: MailProperties, environment: Environment): MailSenderPort =
        when (properties.provider) {
            MailProvider.LOG -> {
                check(!environment.acceptsProfiles(Profiles.of(PROD))) {
                    "prod 프로파일에서 app.mail.provider=log는 허용되지 않습니다. MAIL_PROVIDER=ses와 MAIL_FROM을 설정하세요"
                }
                LoggingMailSender(environment)
            }

            MailProvider.SES -> {
                check(properties.from.isNotBlank()) { "app.mail.from(MAIL_FROM)이 비어 있습니다" }
                SesMailSender(sesClient(properties), properties.from)
            }
        }

    private fun sesClient(properties: MailProperties): SesV2Client =
        SesV2Client.builder()
            .region(Region.of(properties.region))
            .overrideConfiguration { it.apiCallTimeout(properties.timeout) }
            .build()

    private companion object {
        const val PROD = "prod"
    }
}
