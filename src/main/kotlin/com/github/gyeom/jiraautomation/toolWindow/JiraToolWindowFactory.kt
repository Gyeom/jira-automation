package com.github.gyeom.jiraautomation.toolWindow

import com.github.gyeom.jiraautomation.model.TicketHistory
import com.github.gyeom.jiraautomation.services.DiffAnalysisService
import com.github.gyeom.jiraautomation.services.JiraApiService
import com.github.gyeom.jiraautomation.services.TicketHistoryService
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
import java.time.format.DateTimeFormatter
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
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
        private val historyService = project.service<TicketHistoryService>()
        private var historyPanel: JBPanel<*>? = null

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

            // Recent tickets section
            val historyScrollPanel = createHistoryPanel()
            mainPanel.add(historyScrollPanel, BorderLayout.CENTER)

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

        private fun createHistoryPanel(): JBScrollPane {
            val panel = JBPanel<JBPanel<*>>()
            panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
            panel.border = BorderFactory.createEmptyBorder(10, 15, 10, 15)
            historyPanel = panel

            // Title
            val historyTitle = JBLabel("Recent Tickets")
            historyTitle.font = historyTitle.font.deriveFont(Font.BOLD, 14f)
            panel.add(historyTitle)
            panel.add(Box.createVerticalStrut(10))

            // Load and display tickets
            refreshHistoryPanel()

            val scrollPane = JBScrollPane(panel)
            scrollPane.border = null
            return scrollPane
        }

        private fun refreshHistoryPanel() {
            historyPanel?.let { panel ->
                // Remove all except title (first 2 components)
                while (panel.componentCount > 2) {
                    panel.remove(2)
                }

                val recentTickets = historyService.getRecentTickets(10)

                if (recentTickets.isEmpty()) {
                    val emptyLabel = JBLabel("No tickets created yet")
                    emptyLabel.foreground = java.awt.Color.GRAY
                    panel.add(emptyLabel)
                } else {
                    recentTickets.forEach { ticket ->
                        panel.add(createTicketItem(ticket))
                        panel.add(Box.createVerticalStrut(8))
                    }
                }

                panel.revalidate()
                panel.repaint()
            }
        }

        private fun createTicketItem(ticket: TicketHistory): JBPanel<*> {
            val itemPanel = JBPanel<JBPanel<*>>(GridBagLayout())
            itemPanel.border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(java.awt.Color(60, 60, 60)),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
            )
            itemPanel.maximumSize = Dimension(Int.MAX_VALUE, 80)

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

            // Project and type info
            gbc.gridx = 1
            gbc.weightx = 1.0
            gbc.insets = Insets(2, 10, 2, 0)
            val infoLabel = JBLabel("${ticket.projectKey} | ${ticket.issueType}")
            infoLabel.foreground = java.awt.Color.GRAY
            infoLabel.font = infoLabel.font.deriveFont(11f)
            itemPanel.add(infoLabel, gbc)

            // Title
            gbc.gridx = 0
            gbc.gridy = 1
            gbc.gridwidth = 2
            gbc.insets = Insets(2, 0, 2, 0)
            val titleLabel = JBLabel(ticket.title)
            itemPanel.add(titleLabel, gbc)

            // Created time
            gbc.gridy = 2
            val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            val timeLabel = JBLabel(ticket.createdAt.format(timeFormatter))
            timeLabel.foreground = java.awt.Color.GRAY
            timeLabel.font = timeLabel.font.deriveFont(10f)
            itemPanel.add(timeLabel, gbc)

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

            // Refresh history if dialog was successful
            if (dialogResult) {
                refreshHistoryPanel()
            }
        }
    }
}
