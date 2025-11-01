package com.github.gyeom.jiraautomation.services

import com.github.gyeom.jiraautomation.model.GeneratedTicket
import com.github.gyeom.jiraautomation.model.OutputLanguage
import com.github.gyeom.jiraautomation.settings.JiraSettingsState
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Service(Service.Level.PROJECT)
class AIService(private val project: Project) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val settings = JiraSettingsState.getInstance(project)

    fun generateTicketFromDiff(
        diffSummary: String,
        diffContent: String,
        language: OutputLanguage
    ): Result<GeneratedTicket> {
        val state = settings.state

        if (state.aiApiKey.isEmpty()) {
            return Result.failure(Exception("AI API key not configured. Please configure in Settings → Tools → Jira Ticket Creator"))
        }

        return when (state.aiProvider.lowercase()) {
            "openai" -> generateWithOpenAI(diffSummary, diffContent, language)
            "anthropic" -> generateWithAnthropic(diffSummary, diffContent, language)
            else -> Result.failure(Exception("Unsupported AI provider: ${state.aiProvider}"))
        }
    }

    private fun generateWithOpenAI(
        diffSummary: String,
        diffContent: String,
        language: OutputLanguage
    ): Result<GeneratedTicket> {
        val state = settings.state
        val prompt = buildPrompt(diffSummary, diffContent, language)

        val requestJson = JsonObject().apply {
            addProperty("model", state.aiModel)
            add("messages", gson.toJsonTree(listOf(
                mapOf("role" to "system", "content" to "You are a helpful assistant that creates Jira tickets from code changes."),
                mapOf("role" to "user", "content" to prompt)
            )))
            addProperty("temperature", 0.7)
            addProperty("max_tokens", 1500)
        }

        val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer ${state.aiApiKey}")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
                    val content = jsonResponse
                        .getAsJsonArray("choices")
                        .get(0).asJsonObject
                        .getAsJsonObject("message")
                        .get("content").asString

                    parseGeneratedTicket(content)
                } else {
                    Result.failure(Exception("OpenAI API error: ${response.code} ${response.message}\n$responseBody"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Failed to generate ticket with OpenAI: ${e.message}", e))
        }
    }

    private fun generateWithAnthropic(
        diffSummary: String,
        diffContent: String,
        language: OutputLanguage
    ): Result<GeneratedTicket> {
        val state = settings.state
        val prompt = buildPrompt(diffSummary, diffContent, language)

        val requestJson = JsonObject().apply {
            addProperty("model", state.aiModel)
            addProperty("max_tokens", 1500)
            add("messages", gson.toJsonTree(listOf(
                mapOf("role" to "user", "content" to prompt)
            )))
        }

        val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", state.aiApiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
                    val content = jsonResponse
                        .getAsJsonArray("content")
                        .get(0).asJsonObject
                        .get("text").asString

                    parseGeneratedTicket(content)
                } else {
                    Result.failure(Exception("Anthropic API error: ${response.code} ${response.message}\n$responseBody"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Failed to generate ticket with Anthropic: ${e.message}", e))
        }
    }

    private fun buildPrompt(
        diffSummary: String,
        diffContent: String,
        language: OutputLanguage
    ): String {
        // Limit diff content to prevent token overflow
        val truncatedDiff = if (diffContent.length > 3000) {
            diffContent.substring(0, 3000) + "\n... (truncated)"
        } else {
            diffContent
        }

        return """
Given the following code changes, generate a Jira ticket in ${language.displayName}.

$diffSummary

Detailed Changes:
$truncatedDiff

Please generate:
1. A concise Jira ticket title (max 100 characters, in ${language.displayName})
   - Should clearly describe what was changed
   - Format: [Category] Brief description
   - Example (Korean): [기능] 사용자 인증 로직 구현
   - Example (English): [Feature] Implement user authentication

2. A detailed description (in ${language.displayName}) with the following sections:
   - **What was changed**: Specific changes made to the code
   - **Why it was changed**: Reasoning and motivation behind the changes
   - **Impact**: Potential effects on the system, dependencies, or users
   - **Technical details**: Any important implementation notes

Format your response EXACTLY as JSON:
{
  "title": "your title here",
  "description": "## What was changed\n...\n\n## Why it was changed\n...\n\n## Impact\n...\n\n## Technical details\n..."
}

Important:
- Use ${language.displayName} for all text
- Keep the title under 100 characters
- Use markdown formatting in the description
- Be specific and concise
""".trimIndent()
    }

    private fun parseGeneratedTicket(content: String): Result<GeneratedTicket> {
        return try {
            // Try to extract JSON from the response
            val jsonStart = content.indexOf("{")
            val jsonEnd = content.lastIndexOf("}") + 1

            if (jsonStart == -1 || jsonEnd <= jsonStart) {
                // Fallback: use the raw content
                return Result.success(
                    GeneratedTicket(
                        title = "Code Changes",
                        description = content
                    )
                )
            }

            val jsonString = content.substring(jsonStart, jsonEnd)
            val jsonObject = gson.fromJson(jsonString, JsonObject::class.java)

            val title = jsonObject.get("title")?.asString ?: "Code Changes"
            val description = jsonObject.get("description")?.asString ?: content

            Result.success(GeneratedTicket(title, description))
        } catch (e: Exception) {
            // If parsing fails, return raw content
            Result.success(
                GeneratedTicket(
                    title = "Code Changes",
                    description = content
                )
            )
        }
    }

    /**
     * Fetch available models from OpenAI API
     */
    fun fetchOpenAIModels(apiKey: String): Result<List<String>> {
        try {
            val request = Request.Builder()
                .url("https://api.openai.com/v1/models")
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
                val data = jsonResponse.getAsJsonArray("data")

                val models = data
                    .map { it.asJsonObject.get("id").asString }
                    .filter { it.startsWith("gpt-") } // Only GPT models
                    .sorted()
                    .reversed() // Newer models first

                println("Fetched ${models.size} OpenAI models")
                return Result.success(models)
            } else {
                println("Failed to fetch OpenAI models: ${response.code}")
                return Result.failure(Exception("Failed to fetch models: ${response.code}"))
            }
        } catch (e: Exception) {
            println("Error fetching OpenAI models: ${e.message}")
            return Result.failure(e)
        }
    }

    /**
     * Fetch available models from Anthropic API
     */
    fun fetchAnthropicModels(apiKey: String): Result<List<String>> {
        try {
            val request = Request.Builder()
                .url("https://api.anthropic.com/v1/models")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
                val data = jsonResponse.getAsJsonArray("data")

                val models = data
                    .map { it.asJsonObject.get("id").asString }
                    .sorted()
                    .reversed() // Newer models first

                println("Fetched ${models.size} Anthropic models")
                return Result.success(models)
            } else {
                println("Failed to fetch Anthropic models: ${response.code}")
                return Result.failure(Exception("Failed to fetch models: ${response.code}"))
            }
        } catch (e: Exception) {
            println("Error fetching Anthropic models: ${e.message}")
            return Result.failure(e)
        }
    }

    /**
     * Get fallback models if API fetch fails
     */
    fun getFallbackModels(provider: String): List<String> {
        return when (provider.lowercase()) {
            "openai" -> listOf(
                "gpt-4o",
                "gpt-4o-mini",
                "gpt-4-turbo",
                "gpt-4",
                "gpt-3.5-turbo"
            )
            "anthropic" -> listOf(
                "claude-3-5-sonnet-20241022",
                "claude-3-5-haiku-20241022",
                "claude-3-opus-20240229",
                "claude-3-sonnet-20240229",
                "claude-3-haiku-20240307"
            )
            else -> emptyList()
        }
    }
}
