package com.github.gyeom.jiraautomation.services

import com.github.gyeom.jiraautomation.model.*
import com.github.gyeom.jiraautomation.settings.JiraSettingsState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.vcs.log.VcsCommitMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 하위작업 관리 서비스
 */
@Service(Service.Level.PROJECT)
class SubtaskService(private val project: Project) {

    private val jiraApiService = project.service<JiraApiService>()
    private val settings = JiraSettingsState.getInstance(project)
    private val templateService = project.service<TemplateService>()

    /**
     * 커밋 변경사항을 분석하여 하위작업 제안
     */
    suspend fun analyzeCommitsForSubtasks(
        commits: List<VcsCommitMetadata>
    ): List<CommitAnalysisResult> = withContext(Dispatchers.Default) {
        commits.map { commit ->
            analyzeCommit(commit)
        }
    }

    /**
     * 단일 커밋 분석
     */
    private fun analyzeCommit(commit: VcsCommitMetadata): CommitAnalysisResult {
        val suggestions = mutableListOf<SubtaskSuggestion>()

        // 1. 커밋 메시지 패턴 분석
        val messageSuggestions = analyzeCommitMessage(commit.fullMessage)
        suggestions.addAll(messageSuggestions)

        // 2. 파일 변경 패턴 분석
        val filePatternSuggestions = analyzeFilePatterns(commit)
        suggestions.addAll(filePatternSuggestions)

        // 3. 템플릿 매칭
        val templateSuggestions = matchTemplates(commit)
        suggestions.addAll(templateSuggestions)

        return CommitAnalysisResult(
            commitHash = commit.id.toShortString(),
            message = commit.fullMessage,
            author = commit.author.name,
            timestamp = commit.timestamp,
            filesChanged = getChangedFiles(commit),
            suggestedSubtasks = suggestions.distinctBy { it.title }
        )
    }

    /**
     * 커밋 메시지 분석
     */
    private fun analyzeCommitMessage(message: String): List<SubtaskSuggestion> {
        val suggestions = mutableListOf<SubtaskSuggestion>()

        // 일반적인 패턴 매칭
        val patterns = mapOf(
            "feat:" to "새 기능 구현",
            "fix:" to "버그 수정",
            "test:" to "테스트 추가",
            "docs:" to "문서 업데이트",
            "refactor:" to "코드 리팩토링",
            "style:" to "코드 스타일 수정",
            "perf:" to "성능 개선",
            "chore:" to "빌드/설정 변경"
        )

        patterns.forEach { (prefix, taskType) ->
            if (message.lowercase().startsWith(prefix)) {
                val title = message.substringAfter(prefix).trim()
                suggestions.add(
                    SubtaskSuggestion(
                        title = "[$taskType] $title",
                        description = "커밋 메시지: $message",
                        confidence = 0.9f,
                        reason = "커밋 타입 '$prefix'에 기반한 제안",
                        basedOn = SuggestionSource.COMMIT_MESSAGE
                    )
                )

                // 추가 하위작업 제안
                when(prefix) {
                    "feat:" -> {
                        suggestions.add(createTestSuggestion(title))
                        suggestions.add(createDocumentationSuggestion(title))
                    }
                    "fix:" -> {
                        suggestions.add(createTestSuggestion(title, isBugFix = true))
                    }
                }
            }
        }

        // TODO, FIXME 패턴 검색
        val todoPattern = Regex("(TODO|FIXME|HACK|NOTE):\\s*(.+)", RegexOption.IGNORE_CASE)
        todoPattern.findAll(message).forEach { match ->
            val type = match.groupValues[1]
            val content = match.groupValues[2]
            suggestions.add(
                SubtaskSuggestion(
                    title = "[$type] $content",
                    description = "커밋 메시지에서 발견된 $type 항목",
                    confidence = 0.85f,
                    reason = "$type 마커 발견",
                    basedOn = SuggestionSource.COMMIT_MESSAGE
                )
            )
        }

        return suggestions
    }

    /**
     * 파일 패턴 분석
     */
    private fun analyzeFilePatterns(commit: VcsCommitMetadata): List<SubtaskSuggestion> {
        val suggestions = mutableListOf<SubtaskSuggestion>()
        val changedFiles = getChangedFiles(commit)

        // 파일 타입별 분석
        val hasTestFiles = changedFiles.any { it.contains("test", ignoreCase = true) }
        val hasConfigFiles = changedFiles.any {
            it.endsWith(".yml") || it.endsWith(".yaml") ||
            it.endsWith(".properties") || it.endsWith(".json")
        }
        val hasMigrationFiles = changedFiles.any { it.contains("migration", ignoreCase = true) }
        val hasUIFiles = changedFiles.any {
            it.endsWith(".html") || it.endsWith(".css") ||
            it.endsWith(".tsx") || it.endsWith(".jsx")
        }

        if (!hasTestFiles && changedFiles.size > 2) {
            suggestions.add(
                SubtaskSuggestion(
                    title = "테스트 코드 작성",
                    description = "변경된 ${changedFiles.size}개 파일에 대한 테스트 추가",
                    confidence = 0.7f,
                    reason = "테스트 파일이 포함되지 않은 큰 변경",
                    basedOn = SuggestionSource.FILE_PATTERN
                )
            )
        }

        if (hasConfigFiles) {
            suggestions.add(
                SubtaskSuggestion(
                    title = "설정 검증 및 문서화",
                    description = "변경된 설정 파일 검증 및 관련 문서 업데이트",
                    confidence = 0.6f,
                    reason = "설정 파일 변경 감지",
                    basedOn = SuggestionSource.FILE_PATTERN
                )
            )
        }

        if (hasMigrationFiles) {
            suggestions.add(
                SubtaskSuggestion(
                    title = "데이터베이스 마이그레이션 테스트",
                    description = "마이그레이션 스크립트 실행 및 롤백 테스트",
                    confidence = 0.8f,
                    reason = "마이그레이션 파일 변경 감지",
                    basedOn = SuggestionSource.FILE_PATTERN
                )
            )
        }

        if (hasUIFiles) {
            suggestions.add(
                SubtaskSuggestion(
                    title = "UI/UX 검증",
                    description = "변경된 UI 컴포넌트의 시각적 검증 및 접근성 테스트",
                    confidence = 0.65f,
                    reason = "UI 파일 변경 감지",
                    basedOn = SuggestionSource.FILE_PATTERN
                )
            )
        }

        return suggestions
    }

    /**
     * 템플릿 매칭
     */
    private fun matchTemplates(commit: VcsCommitMetadata): List<SubtaskSuggestion> {
        val suggestions = mutableListOf<SubtaskSuggestion>()
        val templates = templateService.getActiveTemplates()

        templates.forEach { template ->
            if (template.pattern != null && template.pattern.containsMatchIn(commit.fullMessage)) {
                template.subtasks.forEach { subtaskDef ->
                    suggestions.add(
                        SubtaskSuggestion(
                            title = subtaskDef.title,
                            description = subtaskDef.description ?: "템플릿 기반 하위작업",
                            confidence = 0.95f,
                            reason = "'${template.name}' 템플릿 매칭",
                            basedOn = SuggestionSource.TEMPLATE
                        )
                    )
                }
            }
        }

        return suggestions
    }

    /**
     * 하위작업 일괄 생성
     */
    suspend fun createSubtasks(
        parentIssueKey: String,
        subtasks: List<SubtaskData>
    ): Result<BatchSubtaskResult> = withContext(Dispatchers.IO) {
        try {
            val createdSubtasks = mutableListOf<CreatedSubtask>()
            val errors = mutableListOf<SubtaskCreationError>()

            subtasks.forEach { subtaskData ->
                try {
                    val result = createSingleSubtask(parentIssueKey, subtaskData)
                    result.onSuccess { created ->
                        createdSubtasks.add(created)
                    }.onFailure { error ->
                        errors.add(
                            SubtaskCreationError(
                                subtaskTitle = subtaskData.title,
                                errorMessage = error.message ?: "Unknown error"
                            )
                        )
                    }
                } catch (e: Exception) {
                    errors.add(
                        SubtaskCreationError(
                            subtaskTitle = subtaskData.title,
                            errorMessage = e.message ?: "Unknown error"
                        )
                    )
                }
            }

            Result.success(
                BatchSubtaskResult(
                    parentIssueKey = parentIssueKey,
                    totalRequested = subtasks.size,
                    successCount = createdSubtasks.size,
                    failedCount = errors.size,
                    createdSubtasks = createdSubtasks,
                    errors = errors
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 단일 하위작업 생성
     */
    private suspend fun createSingleSubtask(
        parentIssueKey: String,
        subtaskData: SubtaskData
    ): Result<CreatedSubtask> {
        // JiraApiService를 통해 실제 Jira API 호출
        // 여기서는 구조만 제공
        return try {
            // TODO: 실제 API 호출 구현
            val createdIssue = jiraApiService.createSubtask(
                parentKey = parentIssueKey,
                summary = subtaskData.title,
                description = subtaskData.description,
                issueType = subtaskData.issueType,
                assignee = subtaskData.assignee,
                priority = subtaskData.priority,
                labels = subtaskData.labels
            )

            createdIssue.map { issue ->
                CreatedSubtask(
                    key = issue.key,
                    title = subtaskData.title,
                    url = "${settings.state.jiraUrl}/browse/${issue.key}"
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 테스트 하위작업 제안 생성
     */
    private fun createTestSuggestion(feature: String, isBugFix: Boolean = false): SubtaskSuggestion {
        val title = if (isBugFix) {
            "회귀 테스트 추가: $feature"
        } else {
            "테스트 코드 작성: $feature"
        }

        return SubtaskSuggestion(
            title = title,
            description = if (isBugFix) {
                "버그 재발 방지를 위한 테스트 케이스 추가"
            } else {
                "새 기능에 대한 단위 테스트 및 통합 테스트 작성"
            },
            confidence = 0.75f,
            reason = "기능 변경에 따른 테스트 필요",
            basedOn = SuggestionSource.CODE_ANALYSIS
        )
    }

    /**
     * 문서화 하위작업 제안 생성
     */
    private fun createDocumentationSuggestion(feature: String): SubtaskSuggestion {
        return SubtaskSuggestion(
            title = "문서 업데이트: $feature",
            description = "API 문서, 사용자 가이드, README 업데이트",
            confidence = 0.65f,
            reason = "새 기능 추가에 따른 문서화 필요",
            basedOn = SuggestionSource.CODE_ANALYSIS
        )
    }

    /**
     * 변경된 파일 목록 추출
     */
    private fun getChangedFiles(commit: VcsCommitMetadata): List<String> {
        // VcsCommitMetadata에서 파일 목록 추출
        // 실제 구현은 VCS 통합 API 사용
        return emptyList() // TODO: 실제 구현
    }
}