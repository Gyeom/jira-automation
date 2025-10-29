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

        // Build fields with optional priority
        val fields = JiraIssueFields(
            project = JiraProject(key = projectKeyToUse),
            summary = title,
            description = descriptionContent,
            issuetype = JiraIssueType(name = issueTypeToUse),
            priority = priority?.let {
                println("Setting priority with ID: $it")
                JiraPriority(id = it)
            }
        )

        val issueRequest = JiraIssueRequest(fields = fields)

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

    fun getProjects(): Result<List<JiraProjectListResponse>> {
        val state = settings.state

        if (state.jiraUrl.isEmpty() || state.jiraUsername.isEmpty() || state.jiraApiToken.isEmpty()) {
            return Result.failure(Exception("Jira credentials not configured"))
        }

        // Limit the number of projects to avoid performance issues
        // Use expand=description to get more info if needed
        // Use orderBy=-lastIssueUpdatedTime to get recently used projects first
        val url = "${state.jiraUrl.trimEnd('/')}/rest/api/3/project?orderBy=-lastIssueUpdatedTime&maxResults=50"
        val credentials = Credentials.basic(state.jiraUsername, state.jiraApiToken)

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", credentials)
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val projects = gson.fromJson(responseBody, Array<JiraProjectListResponse>::class.java).toList()
                    println("Successfully fetched ${projects.size} projects")

                    // Sort by key for easier navigation if not already sorted
                    val sortedProjects = projects.sortedBy { it.key }
                    Result.success(sortedProjects)
                } else {
                    println("Failed to fetch projects. Response code: ${response.code}")
                    println("Response body: $responseBody")
                    Result.failure(Exception("Failed to fetch projects: ${response.code} ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Failed to connect to Jira: ${e.message}", e))
        }
    }

    fun searchProjects(query: String): Result<List<JiraProjectListResponse>> {
        val state = settings.state

        if (state.jiraUrl.isEmpty() || state.jiraUsername.isEmpty() || state.jiraApiToken.isEmpty()) {
            return Result.failure(Exception("Jira credentials not configured"))
        }

        // Search for projects by key or name
        val url = "${state.jiraUrl.trimEnd('/')}/rest/api/3/project/search?query=$query&maxResults=20"
        val credentials = Credentials.basic(state.jiraUsername, state.jiraApiToken)

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", credentials)
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    // Parse search response which has a 'values' field
                    val searchResponse = gson.fromJson(responseBody, Map::class.java)
                    val values = searchResponse["values"] as? List<Map<String, Any>> ?: emptyList()

                    val projects = values.map { projectMap ->
                        JiraProjectListResponse(
                            id = projectMap["id"]?.toString() ?: "",
                            key = projectMap["key"]?.toString() ?: "",
                            name = projectMap["name"]?.toString() ?: "",
                            projectTypeKey = projectMap["projectTypeKey"]?.toString()
                        )
                    }

                    println("Successfully searched and found ${projects.size} projects")
                    Result.success(projects)
                } else {
                    println("Failed to search projects. Response code: ${response.code}")
                    Result.failure(Exception("Failed to search projects: ${response.code} ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Failed to connect to Jira: ${e.message}", e))
        }
    }

    fun getIssueTypesForProject(projectKey: String): Result<List<JiraIssueTypeResponse>> {
        val state = settings.state

        if (state.jiraUrl.isEmpty() || state.jiraUsername.isEmpty() || state.jiraApiToken.isEmpty()) {
            return Result.failure(Exception("Jira credentials not configured"))
        }

        val url = "${state.jiraUrl.trimEnd('/')}/rest/api/3/project/$projectKey"
        val credentials = Credentials.basic(state.jiraUsername, state.jiraApiToken)

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", credentials)
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val projectResponse = gson.fromJson(responseBody, Map::class.java)
                    val issueTypes = projectResponse["issueTypes"] as? List<Map<String, Any>> ?: emptyList()

                    val issueTypeResponses = issueTypes.map { issueType ->
                        JiraIssueTypeResponse(
                            self = issueType["self"] as? String,
                            id = issueType["id"]?.toString() ?: "",
                            name = issueType["name"]?.toString() ?: "",
                            description = issueType["description"] as? String,
                            iconUrl = issueType["iconUrl"] as? String,
                            subtask = issueType["subtask"] as? Boolean ?: false
                        )
                    }

                    println("Successfully fetched ${issueTypeResponses.size} issue types for project $projectKey")
                    Result.success(issueTypeResponses)
                } else {
                    println("Failed to fetch issue types. Response code: ${response.code}")
                    println("Response body: $responseBody")
                    Result.failure(Exception("Failed to fetch issue types: ${response.code} ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Failed to connect to Jira: ${e.message}", e))
        }
    }

    fun getProjectPriorities(projectKey: String): Result<List<JiraPriorityResponse>> {
        val state = settings.state

        if (state.jiraUrl.isEmpty() || state.jiraUsername.isEmpty() || state.jiraApiToken.isEmpty()) {
            return Result.failure(Exception("Jira credentials not configured"))
        }

        // Get priorities for a specific project using createmeta endpoint
        val url = "${state.jiraUrl.trimEnd('/')}/rest/api/3/issue/createmeta?projectKeys=$projectKey&expand=projects.issuetypes.fields"
        val credentials = Credentials.basic(state.jiraUsername, state.jiraApiToken)

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", credentials)
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val createMeta = gson.fromJson(responseBody, Map::class.java)
                    val projects = createMeta["projects"] as? List<Map<String, Any>> ?: emptyList()

                    if (projects.isNotEmpty()) {
                        val project = projects[0]
                        val issueTypes = project["issuetypes"] as? List<Map<String, Any>> ?: emptyList()

                        // Extract priorities from the first issue type's fields
                        if (issueTypes.isNotEmpty()) {
                            val fields = issueTypes[0]["fields"] as? Map<String, Any> ?: emptyMap()
                            val priorityField = fields["priority"] as? Map<String, Any> ?: emptyMap()
                            val allowedValues = priorityField["allowedValues"] as? List<Map<String, Any>> ?: emptyList()

                            val priorities = allowedValues.map { priority ->
                                JiraPriorityResponse(
                                    id = priority["id"]?.toString() ?: "",
                                    name = priority["name"]?.toString() ?: "",
                                    description = priority["description"]?.toString(),
                                    iconUrl = priority["iconUrl"]?.toString()
                                )
                            }

                            if (priorities.isNotEmpty()) {
                                println("Found ${priorities.size} project-specific priorities for $projectKey")
                                priorities.forEach { priority ->
                                    println("Project Priority: id=${priority.id}, name=${priority.name}")
                                }
                                return Result.success(priorities)
                            }
                        }
                    }

                    // Fall back to general priorities if no project-specific ones found
                    println("No project-specific priorities found, falling back to general priorities")
                    return getPriorities()
                } else {
                    // Fall back to general priorities if request fails
                    return getPriorities()
                }
            }
        } catch (e: Exception) {
            // Fall back to general priorities on error
            println("Error getting project priorities: ${e.message}")
            return getPriorities()
        }
    }

    fun getPriorities(): Result<List<JiraPriorityResponse>> {
        val state = settings.state

        if (state.jiraUrl.isEmpty() || state.jiraUsername.isEmpty() || state.jiraApiToken.isEmpty()) {
            return Result.failure(Exception("Jira credentials not configured"))
        }

        val url = "${state.jiraUrl.trimEnd('/')}/rest/api/3/priority"
        val credentials = Credentials.basic(state.jiraUsername, state.jiraApiToken)

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", credentials)
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val priorities = gson.fromJson(responseBody, Array<JiraPriorityResponse>::class.java).toList()
                    println("Successfully fetched ${priorities.size} priorities")
                    // Debug: Log all priority IDs and names
                    priorities.forEach { priority ->
                        println("Priority: id=${priority.id}, name=${priority.name}")
                    }
                    Result.success(priorities)
                } else {
                    println("Failed to fetch priorities. Response code: ${response.code}")
                    println("Response body: $responseBody")
                    Result.failure(Exception("Failed to fetch priorities: ${response.code} ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Failed to connect to Jira: ${e.message}", e))
        }
    }
}
