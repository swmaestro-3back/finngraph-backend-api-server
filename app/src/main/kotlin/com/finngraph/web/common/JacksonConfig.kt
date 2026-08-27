package com.finngraph.web.common

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.SerializationContext
import tools.jackson.databind.ValueSerializer
import tools.jackson.databind.module.SimpleModule
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Configuration
class JacksonConfig {

    @Bean
    fun offsetDateTimeKstCustomizer(): JsonMapperBuilderCustomizer =
        JsonMapperBuilderCustomizer { builder ->
            builder.addModule(
                SimpleModule("offset-date-time-kst").apply {
                    addSerializer(OffsetDateTime::class.java, OffsetDateTimeKstSerializer())
                },
            )
        }

    private class OffsetDateTimeKstSerializer : ValueSerializer<OffsetDateTime>() {
        override fun serialize(value: OffsetDateTime, gen: JsonGenerator, ctxt: SerializationContext) {
            gen.writeString(
                value.withOffsetSameInstant(ZoneOffset.ofHours(9))
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            )
        }
    }
}
