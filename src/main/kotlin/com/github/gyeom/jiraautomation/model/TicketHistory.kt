package com.github.gyeom.jiraautomation.model

import java.time.LocalDateTime

/**
 * 최근 생성한 티켓 히스토리
 */
data class TicketHistory(
    val key: String,
    val title: String,
    val url: String,
    val projectKey: String,
    val issueType: String,
    val createdAt: LocalDateTime = LocalDateTime.now()
)

/**
 * 티켓 히스토리 목록
 */
data class TicketHistoryList(
    val tickets: MutableList<TicketHistory> = mutableListOf()
) {
    companion object {
        const val MAX_HISTORY_SIZE = 10
    }

    fun addTicket(ticket: TicketHistory) {
        // Remove if already exists (by key)
        tickets.removeIf { it.key == ticket.key }

        // Add at the beginning
        tickets.add(0, ticket)

        // Keep only recent MAX_HISTORY_SIZE items
        if (tickets.size > MAX_HISTORY_SIZE) {
            tickets.subList(MAX_HISTORY_SIZE, tickets.size).clear()
        }
    }

    fun getRecentTickets(limit: Int = 5): List<TicketHistory> {
        return tickets.take(limit)
    }
}
