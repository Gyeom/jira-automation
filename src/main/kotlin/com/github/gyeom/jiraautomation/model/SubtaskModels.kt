package com.github.gyeom.jiraautomation.model

/**
 * 하위작업 템플릿 모델
 */
data class SubtaskTemplate(
    val id: String,
    val name: String,
    val description: String,
    val projectKey: String? = null,  // null이면 모든 프로젝트에 적용
    val issueType: String,           // Epic, Story, Task 등
    val pattern: Regex? = null,       // 커밋 메시지 패턴 매칭
    val subtasks: List<SubtaskDefinition>,
    val autoApply: Boolean = false   // 자동 적용 여부
)

/**
 * 하위작업 정의
 */
data class SubtaskDefinition(
    val title: String,
    val description: String? = null,
    val assignee: AssigneeType = AssigneeType.INHERIT,
    val estimatedHours: Float? = null,
    val labels: List<String> = emptyList(),
    val priority: String? = null,    // High, Medium, Low
    val order: Int = 0               // 표시 순서
)

/**
 * 담당자 할당 타입
 */
enum class AssigneeType {
    INHERIT,        // 부모 티켓과 동일
    CURRENT_USER,   // 현재 사용자
    UNASSIGNED,     // 미할당
    SPECIFIC        // 특정 사용자 (별도 필드 필요)
}

/**
 * 하위작업 생성 요청
 */
data class SubtaskCreationRequest(
    val parentIssueKey: String,
    val template: SubtaskTemplate? = null,
    val subtasks: List<SubtaskData>,
    val linkType: String = "Sub-task"  // Sub-task, Blocks, Relates to 등
)

/**
 * 개별 하위작업 데이터
 */
data class SubtaskData(
    val title: String,
    val description: String,
    val issueType: String = "Sub-task",
    val assignee: String? = null,
    val estimatedHours: Float? = null,
    val labels: List<String> = emptyList(),
    val priority: String = "Medium",
    val components: List<String> = emptyList()
)

/**
 * 커밋 분석 결과
 */
data class CommitAnalysisResult(
    val commitHash: String,
    val message: String,
    val author: String,
    val timestamp: Long,
    val filesChanged: List<String>,
    val suggestedSubtasks: List<SubtaskSuggestion>
)

/**
 * 하위작업 제안
 */
data class SubtaskSuggestion(
    val title: String,
    val description: String,
    val confidence: Float,     // 0.0 ~ 1.0
    val reason: String,         // 제안 이유
    val basedOn: SuggestionSource
)

/**
 * 제안 근거
 */
enum class SuggestionSource {
    COMMIT_MESSAGE,     // 커밋 메시지 분석
    FILE_PATTERN,       // 파일 패턴 매칭
    CODE_ANALYSIS,      // 코드 변경 분석
    TEMPLATE,          // 템플릿 기반
    AI_SUGGESTION      // AI 제안
}

/**
 * 하위작업 일괄 생성 결과
 */
data class BatchSubtaskResult(
    val parentIssueKey: String,
    val totalRequested: Int,
    val successCount: Int,
    val failedCount: Int,
    val createdSubtasks: List<CreatedSubtask>,
    val errors: List<SubtaskCreationError>
)

/**
 * 생성된 하위작업 정보
 */
data class CreatedSubtask(
    val key: String,
    val title: String,
    val url: String
)

/**
 * 하위작업 생성 에러
 */
data class SubtaskCreationError(
    val subtaskTitle: String,
    val errorMessage: String,
    val errorCode: String? = null
)