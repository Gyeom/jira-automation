package com.github.gyeom.jiraautomation.services

import com.github.gyeom.jiraautomation.model.TicketHistory
import com.github.gyeom.jiraautomation.model.TicketHistoryList
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 티켓 생성 히스토리를 관리하는 서비스
 */
@Service(Service.Level.PROJECT)
@State(
    name = "JiraTicketHistory",
    storages = [Storage("jiraTicketHistory.xml")]
)
class TicketHistoryService : PersistentStateComponent<TicketHistoryService.State> {

    private var myState = State()

    data class State(
        var tickets: MutableList<TicketHistoryItem> = mutableListOf()
    )

    data class TicketHistoryItem(
        var key: String = "",
        var title: String = "",
        var url: String = "",
        var projectKey: String = "",
        var issueType: String = "",
        var createdAt: String = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    )

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    fun addTicket(ticket: TicketHistory) {
        val item = TicketHistoryItem(
            key = ticket.key,
            title = ticket.title,
            url = ticket.url,
            projectKey = ticket.projectKey,
            issueType = ticket.issueType,
            createdAt = ticket.createdAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        )

        // Remove if already exists
        myState.tickets.removeIf { it.key == ticket.key }

        // Add at the beginning
        myState.tickets.add(0, item)

        // Keep only recent 10 items
        if (myState.tickets.size > TicketHistoryList.MAX_HISTORY_SIZE) {
            myState.tickets.subList(TicketHistoryList.MAX_HISTORY_SIZE, myState.tickets.size).clear()
        }
    }

    fun getRecentTickets(limit: Int = 5): List<TicketHistory> {
        return myState.tickets.take(limit).map { item ->
            TicketHistory(
                key = item.key,
                title = item.title,
                url = item.url,
                projectKey = item.projectKey,
                issueType = item.issueType,
                createdAt = LocalDateTime.parse(item.createdAt, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            )
        }
    }

    fun clearHistory() {
        myState.tickets.clear()
    }

    companion object {
        fun getInstance(project: Project): TicketHistoryService {
            return project.getService(TicketHistoryService::class.java)
        }
    }
}
