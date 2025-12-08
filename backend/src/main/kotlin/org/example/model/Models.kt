package org.example.model

import kotlinx.serialization.Serializable

// Модели для LLM API (специфичны для backend)

@Serializable
data class LLMRequest(
    val model: String,
    val messages: List<LLMMessage>,
    val tools: List<Tool>? = null,
    val max_tokens: Int = 4096,
    val temperature: Float? = null
)

@Serializable
data class LLMMessage(
    val role: String,
    val content: String? = null,
    val tool_calls: List<LLMToolCall>? = null,
    val tool_call_id: String? = null
)

@Serializable
data class LLMToolCall(
    val id: String,
    val type: String? = null,
    val function: FunctionCall
)

@Serializable
data class FunctionCall(
    val name: String,
    val arguments: String
)

@Serializable
data class Tool(
    val type: String,
    val function: FunctionDefinition
)

@Serializable
data class FunctionDefinition(
    val name: String,
    val description: String,
    val parameters: FunctionParameters
)

@Serializable
data class FunctionParameters(
    val type: String,
    val properties: Map<String, PropertyDefinition>,
    val required: List<String> = emptyList()
)

@Serializable
data class PropertyDefinition(
    val type: String,
    val description: String,
    val enum: List<String>? = null
)

@Serializable
data class LLMResponse(
    val id: String? = null,
    val choices: List<Choice>
)

@Serializable
data class Choice(
    val message: LLMMessage,
    val finish_reason: String? = null
)

// Модели для Streaming API (SSE)

@Serializable
data class LLMStreamRequest(
    val model: String,
    val messages: List<LLMMessage>,
    val tools: List<Tool>? = null,
    val max_tokens: Int = 4096,
    val temperature: Float? = null,
    val stream: Boolean = true
)

@Serializable
data class LLMStreamChunk(
    val id: String? = null,
    val `object`: String? = null,
    val created: Long? = null,
    val model: String? = null,
    val choices: List<StreamChoice> = emptyList(),
    val usage: Usage? = null
)

@Serializable
data class StreamChoice(
    val index: Int = 0,
    val delta: DeltaMessage? = null,
    val finish_reason: String? = null
)

@Serializable
data class DeltaMessage(
    val role: String? = null,
    val content: String? = null,
    val tool_calls: List<DeltaToolCall>? = null
)

@Serializable
data class DeltaToolCall(
    val index: Int = 0,
    val id: String? = null,
    val type: String? = null,
    val function: DeltaFunctionCall? = null
)

@Serializable
data class DeltaFunctionCall(
    val name: String? = null,
    val arguments: String? = null
)

@Serializable
data class Usage(
    val prompt_tokens: Int = 0,
    val completion_tokens: Int = 0,
    val total_tokens: Int = 0
)
