package com.github.gyeom.jiraautomation.toolWindow

import com.github.gyeom.jiraautomation.model.RecentIssue
import com.github.gyeom.jiraautomation.services.DiffAnalysisService
import com.github.gyeom.jiraautomation.services.JiraApiService
import com.github.gyeom.jiraautomation.ui.CreateJiraTicketDialog
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JTabbedPane
import javax.swing.SwingConstants

class JiraToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val toolWindowContent = JiraToolWindowContent(project)
        val content = ContentFactory.getInstance().createContent(toolWindowContent.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    class JiraToolWindowContent(private val project: Project) {

        private val diffAnalysisService = project.service<DiffAnalysisService>()
        private val jiraApiService = project.service<JiraApiService>()
        private var createdTicketsPanel: JBPanel<*>? = null
        private var assignedTicketsPanel: JBPanel<*>? = null

        fun getContent(): JBPanel<*> {
            val mainPanel = JBPanel<JBPanel<*>>(BorderLayout())

            // Top section with title and create button
            val topPanel = JBPanel<JBPanel<*>>(BorderLayout())
            topPanel.border = BorderFactory.createEmptyBorder(15, 15, 10, 15)

            val titleLabel = JBLabel("Jira Ticket Creator")
            titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 16f)
            topPanel.add(titleLabel, BorderLayout.NORTH)

            // Create button
            val createButton = JButton("Create Ticket from Uncommitted Changes")
            createButton.addActionListener { createTicketFromChanges() }
            val buttonPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 0, 10))
            buttonPanel.add(createButton)
            topPanel.add(buttonPanel, BorderLayout.CENTER)

            mainPanel.add(topPanel, BorderLayout.NORTH)

            // Tabbed pane for different ticket views
            val tabbedPane = JTabbedPane()

            // Tab 1: Assigned to me
            val assignedScrollPanel = createAssignedTicketsPanel()
            tabbedPane.addTab("Assigned to Me", assignedScrollPanel)

            // Tab 2: Created by me
            val createdScrollPanel = createCreatedTicketsPanel()
            tabbedPane.addTab("Created by Me", createdScrollPanel)

            mainPanel.add(tabbedPane, BorderLayout.CENTER)

            // Bottom section with settings button
            val bottomPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.CENTER, 10, 10))
            val settingsButton = JButton("Open Settings")
            settingsButton.addActionListener {
                com.intellij.openapi.options.ShowSettingsUtil.getInstance()
                    .showSettingsDialog(project, "Jira Ticket Creator")
            }
            bottomPanel.add(settingsButton)
            mainPanel.add(bottomPanel, BorderLayout.SOUTH)

            return mainPanel
        }

        private fun createAssignedTicketsPanel(): JBScrollPane {
            val panel = JBPanel<JBPanel<*>>()
            panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
            panel.border = BorderFactory.createEmptyBorder(10, 15, 10, 15)
            assignedTicketsPanel = panel

            // Title and Refresh button
            val titlePanel = JBPanel<JBPanel<*>>(BorderLayout())
            val title = JBLabel("My Assigned Tickets")
            title.font = title.font.deriveFont(Font.BOLD, 14f)
            titlePanel.add(title, BorderLayout.WEST)

            val refreshButton = JButton("Refresh")
            refreshButton.addActionListener {
                refreshAssignedTicketsPanel()
            }
            titlePanel.add(refreshButton, BorderLayout.EAST)

            panel.add(titlePanel)
            panel.add(Box.createVerticalStrut(10))

            // Load and display tickets
            refreshAssignedTicketsPanel()

            val scrollPane = JBScrollPane(panel)
            scrollPane.border = null
            return scrollPane
        }

        private fun createCreatedTicketsPanel(): JBScrollPane {
            val panel = JBPanel<JBPanel<*>>()
            panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
            panel.border = BorderFactory.createEmptyBorder(10, 15, 10, 15)
            createdTicketsPanel = panel

            // Title and Refresh button
            val titlePanel = JBPanel<JBPanel<*>>(BorderLayout())
            val title = JBLabel("My Created Tickets")
            title.font = title.font.deriveFont(Font.BOLD, 14f)
            titlePanel.add(title, BorderLayout.WEST)

            val refreshButton = JButton("Refresh")
            refreshButton.addActionListener {
                refreshCreatedTicketsPanel()
            }
            titlePanel.add(refreshButton, BorderLayout.EAST)

            panel.add(titlePanel)
            panel.add(Box.createVerticalStrut(10))

            // Load and display tickets
            refreshCreatedTicketsPanel()

            val scrollPane = JBScrollPane(panel)
            scrollPane.border = null
            return scrollPane
        }

        private fun refreshAssignedTicketsPanel() {
            assignedTicketsPanel?.let { panel ->
                // Remove all except title panel (first 2 components)
                while (panel.componentCount > 2) {
                    panel.remove(2)
                }

                // Show loading message
                val loadingLabel = JBLabel("Loading assigned tickets...")
                loadingLabel.foreground = java.awt.Color.GRAY
                panel.add(loadingLabel)
                panel.revalidate()
                panel.repaint()

                // Load tickets from Jira API in background thread
                Thread {
                    val result = jiraApiService.getRecentAssignedIssues(10)

                    javax.swing.SwingUtilities.invokeLater {
                        // Remove loading message
                        panel.remove(loadingLabel)

                        result.onSuccess { recentTickets ->
                            if (recentTickets.isEmpty()) {
                                val emptyLabel = JBLabel("No assigned tickets found")
                                emptyLabel.foreground = java.awt.Color.GRAY
                                panel.add(emptyLabel)
                            } else {
                                recentTickets.forEach { ticket ->
                                    panel.add(createTicketItem(ticket))
                                    panel.add(Box.createVerticalStrut(8))
                                }
                            }
                        }.onFailure { error ->
                            val errorLabel = JBLabel("Failed to load tickets: ${error.message}")
                            errorLabel.foreground = java.awt.Color.RED
                            panel.add(errorLabel)
                        }

                        panel.revalidate()
                        panel.repaint()
                    }
                }.start()
            }
        }

        private fun refreshCreatedTicketsPanel() {
            createdTicketsPanel?.let { panel ->
                // Remove all except title panel (first 2 components)
                while (panel.componentCount > 2) {
                    panel.remove(2)
                }

                // Show loading message
                val loadingLabel = JBLabel("Loading created tickets...")
                loadingLabel.foreground = java.awt.Color.GRAY
                panel.add(loadingLabel)
                panel.revalidate()
                panel.repaint()

                // Load tickets from Jira API in background thread
                Thread {
                    val result = jiraApiService.getRecentCreatedIssues(10)

                    javax.swing.SwingUtilities.invokeLater {
                        // Remove loading message
                        panel.remove(loadingLabel)

                        result.onSuccess { recentTickets ->
                            if (recentTickets.isEmpty()) {
                                val emptyLabel = JBLabel("No created tickets found")
                                emptyLabel.foreground = java.awt.Color.GRAY
                                panel.add(emptyLabel)
                            } else {
                                recentTickets.forEach { ticket ->
                                    panel.add(createTicketItem(ticket))
                                    panel.add(Box.createVerticalStrut(8))
                                }
                            }
                        }.onFailure { error ->
                            val errorLabel = JBLabel("Failed to load tickets: ${error.message}")
                            errorLabel.foreground = java.awt.Color.RED
                            panel.add(errorLabel)
                        }

                        panel.revalidate()
                        panel.repaint()
                    }
                }.start()
            }
        }

        private fun createTicketItem(ticket: RecentIssue): JBPanel<*> {
            val itemPanel = JBPanel<JBPanel<*>>(GridBagLayout())
            itemPanel.border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(java.awt.Color(60, 60, 60)),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
            )
            itemPanel.maximumSize = Dimension(Int.MAX_VALUE, 100)

            val gbc = GridBagConstraints()
            gbc.fill = GridBagConstraints.HORIZONTAL
            gbc.anchor = GridBagConstraints.WEST
            gbc.insets = Insets(2, 0, 2, 0)

            // Ticket key as clickable link
            gbc.gridx = 0
            gbc.gridy = 0
            gbc.weightx = 0.0
            val keyLabel = JBLabel("<html><b>${ticket.key}</b></html>")
            keyLabel.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            keyLabel.toolTipText = "Click to open in browser"
            keyLabel.addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent?) {
                    BrowserUtil.browse(ticket.url)
                }
            })
            itemPanel.add(keyLabel, gbc)

            // Status badge
            gbc.gridx = 1
            gbc.weightx = 0.0
            gbc.insets = Insets(2, 10, 2, 10)
            val statusLabel = JBLabel(ticket.status)
            statusLabel.foreground = when (ticket.status.lowercase()) {
                "done", "closed", "resolved" -> java.awt.Color(0, 150, 0)
                "in progress", "in review" -> java.awt.Color(0, 100, 200)
                else -> java.awt.Color.GRAY
            }
            statusLabel.font = statusLabel.font.deriveFont(Font.BOLD, 10f)
            itemPanel.add(statusLabel, gbc)

            // Project and type info
            gbc.gridx = 2
            gbc.weightx = 1.0
            gbc.insets = Insets(2, 0, 2, 0)
            val infoText = buildString {
                append("${ticket.projectKey} | ${ticket.issueType}")
                ticket.priority?.let { append(" | $it") }
            }
            val infoLabel = JBLabel(infoText)
            infoLabel.foreground = java.awt.Color.GRAY
            infoLabel.font = infoLabel.font.deriveFont(11f)
            itemPanel.add(infoLabel, gbc)

            // Title (summary)
            gbc.gridx = 0
            gbc.gridy = 1
            gbc.gridwidth = 3
            gbc.insets = Insets(2, 0, 2, 0)
            val titleLabel = JBLabel(ticket.summary)
            itemPanel.add(titleLabel, gbc)

            // Created time
            gbc.gridy = 2
            try {
                // Parse ISO 8601 date format from Jira
                val instant = java.time.Instant.parse(ticket.created)
                val zonedDateTime = instant.atZone(java.time.ZoneId.systemDefault())
                val timeFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                val timeLabel = JBLabel("Created: ${zonedDateTime.format(timeFormatter)}")
                timeLabel.foreground = java.awt.Color.GRAY
                timeLabel.font = timeLabel.font.deriveFont(10f)
                itemPanel.add(timeLabel, gbc)
            } catch (e: Exception) {
                // If parsing fails, just show raw date
                val timeLabel = JBLabel("Created: ${ticket.created}")
                timeLabel.foreground = java.awt.Color.GRAY
                timeLabel.font = timeLabel.font.deriveFont(10f)
                itemPanel.add(timeLabel, gbc)
            }

            return itemPanel
        }

        private fun createTicketFromChanges() {
            val diffResult = diffAnalysisService.analyzeUncommittedChanges()

            if (diffResult == null) {
                Messages.showInfoMessage(
                    project,
                    "No uncommitted changes found.\nPlease make some changes before creating a Jira ticket.",
                    "No Changes"
                )
                return
            }

            val dialog = CreateJiraTicketDialog(project, diffResult)
            val dialogResult = dialog.showAndGet()

            // Refresh both panels if dialog was successful (to show newly created ticket from API)
            if (dialogResult) {
                refreshCreatedTicketsPanel()
                refreshAssignedTicketsPanel()
            }
        }
    }
}
