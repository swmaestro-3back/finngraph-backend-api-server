package com.finngraph.dailybriefing

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.bedrock.BedrockAPIMethod
import ai.koog.prompt.executor.clients.bedrock.BedrockLLMClient
import ai.koog.prompt.executor.clients.bedrock.StaticBearerTokenProvider
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.model.StructureFixingParser
import ai.koog.prompt.executor.model.executeStructured
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import aws.sdk.kotlin.services.bedrockruntime.BedrockRuntimeClient
import aws.smithy.kotlin.runtime.auth.AuthSchemeId
import com.finngraph.composition.briefing.BriefingValidator
import com.finngraph.composition.briefing.HeadlineDraft
import com.finngraph.composition.briefing.SentenceDraft
import com.finngraph.composition.briefing.WatchDraft
import com.finngraph.composition.port.BriefingWriterPort
import com.finngraph.composition.port.CommentaryInput
import com.finngraph.composition.port.HeadlineInput
import com.finngraph.composition.port.WatchInput
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.milliseconds

@Serializable
@SerialName("HeadlineOutput")
@LLMDescription("One-sentence headline summarizing today's market")
data class HeadlineOutput(
    @property:LLMDescription("One sentence of 40-90 characters, in Korean")
    val text: String,
    @property:LLMDescription("CLUSTER citation keys of the issues the sentence mentions")
    val citations: List<String> = emptyList(),
)

@Serializable
@SerialName("SentenceOutput")
@LLMDescription("One sentence with supporting citations")
data class SentenceOutput(
    @property:LLMDescription("Sentence of at most 90 characters, in Korean")
    val text: String,
    @property:LLMDescription("Citation keys supporting the sentence (NEWS:, RELATION:, DISCLOSURE:)")
    val citations: List<String> = emptyList(),
)

@Serializable
@SerialName("CommentaryOutput")
@LLMDescription("Issue commentary of 2-3 sentences")
data class CommentaryOutput(
    val sentences: List<SentenceOutput>,
)

@Serializable
@SerialName("WatchItemOutput")
@LLMDescription("One candidate rewritten as an observation sentence")
data class WatchItemOutput(
    @property:LLMDescription("Candidate index, starting at 0")
    val candidateIndex: Int,
    @property:LLMDescription("Observation sentence of at most 80 characters, in Korean")
    val text: String,
)

@Serializable
@SerialName("WatchOutput")
@LLMDescription("Watch points (at most 4)")
data class WatchOutput(
    val items: List<WatchItemOutput>,
)

class KoogBriefingWriter(
    private val properties: BriefingLlmProperties,
    private val executor: PromptExecutor = bedrockExecutor(properties),
) : BriefingWriterPort {

    override val enabled: Boolean = true

    private val model = LLModel(
        provider = LLMProvider.Bedrock,
        id = properties.modelId,
        capabilities = listOf(LLMCapability.Completion, LLMCapability.Temperature, LLMCapability.Schema.JSON.Basic),
        contextLength = CONTEXT_LENGTH,
        maxOutputTokens = MAX_OUTPUT_TOKENS,
    )
    private val fixingParser = StructureFixingParser(model = model, retries = FIXING_RETRIES)

    override fun headline(input: HeadlineInput): HeadlineDraft? {
        val first = request<HeadlineOutput>("briefing-headline", BriefingPrompts.HEADLINE_SYSTEM, BriefingPrompts.headlineUser(input), HEADLINE_TOKENS)
            ?: return null
        if (first.text.trim().length <= BriefingValidator.HEADLINE_MAX_CHARS) return HeadlineDraft(first.text, first.citations)
        val shortened = request<HeadlineOutput>(
            "briefing-headline-shorten",
            BriefingPrompts.HEADLINE_SYSTEM,
            BriefingPrompts.shortenUser(first.text, BriefingValidator.HEADLINE_MAX_CHARS),
            HEADLINE_TOKENS,
        ) ?: return HeadlineDraft(first.text, first.citations)
        return HeadlineDraft(shortened.text, shortened.citations.ifEmpty { first.citations })
    }

    override fun commentary(input: CommentaryInput): List<SentenceDraft>? =
        request<CommentaryOutput>("briefing-commentary", BriefingPrompts.COMMENTARY_SYSTEM, BriefingPrompts.commentaryUser(input), COMMENTARY_TOKENS)
            ?.sentences?.map { SentenceDraft(it.text, it.citations) }

    override fun watchPoints(input: WatchInput): List<WatchDraft>? =
        request<WatchOutput>("briefing-watch", BriefingPrompts.WATCH_SYSTEM, BriefingPrompts.watchUser(input), WATCH_TOKENS)
            ?.items?.map { WatchDraft(it.candidateIndex, it.text, emptyList()) }

    private inline fun <reified T> request(id: String, system: String, user: String, maxTokens: Int): T? = runBlocking {
        val prompt = prompt(id, LLMParams(temperature = 0.0, maxTokens = maxTokens)) {
            system(system)
            user(user)
        }
        executor.executeStructured<T>(prompt = prompt, model = model, fixingParser = fixingParser)
            .getOrThrow()
            .data
    }

    companion object {
        private const val CONTEXT_LENGTH = 200_000L
        private const val MAX_OUTPUT_TOKENS = 4_096L
        private const val FIXING_RETRIES = 1
        private const val HEADLINE_TOKENS = 300
        private const val COMMENTARY_TOKENS = 600
        private const val WATCH_TOKENS = 800

        fun bedrockExecutor(properties: BriefingLlmProperties): PromptExecutor {
            val runtime = BedrockRuntimeClient {
                region = properties.region
                bearerTokenProvider = StaticBearerTokenProvider(properties.bearerToken)
                authSchemePreference = listOf(AuthSchemeId.HttpBearer)
                callTimeout = properties.timeout.toMillis().milliseconds
            }
            val client = BedrockLLMClient(bedrockClient = runtime, apiMethod = BedrockAPIMethod.Converse)
            return MultiLLMPromptExecutor(client)
        }
    }
}
