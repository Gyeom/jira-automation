package com.github.gyeom.jiraautomation.services

import com.github.gyeom.jiraautomation.model.*
import com.github.gyeom.jiraautomation.settings.JiraSettingsState
import com.google.gson.Gson
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Service(Service.Level.PROJECT)
class JiraApiService(private val project: Project) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val settings = JiraSettingsState.getInstance(project)

    fun createIssue(
        title: String,
        description: String,
        projectKey: String? = null,
        issueType: String? = null,
        priority: String? = null
    ): Result<JiraIssueResponse> {
        val state = settings.state

        println("=== Creating Jira Issue ===")
        println("URL: ${state.jiraUrl}")
        println("Username: ${state.jiraUsername}")
        println("Project: $projectKey (default: ${state.defaultProjectKey})")
        println("Issue Type: $issueType (default: ${state.defaultIssueType})")
        println("Title: $title")

        if (state.jiraUrl.isEmpty() || state.jiraUsername.isEmpty() || state.jiraApiToken.isEmpty()) {
            return Result.failure(Exception("Jira credentials not configured. Please configure in Settings → Tools → Jira Ticket Creator"))
        }

        val projectKeyToUse = projectKey ?: state.defaultProjectKey
        val issueTypeToUse = issueType ?: state.defaultIssueType

        if (projectKeyToUse.isEmpty()) {
            return Result.failure(Exception("Project key is required"))
        }

        println("Using Project: $projectKeyToUse, Issue Type: $issueTypeToUse")

        val descriptionContent = convertMarkdownToJiraFormat(description)

        val issueRequest = JiraIssueRequest(
            fields = JiraIssueFields(
                project = JiraProject(key = projectKeyToUse),
                summary = title,
                description = descriptionContent,
                issuetype = JiraIssueType(name = issueTypeToUse),
                priority = priority?.let { JiraPriority(name = it) }
            )
        )

        val json = gson.toJson(issueRequest)
        println("Request JSON: $json")

        val requestBody = json.toRequestBody("application/json".toMediaType())

        val url = "${state.jiraUrl.trimEnd('/')}/rest/api/3/issue"
        val credentials = Credentials.basic(state.jiraUsername, state.jiraApiToken)

        println("Request URL: $url")
        println("Auth: ${state.jiraUsername}:***")

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", credentials)
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val issueResponse = gson.fromJson(responseBody, JiraIssueResponse::class.java)
                    println("Successfully created Jira issue: ${issueResponse.key}")
                    println("Response: $responseBody")
                    Result.success(issueResponse)
                } else {
                    println("Failed to create Jira issue. Response code: ${response.code}")
                    println("Response body: $responseBody")
                    val errorResponse = try {
                        gson.fromJson(responseBody, JiraErrorResponse::class.java)
                    } catch (e: Exception) {
                        null
                    }

                    val errorMessage = errorResponse?.errorMessages?.joinToString(", ")
                        ?: errorResponse?.errors?.entries?.joinToString(", ") { "${it.key}: ${it.value}" }
                        ?: "HTTP ${response.code}: ${response.message}"

                    Result.failure(Exception("Failed to create Jira issue: $errorMessage"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Failed to connect to Jira: ${e.message}", e))
        }
    }

    private fun convertMarkdownToJiraFormat(markdown: String): JiraDescription {
        val lines = markdown.split("\n")
        val content = mutableListOf<JiraContent>()

        var currentParagraphLines = mutableListOf<String>()

        fun flushParagraph() {
            if (currentParagraphLines.isNotEmpty()) {
                val text = currentParagraphLines.joinToString(" ").trim()
                if (text.isNotEmpty()) {
                    content.add(
                        JiraContent(
                            type = "paragraph",
                            content = listOf(JiraTextContent(type = "text", text = text))
                        )
                    )
                }
                currentParagraphLines.clear()
            }
        }

        for (line in lines) {
            when {
                line.startsWith("## ") -> {
                    flushParagraph()
                    content.add(
                        JiraContent(
                            type = "heading",
                            attrs = mapOf("level" to 2),
                            content = listOf(JiraTextContent(type = "text", text = line.substring(3).trim()))
                        )
                    )
                }
                line.startsWith("# ") -> {
                    flushParagraph()
                    content.add(
                        JiraContent(
                            type = "heading",
                            attrs = mapOf("level" to 1),
                            content = listOf(JiraTextContent(type = "text", text = line.substring(2).trim()))
                        )
                    )
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    flushParagraph()
                    // For simplicity, just add as a paragraph with bullet prefix
                    content.add(
                        JiraContent(
                            type = "paragraph",
                            content = listOf(JiraTextContent(type = "text", text = "• ${line.substring(2).trim()}"))
                        )
                    )
                }
                line.isBlank() -> {
                    flushParagraph()
                }
                else -> {
                    currentParagraphLines.add(line)
                }
            }
        }

        flushParagraph()

        return JiraDescription(content = content)
    }

    fun testConnection(): Result<Boolean> {
        val state = settings.state

        if (state.jiraUrl.isEmpty() || state.jiraUsername.isEmpty() || state.jiraApiToken.isEmpty()) {
            return Result.failure(Exception("Jira credentials not configured"))
        }

        val url = "${state.jiraUrl.trimEnd('/')}/rest/api/3/myself"
        val credentials = Credentials.basic(state.jiraUsername, state.jiraApiToken)

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", credentials)
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("Authentication failed: ${response.code} ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Connection failed: ${e.message}", e))
        }
    }
}
