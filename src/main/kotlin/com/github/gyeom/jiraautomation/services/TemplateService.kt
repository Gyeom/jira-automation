package com.github.gyeom.jiraautomation.services

import com.github.gyeom.jiraautomation.model.SubtaskTemplate
import com.github.gyeom.jiraautomation.model.SubtaskDefinition
import com.github.gyeom.jiraautomation.model.AssigneeType
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import java.util.UUID

/**
 * 템플릿 관리 서비스
 */
@Service(Service.Level.PROJECT)
@State(
    name = "JiraSubtaskTemplates",
    storages = [Storage("jiraSubtaskTemplates.xml")]
)
class TemplateService : PersistentStateComponent<TemplateService.State> {

    data class State(
        @XCollection(style = XCollection.Style.v2)
        var templates: MutableList<TemplateData> = mutableListOf(),
        var activeTemplateIds: MutableSet<String> = mutableSetOf()
    )

    @Tag("template")
    data class TemplateData(
        var id: String = "",
        var name: String = "",
        var description: String = "",
        var projectKey: String? = null,
        var issueType: String = "",
        var patternString: String? = null,
        @XCollection(style = XCollection.Style.v2)
        var subtasks: MutableList<SubtaskData> = mutableListOf(),
        var autoApply: Boolean = false
    )

    @Tag("subtask")
    data class SubtaskData(
        var title: String = "",
        var description: String? = null,
        var assignee: String = AssigneeType.INHERIT.name,
        var estimatedHours: Float? = null,
        @XCollection(style = XCollection.Style.v2)
        var labels: MutableList<String> = mutableListOf(),
        var priority: String? = null,
        var order: Int = 0
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, this.state)
    }

    /**
     * 기본 템플릿 초기화
     */
    fun initializeDefaultTemplates() {
        if (state.templates.isEmpty()) {
            addDefaultTemplates()
        }
    }

    /**
     * 기본 템플릿 추가
     */
    private fun addDefaultTemplates() {
        // 기능 개발 템플릿
        val featureTemplate = TemplateData(
            id = UUID.randomUUID().toString(),
            name = "기능 개발 템플릿",
            description = "새로운 기능 개발 시 사용하는 표준 템플릿",
            issueType = "Story",
            patternString = "^feat:.*",
            subtasks = mutableListOf(
                SubtaskData(
                    title = "API 설계 및 구현",
                    description = "백엔드 API 엔드포인트 설계 및 구현",
                    assignee = AssigneeType.INHERIT.name,
                    estimatedHours = 4f,
                    priority = "High",
                    order = 1
                ),
                SubtaskData(
                    title = "프론트엔드 구현",
                    description = "UI 컴포넌트 및 상태 관리 구현",
                    assignee = AssigneeType.INHERIT.name,
                    estimatedHours = 6f,
                    priority = "High",
                    order = 2
                ),
                SubtaskData(
                    title = "단위 테스트 작성",
                    description = "단위 테스트 및 통합 테스트 작성",
                    assignee = AssigneeType.INHERIT.name,
                    estimatedHours = 3f,
                    labels = mutableListOf("testing"),
                    priority = "Medium",
                    order = 3
                ),
                SubtaskData(
                    title = "코드 리뷰",
                    description = "동료 리뷰 및 피드백 반영",
                    assignee = AssigneeType.INHERIT.name,
                    estimatedHours = 2f,
                    priority = "Medium",
                    order = 4
                ),
                SubtaskData(
                    title = "문서 업데이트",
                    description = "API 문서 및 사용자 가이드 업데이트",
                    assignee = AssigneeType.INHERIT.name,
                    estimatedHours = 2f,
                    labels = mutableListOf("documentation"),
                    priority = "Low",
                    order = 5
                )
            ),
            autoApply = false
        )

        // 버그 수정 템플릿
        val bugFixTemplate = TemplateData(
            id = UUID.randomUUID().toString(),
            name = "버그 수정 템플릿",
            description = "버그 수정 작업 시 사용하는 템플릿",
            issueType = "Bug",
            patternString = "^fix:.*",
            subtasks = mutableListOf(
                SubtaskData(
                    title = "버그 재현 및 원인 분석",
                    description = "버그 재현 단계 확인 및 근본 원인 분석",
                    assignee = AssigneeType.INHERIT.name,
                    estimatedHours = 2f,
                    priority = "High",
                    order = 1
                ),
                SubtaskData(
                    title = "수정 사항 구현",
                    description = "버그 수정 코드 작성",
                    assignee = AssigneeType.INHERIT.name,
                    estimatedHours = 3f,
                    priority = "High",
                    order = 2
                ),
                SubtaskData(
                    title = "회귀 테스트 추가",
                    description = "버그 재발 방지를 위한 테스트 케이스 추가",
                    assignee = AssigneeType.INHERIT.name,
                    estimatedHours = 2f,
                    labels = mutableListOf("testing", "regression"),
                    priority = "Medium",
                    order = 3
                ),
                SubtaskData(
                    title = "QA 검증",
                    description = "수정 사항 QA 검증 및 확인",
                    assignee = AssigneeType.INHERIT.name,
                    estimatedHours = 1f,
                    priority = "Medium",
                    order = 4
                )
            ),
            autoApply = false
        )

        // 리팩토링 템플릿
        val refactorTemplate = TemplateData(
            id = UUID.randomUUID().toString(),
            name = "리팩토링 템플릿",
            description = "코드 리팩토링 작업 시 사용하는 템플릿",
            issueType = "Task",
            patternString = "^refactor:.*",
            subtasks = mutableListOf(
                SubtaskData(
                    title = "리팩토링 계획 수립",
                    description = "리팩토링 범위 및 방향 설정",
                    assignee = AssigneeType.INHERIT.name,
                    estimatedHours = 1f,
                    priority = "Medium",
                    order = 1
                ),
                SubtaskData(
                    title = "기존 테스트 확인",
                    description = "리팩토링 전 테스트 커버리지 확인",
                    assignee = AssigneeType.INHERIT.name,
                    estimatedHours = 1f,
                    labels = mutableListOf("testing"),
                    priority = "High",
                    order = 2
                ),
                SubtaskData(
                    title = "리팩토링 구현",
                    description = "계획에 따른 리팩토링 수행",
                    assignee = AssigneeType.INHERIT.name,
                    estimatedHours = 4f,
                    priority = "Medium",
                    order = 3
                ),
                SubtaskData(
                    title = "성능 테스트",
                    description = "리팩토링 후 성능 영향 확인",
                    assignee = AssigneeType.INHERIT.name,
                    estimatedHours = 2f,
                    labels = mutableListOf("performance"),
                    priority = "Low",
                    order = 4
                )
            ),
            autoApply = false
        )

        state.templates.add(featureTemplate)
        state.templates.add(bugFixTemplate)
        state.templates.add(refactorTemplate)

        // 기본적으로 모든 템플릿 활성화
        state.activeTemplateIds.addAll(listOf(
            featureTemplate.id,
            bugFixTemplate.id,
            refactorTemplate.id
        ))
    }

    /**
     * 활성화된 템플릿 가져오기
     */
    fun getActiveTemplates(): List<SubtaskTemplate> {
        return state.templates
            .filter { state.activeTemplateIds.contains(it.id) }
            .map { it.toSubtaskTemplate() }
    }

    /**
     * 모든 템플릿 가져오기
     */
    fun getAllTemplates(): List<SubtaskTemplate> {
        return state.templates.map { it.toSubtaskTemplate() }
    }

    /**
     * 템플릿 추가
     */
    fun addTemplate(template: SubtaskTemplate): String {
        val id = template.id.ifEmpty { UUID.randomUUID().toString() }
        val templateData = template.toTemplateData(id)
        state.templates.add(templateData)
        if (template.autoApply) {
            state.activeTemplateIds.add(id)
        }
        return id
    }

    /**
     * 템플릿 업데이트
     */
    fun updateTemplate(template: SubtaskTemplate) {
        val index = state.templates.indexOfFirst { it.id == template.id }
        if (index >= 0) {
            state.templates[index] = template.toTemplateData(template.id)
        }
    }

    /**
     * 템플릿 삭제
     */
    fun deleteTemplate(templateId: String) {
        state.templates.removeAll { it.id == templateId }
        state.activeTemplateIds.remove(templateId)
    }

    /**
     * 템플릿 활성화/비활성화
     */
    fun setTemplateActive(templateId: String, active: Boolean) {
        if (active) {
            state.activeTemplateIds.add(templateId)
        } else {
            state.activeTemplateIds.remove(templateId)
        }
    }

    /**
     * 프로젝트별 템플릿 가져오기
     */
    fun getTemplatesForProject(projectKey: String): List<SubtaskTemplate> {
        return state.templates
            .filter { it.projectKey == null || it.projectKey == projectKey }
            .filter { state.activeTemplateIds.contains(it.id) }
            .map { it.toSubtaskTemplate() }
    }

    /**
     * 이슈 타입별 템플릿 가져오기
     */
    fun getTemplatesForIssueType(issueType: String): List<SubtaskTemplate> {
        return state.templates
            .filter { it.issueType == issueType }
            .filter { state.activeTemplateIds.contains(it.id) }
            .map { it.toSubtaskTemplate() }
    }

    // Extension functions for conversion
    private fun TemplateData.toSubtaskTemplate(): SubtaskTemplate {
        return SubtaskTemplate(
            id = id,
            name = name,
            description = description,
            projectKey = projectKey,
            issueType = issueType,
            pattern = patternString?.let { Regex(it) },
            subtasks = subtasks.map { it.toSubtaskDefinition() },
            autoApply = autoApply
        )
    }

    private fun SubtaskData.toSubtaskDefinition(): SubtaskDefinition {
        return SubtaskDefinition(
            title = title,
            description = description,
            assignee = AssigneeType.valueOf(assignee),
            estimatedHours = estimatedHours,
            labels = labels.toList(),
            priority = priority,
            order = order
        )
    }

    private fun SubtaskTemplate.toTemplateData(id: String): TemplateData {
        return TemplateData(
            id = id,
            name = name,
            description = description,
            projectKey = projectKey,
            issueType = issueType,
            patternString = pattern?.pattern,
            subtasks = subtasks.map { it.toSubtaskData() }.toMutableList(),
            autoApply = autoApply
        )
    }

    private fun SubtaskDefinition.toSubtaskData(): SubtaskData {
        return SubtaskData(
            title = title,
            description = description,
            assignee = assignee.name,
            estimatedHours = estimatedHours,
            labels = labels.toMutableList(),
            priority = priority,
            order = order
        )
    }
}