package com.finngraph.dailybriefing

import com.finngraph.composition.port.BriefingWriterPort
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BriefingLlmProperties::class)
class BriefingWriterConfig {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun briefingWriter(properties: BriefingLlmProperties): BriefingWriterPort {
        if (!properties.configured) {
            log.info("briefing writer disabled: BEDROCK_REGION / BEDROCK_CHAT_MODEL / AWS_BEARER_TOKEN_BEDROCK 미설정")
            return DisabledBriefingWriter()
        }
        return KoogBriefingWriter(properties)
    }
}
