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
        priority: String? = null,
        assigneeAccountId: String? = null,
        reporterAccountId: String? = null,
        parentKey: String? = null,
        epicKey: String? = null,
        sprintId: Long? = null,
        labels: List<String> = emptyList(),
        components: List<JiraComponent> = emptyList(),
        storyPoints: Double? = null,
        originalEstimate: String? = null,
        startDate: String? = null,
        dueDate: String? = null
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

        // Build fields with optional priority, assignee, reporter, parent, and epic
        val fields = JiraIssueFields(
            project = JiraProject(key = projectKeyToUse),
            summary = title,
            description = descriptionContent,
            issuetype = JiraIssueType(name = issueTypeToUse),
            assignee = assigneeAccountId?.let {
                println("Setting assignee with accountId: $it")
                JiraAssignee(accountId = it)
            },
            reporter = reporterAccountId?.let {
                println("Setting reporter with accountId: $it")
                JiraReporter(accountId = it)
            },
            priority = priority?.let {
                println("Setting priority with ID: $it")
                JiraPriority(id = it)
            },
            parent = parentKey?.let {
                println("Setting parent issue: $it")
                ParentIssueRef(key = it)
            },
            customfield_10014 = epicKey?.let {
                println("Setting epic link: $it")
                it
            },
            customfield_10020 = sprintId?.let {
                println("Setting sprint: $it")
                it
            },
            labels = if (labels.isNotEmpty()) {
                println("Setting labels: ${labels.joinToString(", ")}")
                labels
            } else null,
            components = if (components.isNotEmpty()) {
                println("Setting components: ${components.map { it.name }.joinToString(", ")}")
                components.map { ComponentReference(id = it.id) }
            } else null,
            timetracking = originalEstimate?.let {
                println("Setting time tracking: originalEstimate=$it")
                JiraTimeTracking(originalEstimate = it)
            },
            customfield_10016 = storyPoints?.let {
                println("Setting story points: $it")
                it
            },
            customfield_10015 = startDate?.let {
                println("Setting start date: $it")
                it
            },
            duedate = dueDate?.let {
                println("Setting due date: $it")
                it
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

    /**
     * 현재 사용자 정보 가져오기
     */
    fun getCurrentUser(): Result<CurrentUser> {
        val state = settings.state

        if (state.jiraUrl.isEmpty() || state.jiraUsername.isEmpty() || state.jiraApiToken.isEmpty()) {
            return Result.failure(Exception("Jira credentials not configured"))
        }

        return try {
            val credentials = Credentials.basic(state.jiraUsername, state.jiraApiToken)
            val request = Request.Builder()
                .url("${state.jiraUrl}/rest/api/3/myself")
                .header("Authorization", credentials)
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val userData = gson.fromJson(responseBody, Map::class.java)

                val currentUser = CurrentUser(
                    accountId = userData["accountId"] as String,
                    displayName = userData["displayName"] as String,
                    emailAddress = userData["emailAddress"] as? String,
                    avatarUrls = (userData["avatarUrls"] as? Map<String, String>) ?: emptyMap(),
                    active = userData["active"] as? Boolean ?: true,
                    locale = userData["locale"] as? String,
                    timeZone = userData["timeZone"] as? String
                )
                Result.success(currentUser)
            } else {
                Result.failure(Exception("Failed to get current user: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 사용자 검색
     * @param query 검색 쿼리 (이름 또는 이메일)
     * @param projectKey 프로젝트 키 (할당 가능한 사용자만 필터링)
     * @param maxResults 최대 결과 수
     */
    fun searchUsers(
        query: String,
        projectKey: String? = null,
        maxResults: Int = 50
    ): Result<List<JiraUser>> {
        val state = settings.state

        if (state.jiraUrl.isEmpty() || state.jiraUsername.isEmpty() || state.jiraApiToken.isEmpty()) {
            return Result.failure(Exception("Jira credentials not configured"))
        }

        return try {
            val credentials = Credentials.basic(state.jiraUsername, state.jiraApiToken)

            // 프로젝트가 지정된 경우 할당 가능한 사용자 검색
            val endpoint = if (projectKey != null) {
                "${state.jiraUrl}/rest/api/3/user/assignable/search?project=$projectKey&query=$query&maxResults=$maxResults"
            } else {
                "${state.jiraUrl}/rest/api/3/user/search?query=$query&maxResults=$maxResults"
            }

            val request = Request.Builder()
                .url(endpoint)
                .header("Authorization", credentials)
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val userDataArray: Array<*> = gson.fromJson(responseBody, Array::class.java)
                val users = userDataArray
                    .mapNotNull { item ->
                        val userData = item as? Map<*, *> ?: return@mapNotNull null
                        JiraUser(
                            accountId = userData["accountId"] as String,
                            displayName = userData["displayName"] as String,
                            emailAddress = userData["emailAddress"] as? String,
                            avatarUrls = (userData["avatarUrls"] as? Map<String, String>) ?: emptyMap(),
                            active = userData["active"] as? Boolean ?: true,
                            self = userData["self"] as? String
                        )
                    }
                Result.success(users)
            } else {
                Result.failure(Exception("Failed to search users: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 프로젝트에 할당 가능한 사용자 목록 가져오기
     */
    fun getAssignableUsers(
        projectKey: String,
        issueKey: String? = null
    ): Result<List<JiraUser>> {
        val state = settings.state

        if (state.jiraUrl.isEmpty() || state.jiraUsername.isEmpty() || state.jiraApiToken.isEmpty()) {
            return Result.failure(Exception("Jira credentials not configured"))
        }

        return try {
            val credentials = Credentials.basic(state.jiraUsername, state.jiraApiToken)

            val endpoint = if (issueKey != null) {
                "${state.jiraUrl}/rest/api/3/user/assignable/search?issueKey=$issueKey"
            } else {
                "${state.jiraUrl}/rest/api/3/user/assignable/search?project=$projectKey"
            }

            val request = Request.Builder()
                .url(endpoint)
                .header("Authorization", credentials)
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val userDataArray: Array<*> = gson.fromJson(responseBody, Array::class.java)
                val users = userDataArray
                    .mapNotNull { item ->
                        val userData = item as? Map<*, *> ?: return@mapNotNull null
                        JiraUser(
                            accountId = userData["accountId"] as String,
                            displayName = userData["displayName"] as String,
                            emailAddress = userData["emailAddress"] as? String,
                            avatarUrls = (userData["avatarUrls"] as? Map<String, String>) ?: emptyMap(),
                            active = userData["active"] as? Boolean ?: true,
                            self = userData["self"] as? String
                        )
                    }
                Result.success(users)
            } else {
                Result.failure(Exception("Failed to get assignable users: ${response.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 프로젝트의 모든 Epic 조회
     */
    fun getEpics(projectKey: String): Result<List<Epic>> {
        val state = settings.state

        if (state.jiraUrl.isEmpty() || state.jiraUsername.isEmpty() || state.jiraApiToken.isEmpty()) {
            return Result.failure(Exception("Jira credentials not configured"))
        }

        return try {
            val credentials = Credentials.basic(state.jiraUsername, state.jiraApiToken)

            // First, we need to find the board ID for the project
            val boardsUrl = "${state.jiraUrl}/rest/agile/1.0/board?projectKeyOrId=$projectKey"
            val boardsRequest = Request.Builder()
                .url(boardsUrl)
                .header("Authorization", credentials)
                .header("Accept", "application/json")
                .build()

            val boardsResponse = client.newCall(boardsRequest).execute()
            val boardsResponseBody = boardsResponse.body?.string()

            if (!boardsResponse.isSuccessful || boardsResponseBody == null) {
                println("No boards found, falling back to JQL search")
                return getEpicsByJQL(projectKey)
            }

            val boardsData = gson.fromJson(boardsResponseBody, Map::class.java)
            val boards = boardsData["values"] as? List<Map<String, Any>> ?: emptyList()

            if (boards.isEmpty()) {
                println("No boards found for project $projectKey, trying JQL search")
                // If no board found, try JQL query
                return getEpicsByJQL(projectKey)
            }

            val boardId = boards.first()["id"]

            // Get epics from the board
            val epicsUrl = "${state.jiraUrl}/rest/agile/1.0/board/$boardId/epic"
            val request = Request.Builder()
                .url(epicsUrl)
                .header("Authorization", credentials)
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val epicsData = gson.fromJson(responseBody, Map::class.java)
                val epicValues = epicsData["values"] as? List<Map<String, Any>> ?: emptyList()

                val epics = epicValues.map { epicData ->
                    Epic(
                        id = epicData["id"].toString(),
                        key = epicData["key"] as String,
                        self = epicData["self"] as String,
                        name = epicData["name"] as? String ?: epicData["key"] as String,
                        summary = epicData["summary"] as? String ?: "",
                        done = epicData["done"] as? Boolean ?: false,
                        color = (epicData["color"] as? Map<String, Any>)?.let {
                            EpicColor(key = it["key"] as String)
                        }
                    )
                }
                Result.success(epics)
            } else {
                // Fall back to JQL query
                getEpicsByJQL(projectKey)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * JQL을 사용한 Epic 조회 (폴백 메서드)
     */
    private fun getEpicsByJQL(projectKey: String): Result<List<Epic>> {
        val state = settings.state

        return try {
            val credentials = Credentials.basic(state.jiraUsername, state.jiraApiToken)
            val jql = "project = $projectKey AND issuetype = Epic ORDER BY created DESC"

            println("Searching epics with JQL: $jql")

            // Use the new POST endpoint with JQL in body
            val searchUrl = "${state.jiraUrl}/rest/api/3/search/jql"
            val requestBody = gson.toJson(mapOf(
                "jql" to jql,
                "fields" to listOf("summary", "status", "key", "id"),
                "maxResults" to 100
            ))

            val request = Request.Builder()
                .url(searchUrl)
                .header("Authorization", credentials)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val searchData = gson.fromJson(responseBody, Map::class.java)
                val issues = searchData["issues"] as? List<Map<String, Any>> ?: emptyList()

                println("Found ${issues.size} epics for project $projectKey")

                val epics = issues.map { issue ->
                    val fields = issue["fields"] as Map<String, Any>
                    val status = fields["status"] as? Map<String, Any>
                    val isDone = status?.get("statusCategory") as? Map<String, Any>

                    Epic(
                        id = issue["id"] as String,
                        key = issue["key"] as String,
                        self = issue["self"] as String,
                        name = fields["summary"] as String,
                        summary = fields["summary"] as String,
                        done = isDone?.get("key") == "done"
                    )
                }
                Result.success(epics)
            } else {
                println("Failed to search epics: ${response.code} - $responseBody")
                Result.failure(Exception("Failed to search epics: ${response.code}"))
            }
        } catch (e: Exception) {
            println("Exception while searching epics: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * 프로젝트의 모든 Sprint 조회
     */
    fun getSprints(projectKey: String): Result<List<Sprint>> {
        val state = settings.state

        if (state.jiraUrl.isEmpty() || state.jiraUsername.isEmpty() || state.jiraApiToken.isEmpty()) {
            return Result.failure(Exception("Jira credentials not configured"))
        }

        return try {
            val credentials = Credentials.basic(state.jiraUsername, state.jiraApiToken)

            // First, find the board ID for the project - try both scrum and kanban
            println("Looking for boards for project: $projectKey")

            // Try scrum boards first
            var boardsUrl = "${state.jiraUrl}/rest/agile/1.0/board?projectKeyOrId=$projectKey"
            var boardsRequest = Request.Builder()
                .url(boardsUrl)
                .header("Authorization", credentials)
                .header("Accept", "application/json")
                .build()

            val boardsResponse = client.newCall(boardsRequest).execute()
            val boardsResponseBody = boardsResponse.body?.string()

            if (!boardsResponse.isSuccessful || boardsResponseBody == null) {
                println("Failed to get boards: ${boardsResponse.code} - $boardsResponseBody")
                return Result.success(emptyList()) // Return empty list instead of failure
            }

            val boardsData = gson.fromJson(boardsResponseBody, Map::class.java)
            val boards = boardsData["values"] as? List<Map<String, Any>> ?: emptyList()

            println("Found ${boards.size} boards for project $projectKey")

            if (boards.isEmpty()) {
                println("No boards found for project $projectKey")
                return Result.success(emptyList())
            }

            val boardId = (boards.first()["id"] as Double).toLong()
            println("Using board ID: $boardId")

            // Get sprints from the board - include state parameter
            val sprintsUrl = "${state.jiraUrl}/rest/agile/1.0/board/$boardId/sprint?state=active,future"
            println("Fetching sprints from: $sprintsUrl")

            val request = Request.Builder()
                .url(sprintsUrl)
                .header("Authorization", credentials)
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val sprintsData = gson.fromJson(responseBody, Map::class.java)
                val sprintValues = sprintsData["values"] as? List<Map<String, Any>> ?: emptyList()

                println("Found ${sprintValues.size} sprints")

                val sprints = sprintValues.mapNotNull { sprintData ->
                    try {
                        Sprint(
                            id = (sprintData["id"] as Double).toLong(),
                            self = sprintData["self"] as String,
                            state = (sprintData["state"] as String).uppercase(),  // Convert to uppercase
                            name = sprintData["name"] as String,
                            startDate = sprintData["startDate"] as? String,
                            endDate = sprintData["endDate"] as? String,
                            completeDate = sprintData["completeDate"] as? String,
                            originBoardId = (sprintData["originBoardId"] as? Double)?.toLong(),
                            goal = sprintData["goal"] as? String
                        )
                    } catch (e: Exception) {
                        println("Error parsing sprint: ${e.message}")
                        null
                    }
                }

                // Sort sprints: ACTIVE first, then FUTURE, then CLOSED
                val sortedSprints = sprints.sortedWith(compareBy(
                    { it.state != "ACTIVE" },
                    { it.state != "FUTURE" },
                    { it.name }
                ))

                Result.success(sortedSprints)
            } else {
                println("Failed to get sprints: ${response.code} - $responseBody")
                Result.success(emptyList()) // Return empty list instead of failure
            }
        } catch (e: Exception) {
            println("Exception while getting sprints: ${e.message}")
            e.printStackTrace()
            Result.success(emptyList()) // Return empty list instead of failure
        }
    }

    /**
     * 하위작업 생성
     */
    fun createSubtask(
        parentKey: String,
        summary: String,
        description: String? = null,
        issueType: String = "Sub-task",
        assignee: String? = null,
        priority: String? = null,
        labels: List<String> = emptyList()
    ): Result<JiraIssueResponse> {
        val state = settings.state

        if (state.jiraUrl.isEmpty() || state.jiraUsername.isEmpty() || state.jiraApiToken.isEmpty()) {
            return Result.failure(Exception("Jira credentials not configured"))
        }

        val descriptionContent = description?.let { convertMarkdownToJiraFormat(it) }

        val fields = JiraIssueFields(
            project = JiraProject(key = parentKey.substringBefore("-")),
            summary = summary,
            description = descriptionContent,
            issuetype = JiraIssueType(name = issueType),
            assignee = assignee?.let { JiraAssignee(accountId = it) },
            priority = priority?.let { JiraPriority(name = it) }
        )

        val issueRequest = JiraIssueRequest(fields = fields)

        return try {
            val credentials = Credentials.basic(state.jiraUsername, state.jiraApiToken)
            val json = gson.toJson(issueRequest)
            val requestBody = json.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("${state.jiraUrl}/rest/api/3/issue")
                .header("Authorization", credentials)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val issueResponse = gson.fromJson(responseBody, JiraIssueResponse::class.java)
                Result.success(issueResponse)
            } else {
                val errorResponse = try {
                    gson.fromJson(responseBody, JiraErrorResponse::class.java)
                } catch (e: Exception) {
                    null
                }

                val errorMessage = errorResponse?.errorMessages?.joinToString(", ")
                    ?: errorResponse?.errors?.map { "${it.key}: ${it.value}" }?.joinToString(", ")
                    ?: responseBody ?: "Unknown error"

                Result.failure(Exception("Failed to create subtask: $errorMessage"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 프로젝트의 컴포넌트 목록 조회
     */
    fun getProjectComponents(projectKey: String): Result<List<JiraComponent>> {
        val state = settings.state

        if (state.jiraUrl.isEmpty() || state.jiraUsername.isEmpty() || state.jiraApiToken.isEmpty()) {
            return Result.failure(Exception("Jira credentials not configured"))
        }

        return try {
            val url = "${state.jiraUrl.trimEnd('/')}/rest/api/3/project/$projectKey/components"
            val credentials = Credentials.basic(state.jiraUsername, state.jiraApiToken)

            val request = Request.Builder()
                .url(url)
                .header("Authorization", credentials)
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val components = gson.fromJson(responseBody, Array<JiraComponent>::class.java)
                println("Found ${components.size} components for project $projectKey")
                Result.success(components.toList())
            } else {
                println("Failed to get components: ${response.code} - $responseBody")
                Result.failure(Exception("Failed to get components: ${response.code}"))
            }
        } catch (e: Exception) {
            println("Error getting components: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * 프로젝트에서 사용된 라벨 목록 조회
     * Jira API는 직접적인 프로젝트별 라벨 목록 제공하지 않으므로
     * 최근 이슈들에서 사용된 라벨을 수집
     */
    fun getProjectLabels(projectKey: String): Result<List<String>> {
        val state = settings.state

        if (state.jiraUrl.isEmpty() || state.jiraUsername.isEmpty() || state.jiraApiToken.isEmpty()) {
            return Result.failure(Exception("Jira credentials not configured"))
        }

        return try {
            // Search for recent issues in the project to collect labels
            val jql = "project = $projectKey ORDER BY created DESC"
            val searchUrl = "${state.jiraUrl}/rest/api/3/search/jql"

            val requestBody = gson.toJson(mapOf(
                "jql" to jql,
                "fields" to listOf("labels"),
                "maxResults" to 100
            ))

            val credentials = Credentials.basic(state.jiraUsername, state.jiraApiToken)
            val request = Request.Builder()
                .url(searchUrl)
                .header("Authorization", credentials)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val searchResult = gson.fromJson(responseBody, Map::class.java)
                val issues = searchResult["issues"] as? List<Map<String, Any>> ?: emptyList()

                // Collect all unique labels from issues
                val labelSet = mutableSetOf<String>()
                issues.forEach { issue ->
                    val fields = issue["fields"] as? Map<String, Any> ?: return@forEach
                    val labels = fields["labels"] as? List<String> ?: return@forEach
                    labelSet.addAll(labels)
                }

                val sortedLabels = labelSet.toList().sorted()
                println("Found ${sortedLabels.size} unique labels in project $projectKey")
                Result.success(sortedLabels)
            } else {
                println("Failed to search for labels: ${response.code}")
                Result.success(emptyList()) // Return empty list instead of error
            }
        } catch (e: Exception) {
            println("Error getting labels: ${e.message}")
            Result.success(emptyList()) // Return empty list on error
        }
    }

    /**
     * 라벨 자동완성을 위한 검색
     */
    fun searchLabels(query: String): Result<List<String>> {
        val state = settings.state

        if (state.jiraUrl.isEmpty() || state.jiraUsername.isEmpty() || state.jiraApiToken.isEmpty()) {
            return Result.failure(Exception("Jira credentials not configured"))
        }

        return try {
            val url = "${state.jiraUrl.trimEnd('/')}/rest/api/3/label/suggestions?query=$query"
            val credentials = Credentials.basic(state.jiraUsername, state.jiraApiToken)

            val request = Request.Builder()
                .url(url)
                .header("Authorization", credentials)
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val result = gson.fromJson(responseBody, Map::class.java)
                val suggestions = result["suggestions"] as? List<String> ?: emptyList()
                Result.success(suggestions)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            println("Error searching labels: ${e.message}")
            Result.success(emptyList())
        }
    }

    /**
     * 이슈 링크 타입 목록 조회
     */
    fun getIssueLinkTypes(): Result<List<IssueLinkType>> {
        val state = settings.state

        if (state.jiraUrl.isEmpty() || state.jiraUsername.isEmpty() || state.jiraApiToken.isEmpty()) {
            return Result.failure(Exception("Jira credentials not configured"))
        }

        return try {
            val url = "${state.jiraUrl.trimEnd('/')}/rest/api/3/issueLinkType"
            val credentials = Credentials.basic(state.jiraUsername, state.jiraApiToken)

            val request = Request.Builder()
                .url(url)
                .header("Authorization", credentials)
                .header("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val result = gson.fromJson(responseBody, Map::class.java)
                val issueLinkTypes = result["issueLinkTypes"] as? List<Map<String, Any>> ?: emptyList()

                val linkTypes = issueLinkTypes.map { linkType ->
                    IssueLinkType(
                        id = linkType["id"]?.toString(),
                        name = linkType["name"]?.toString() ?: "",
                        inward = linkType["inward"]?.toString() ?: "",
                        outward = linkType["outward"]?.toString() ?: "",
                        self = linkType["self"]?.toString()
                    )
                }

                println("Found ${linkTypes.size} issue link types")
                Result.success(linkTypes)
            } else {
                println("Failed to get issue link types: ${response.code}")
                Result.failure(Exception("Failed to get issue link types"))
            }
        } catch (e: Exception) {
            println("Error getting issue link types: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * 이슈 검색 (링크할 이슈 찾기용)
     */
    fun searchIssuesForLinking(projectKey: String, searchText: String): Result<List<LinkedIssue>> {
        val state = settings.state

        if (state.jiraUrl.isEmpty() || state.jiraUsername.isEmpty() || state.jiraApiToken.isEmpty()) {
            return Result.failure(Exception("Jira credentials not configured"))
        }

        return try {
            val jql = if (searchText.matches(Regex("^[A-Z]+-\\d+$"))) {
                // If it's an issue key format, search directly
                "key = $searchText"
            } else {
                // Otherwise search in summary and project
                "project = $projectKey AND (summary ~ \"$searchText*\" OR key = \"$searchText\") ORDER BY created DESC"
            }

            val searchUrl = "${state.jiraUrl}/rest/api/3/search/jql"
            val requestBody = gson.toJson(mapOf(
                "jql" to jql,
                "fields" to listOf("summary", "status", "issuetype", "priority"),
                "maxResults" to 20
            ))

            val credentials = Credentials.basic(state.jiraUsername, state.jiraApiToken)
            val request = Request.Builder()
                .url(searchUrl)
                .header("Authorization", credentials)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val searchResult = gson.fromJson(responseBody, Map::class.java)
                val issues = searchResult["issues"] as? List<Map<String, Any>> ?: emptyList()

                val linkedIssues = issues.map { issue ->
                    val fields = issue["fields"] as? Map<String, Any> ?: emptyMap()
                    LinkedIssue(
                        id = issue["id"] as String,
                        key = issue["key"] as String,
                        self = issue["self"] as String,
                        fields = LinkedIssueFields(
                            summary = fields["summary"] as? String,
                            status = (fields["status"] as? Map<String, Any>)?.let {
                                IssueStatus(
                                    name = it["name"] as? String ?: "",
                                    id = it["id"] as? String ?: ""
                                )
                            },
                            issuetype = (fields["issuetype"] as? Map<String, Any>)?.let {
                                JiraIssueType(name = it["name"] as? String ?: "")
                            },
                            priority = (fields["priority"] as? Map<String, Any>)?.let {
                                JiraPriority(
                                    id = it["id"] as? String,
                                    name = it["name"] as? String
                                )
                            }
                        )
                    )
                }

                println("Found ${linkedIssues.size} issues for linking")
                Result.success(linkedIssues)
            } else {
                println("Failed to search issues for linking: ${response.code}")
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            println("Error searching issues for linking: ${e.message}")
            Result.success(emptyList())
        }
    }

    /**
     * 이슈 링크 생성
     */
    fun createIssueLink(
        inwardIssue: String,
        outwardIssue: String,
        linkType: String,
        comment: String? = null
    ): Result<Unit> {
        val state = settings.state

        if (state.jiraUrl.isEmpty() || state.jiraUsername.isEmpty() || state.jiraApiToken.isEmpty()) {
            return Result.failure(Exception("Jira credentials not configured"))
        }

        return try {
            val linkRequest = CreateIssueLinkRequest(
                type = IssueLinkTypeRef(name = linkType),
                inwardIssue = IssueRef(key = inwardIssue),
                outwardIssue = IssueRef(key = outwardIssue),
                comment = comment?.let { LinkComment(body = it) }
            )

            val json = gson.toJson(linkRequest)
            val requestBody = json.toRequestBody("application/json".toMediaType())

            val url = "${state.jiraUrl.trimEnd('/')}/rest/api/3/issueLink"
            val credentials = Credentials.basic(state.jiraUsername, state.jiraApiToken)

            val request = Request.Builder()
                .url(url)
                .header("Authorization", credentials)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                println("Successfully created link between $inwardIssue and $outwardIssue")
                Result.success(Unit)
            } else {
                val responseBody = response.body?.string()
                println("Failed to create issue link: ${response.code} - $responseBody")
                Result.failure(Exception("Failed to create issue link: ${response.code}"))
            }
        } catch (e: Exception) {
            println("Error creating issue link: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Get recent issues assigned to the current user
     * @param maxResults Maximum number of issues to return (default 10)
     * @return Result containing list of recent issues
     */
    fun getRecentAssignedIssues(maxResults: Int = 10): Result<List<RecentIssue>> {
        val state = settings.state

        try {
            // JQL query to get issues assigned to current user, ordered by update date
            val jql = "assignee = currentUser() AND status != Done AND status != Closed ORDER BY updated DESC"
            val url = "${state.jiraUrl}/rest/api/3/search/jql?jql=${java.net.URLEncoder.encode(jql, "UTF-8")}&maxResults=$maxResults&fields=key,summary,status,created,issuetype,project,priority"

            val request = Request.Builder()
                .url(url)
                .header("Authorization", Credentials.basic(state.jiraUsername, state.jiraApiToken))
                .header("Accept", "application/json")
                .get()
                .build()

            println("Fetching issues assigned to current user")

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val jsonResponse = gson.fromJson(responseBody, Map::class.java) as Map<*, *>
                val issues = (jsonResponse["issues"] as? List<Map<String, Any>>) ?: emptyList()

                val recentIssues = issues.mapNotNull { issue ->
                    try {
                        val key = issue["key"] as? String ?: return@mapNotNull null
                        val fields = issue["fields"] as? Map<String, Any> ?: return@mapNotNull null
                        val summary = fields["summary"] as? String ?: ""
                        val created = fields["created"] as? String ?: ""

                        val status = fields["status"] as? Map<String, Any>
                        val statusName = status?.get("name") as? String ?: "Unknown"

                        val issueType = fields["issuetype"] as? Map<String, Any>
                        val issueTypeName = issueType?.get("name") as? String ?: "Task"

                        val project = fields["project"] as? Map<String, Any>
                        val projectKey = project?.get("key") as? String ?: ""
                        val projectName = project?.get("name") as? String ?: ""

                        val priority = fields["priority"] as? Map<String, Any>
                        val priorityName = priority?.get("name") as? String

                        RecentIssue(
                            key = key,
                            summary = summary,
                            status = statusName,
                            created = created,
                            issueType = issueTypeName,
                            projectKey = projectKey,
                            projectName = projectName,
                            url = "${state.jiraUrl}/browse/$key",
                            priority = priorityName
                        )
                    } catch (e: Exception) {
                        println("Error parsing issue: ${e.message}")
                        null
                    }
                }

                println("Successfully fetched ${recentIssues.size} assigned issues")
                return Result.success(recentIssues)
            } else {
                println("Failed to fetch assigned issues: ${response.code} - $responseBody")
                return Result.failure(Exception("Failed to fetch assigned issues: ${response.code}"))
            }
        } catch (e: Exception) {
            println("Error fetching assigned issues: ${e.message}")
            e.printStackTrace()
            return Result.failure(e)
        }
    }

    /**
     * Get recent issues created by the current user
     * @param maxResults Maximum number of issues to return (default 10)
     * @return Result containing list of recent issues
     */
    fun getRecentCreatedIssues(maxResults: Int = 10): Result<List<RecentIssue>> {
        val state = settings.state

        try {
            // Get current user first
            val currentUserResult = getCurrentUser()
            if (currentUserResult.isFailure) {
                return Result.failure(currentUserResult.exceptionOrNull() ?: Exception("Failed to get current user"))
            }

            val currentUser = currentUserResult.getOrNull()
            val userEmail = currentUser?.emailAddress

            // JQL query to get issues created by current user, ordered by creation date
            val jql = "reporter = currentUser() ORDER BY created DESC"
            // Use the new API endpoint as /rest/api/3/search is deprecated
            val url = "${state.jiraUrl}/rest/api/3/search/jql?jql=${java.net.URLEncoder.encode(jql, "UTF-8")}&maxResults=$maxResults&fields=key,summary,status,created,issuetype,project,priority"

            val request = Request.Builder()
                .url(url)
                .header("Authorization", Credentials.basic(state.jiraUsername, state.jiraApiToken))
                .header("Accept", "application/json")
                .get()
                .build()

            println("Fetching recent issues for current user: $userEmail")

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val jsonResponse = gson.fromJson(responseBody, Map::class.java) as Map<*, *>
                val issues = (jsonResponse["issues"] as? List<Map<String, Any>>) ?: emptyList()

                val recentIssues = issues.mapNotNull { issue ->
                    try {
                        val key = issue["key"] as? String ?: return@mapNotNull null
                        val fields = issue["fields"] as? Map<String, Any> ?: return@mapNotNull null
                        val summary = fields["summary"] as? String ?: ""
                        val created = fields["created"] as? String ?: ""

                        val status = fields["status"] as? Map<String, Any>
                        val statusName = status?.get("name") as? String ?: "Unknown"

                        val issueType = fields["issuetype"] as? Map<String, Any>
                        val issueTypeName = issueType?.get("name") as? String ?: "Task"

                        val project = fields["project"] as? Map<String, Any>
                        val projectKey = project?.get("key") as? String ?: ""
                        val projectName = project?.get("name") as? String ?: ""

                        val priority = fields["priority"] as? Map<String, Any>
                        val priorityName = priority?.get("name") as? String

                        RecentIssue(
                            key = key,
                            summary = summary,
                            status = statusName,
                            created = created,
                            issueType = issueTypeName,
                            projectKey = projectKey,
                            projectName = projectName,
                            url = "${state.jiraUrl}/browse/$key",
                            priority = priorityName
                        )
                    } catch (e: Exception) {
                        println("Error parsing issue: ${e.message}")
                        null
                    }
                }

                println("Successfully fetched ${recentIssues.size} recent issues")
                return Result.success(recentIssues)
            } else {
                println("Failed to fetch recent issues: ${response.code} - $responseBody")
                return Result.failure(Exception("Failed to fetch recent issues: ${response.code}"))
            }
        } catch (e: Exception) {
            println("Error fetching recent issues: ${e.message}")
            e.printStackTrace()
            return Result.failure(e)
        }
    }

    /**
     * Search for issues by text (summary or key)
     * @param query Search query
     * @param projectKey Optional project key to limit search
     * @param maxResults Maximum number of results
     * @return Result containing list of matching issues
     */
    fun searchIssues(query: String, projectKey: String? = null, maxResults: Int = 20): Result<List<ParentIssue>> {
        val state = settings.state

        if (query.isBlank() || query.length < 2) {
            return Result.success(emptyList())
        }

        try {
            // Build JQL query - search in summary or exact key match
            val jql = if (projectKey != null) {
                "project = $projectKey AND (summary ~ \"$query*\" OR key = \"$query\") ORDER BY updated DESC"
            } else {
                "(summary ~ \"$query*\" OR key = \"$query\") ORDER BY updated DESC"
            }

            val url = "${state.jiraUrl}/rest/api/3/search/jql?jql=${java.net.URLEncoder.encode(jql, "UTF-8")}&maxResults=$maxResults&fields=id,key,summary,project,issuetype,status"

            val request = Request.Builder()
                .url(url)
                .header("Authorization", Credentials.basic(state.jiraUsername, state.jiraApiToken))
                .header("Accept", "application/json")
                .get()
                .build()

            println("Searching issues with query: $query")

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val jsonResponse = gson.fromJson(responseBody, Map::class.java) as Map<*, *>
                val issues = (jsonResponse["issues"] as? List<Map<String, Any>>) ?: emptyList()

                val results = issues.mapNotNull { issue ->
                    try {
                        val key = issue["key"] as? String ?: return@mapNotNull null
                        val id = issue["id"] as? String ?: return@mapNotNull null
                        val fields = issue["fields"] as? Map<String, Any> ?: return@mapNotNull null
                        val summary = fields["summary"] as? String ?: ""

                        val project = fields["project"] as? Map<String, Any>
                        val projectKeyValue = project?.get("key") as? String ?: ""

                        val issueType = fields["issuetype"] as? Map<String, Any>
                        val issueTypeName = issueType?.get("name") as? String ?: ""

                        val status = fields["status"] as? Map<String, Any>
                        val statusName = status?.get("name") as? String ?: ""

                        ParentIssue(
                            key = key,
                            id = id,
                            summary = summary,
                            projectKey = projectKeyValue,
                            issueType = issueTypeName,
                            status = statusName
                        )
                    } catch (e: Exception) {
                        println("Error parsing search result: ${e.message}")
                        null
                    }
                }

                println("Found ${results.size} matching issues")
                return Result.success(results)
            } else {
                println("Failed to search issues: ${response.code} - $responseBody")
                return Result.failure(Exception("Failed to search issues: ${response.code}"))
            }
        } catch (e: Exception) {
            println("Error searching issues: ${e.message}")
            e.printStackTrace()
            return Result.failure(e)
        }
    }

    /**
     * Validate and get parent issue details
     * @param parentKey The parent issue key (e.g., "PROJECT-123")
     * @return Result containing ParentIssue details
     */
    fun validateParentIssue(parentKey: String): Result<ParentIssue> {
        val state = settings.state

        if (parentKey.isBlank()) {
            return Result.failure(Exception("Parent issue key cannot be empty"))
        }

        try {
            // Include Epic Link field in query
            val url = "${state.jiraUrl}/rest/api/3/issue/$parentKey?fields=id,key,summary,project,issuetype,status,customfield_10014"

            val request = Request.Builder()
                .url(url)
                .header("Authorization", Credentials.basic(state.jiraUsername, state.jiraApiToken))
                .header("Accept", "application/json")
                .get()
                .build()

            println("Validating parent issue: $parentKey")

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val jsonResponse = gson.fromJson(responseBody, Map::class.java) as Map<*, *>
                val key = jsonResponse["key"] as? String ?: return Result.failure(Exception("Invalid issue key"))
                val id = jsonResponse["id"] as? String ?: return Result.failure(Exception("Invalid issue id"))

                val fields = jsonResponse["fields"] as? Map<String, Any> ?: return Result.failure(Exception("Invalid fields"))
                val summary = fields["summary"] as? String ?: ""

                val project = fields["project"] as? Map<String, Any>
                val projectKey = project?.get("key") as? String ?: ""

                val issueType = fields["issuetype"] as? Map<String, Any>
                val issueTypeName = issueType?.get("name") as? String ?: ""

                val status = fields["status"] as? Map<String, Any>
                val statusName = status?.get("name") as? String ?: ""

                // Get Epic Link if exists
                val epicKey = fields["customfield_10014"] as? String

                val parentIssue = ParentIssue(
                    key = key,
                    id = id,
                    summary = summary,
                    projectKey = projectKey,
                    issueType = issueTypeName,
                    status = statusName,
                    epicKey = epicKey
                )

                println("Parent issue validated: $key - $summary (Project: $projectKey, Epic: ${epicKey ?: "None"})")
                return Result.success(parentIssue)
            } else if (response.code == 404) {
                println("Parent issue not found: $parentKey")
                return Result.failure(Exception("Issue '$parentKey' not found"))
            } else {
                println("Failed to validate parent issue: ${response.code} - $responseBody")
                return Result.failure(Exception("Failed to validate parent issue: ${response.code}"))
            }
        } catch (e: Exception) {
            println("Error validating parent issue: ${e.message}")
            e.printStackTrace()
            return Result.failure(e)
        }
    }
}
