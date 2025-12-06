package com.github.gyeom.jiraautomation.toolWindow

import com.github.gyeom.jiraautomation.model.RecentIssue
import com.github.gyeom.jiraautomation.services.AIService
import com.github.gyeom.jiraautomation.services.DiffAnalysisService
import com.github.gyeom.jiraautomation.services.JiraApiService
import com.github.gyeom.jiraautomation.settings.JiraSettingsListener
import com.github.gyeom.jiraautomation.settings.JiraSettingsState
import com.github.gyeom.jiraautomation.ui.CreateJiraTicketDialog
import com.github.gyeom.jiraautomation.ui.QuickTextInputDialog
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
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
        private val aiService = project.service<AIService>()
        private val settings = JiraSettingsState.getInstance(project)
        private var assignedTicketsMainPanel: JBPanel<*>? = null
        private var createdTicketsMainPanel: JBPanel<*>? = null

        // UI components that need to be updated based on settings
        private lateinit var createFromCodeButton: JButton
        private lateinit var createFromTextButton: JButton
        private lateinit var settingsStatusLabel: JBLabel

        fun getContent(): JBPanel<*> {
            val mainPanel = JBPanel<JBPanel<*>>(BorderLayout())

            // Top section with title and create button
            val topPanel = JBPanel<JBPanel<*>>(BorderLayout())
            topPanel.border = BorderFactory.createEmptyBorder(15, 15, 10, 15)

            val titleLabel = JBLabel("Jira Ticket Creator")
            titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 16f)
            topPanel.add(titleLabel, BorderLayout.NORTH)

            // Settings status indicator
            settingsStatusLabel = JBLabel()
            settingsStatusLabel.font = settingsStatusLabel.font.deriveFont(11f)

            // Create buttons
            createFromCodeButton = JButton("Create from Code Changes")
            createFromCodeButton.addActionListener { createTicketFromChanges() }

            createFromTextButton = JButton("Create Jira Ticket")
            createFromTextButton.addActionListener { createTicketFromText() }

            val settingsButton = JButton("Open Settings")
            settingsButton.addActionListener {
                com.intellij.openapi.options.ShowSettingsUtil.getInstance()
                    .showSettingsDialog(project, "Jira Ticket Creator")
            }

            // Status and buttons panel
            val statusPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 10, 5))
            statusPanel.add(settingsStatusLabel)

            val buttonPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 5, 5))
            buttonPanel.add(createFromCodeButton)
            buttonPanel.add(createFromTextButton)
            buttonPanel.add(settingsButton)

            val topContentPanel = JBPanel<JBPanel<*>>()
            topContentPanel.layout = BoxLayout(topContentPanel, BoxLayout.Y_AXIS)
            topContentPanel.add(statusPanel)
            topContentPanel.add(buttonPanel)

            topPanel.add(topContentPanel, BorderLayout.CENTER)
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

            // Subscribe to settings changes
            subscribeToSettingsChanges()

            // Initial state update
            updateUIBasedOnSettings()

            return mainPanel
        }

        private fun subscribeToSettingsChanges() {
            project.messageBus.connect().subscribe(
                JiraSettingsListener.TOPIC,
                object : JiraSettingsListener {
                    override fun onSettingsChanged(isValid: Boolean) {
                        javax.swing.SwingUtilities.invokeLater {
                            updateUIBasedOnSettings(isValid)
                            if (isValid) {
                                // Auto-refresh ticket lists when settings are valid
                                refreshAssignedTicketsPanel()
                                refreshCreatedTicketsPanel()
                            }
                        }
                    }
                }
            )
        }

        private fun updateUIBasedOnSettings(forceValid: Boolean? = null) {
            val state = settings.state
            val hasRequiredSettings = state.jiraUrl.isNotEmpty() &&
                    state.jiraUsername.isNotEmpty() &&
                    state.jiraApiToken.isNotEmpty()

            val isValid = forceValid ?: hasRequiredSettings

            if (!hasRequiredSettings) {
                settingsStatusLabel.text = "⚙️ Please configure Jira settings → Open Settings"
                settingsStatusLabel.foreground = java.awt.Color(255, 152, 0)  // Orange
                createFromCodeButton.isEnabled = false
                createFromTextButton.isEnabled = false
                createFromCodeButton.toolTipText = "Configure Jira settings first"
                createFromTextButton.toolTipText = "Configure Jira settings first"
            } else if (isValid) {
                settingsStatusLabel.text = "✓ Connected to Jira"
                settingsStatusLabel.foreground = java.awt.Color(76, 175, 80)  // Green
                createFromCodeButton.isEnabled = true
                createFromTextButton.isEnabled = true
                createFromCodeButton.toolTipText = "Create ticket from uncommitted code changes"
                createFromTextButton.toolTipText = "Create ticket from text description"
            } else {
                settingsStatusLabel.text = "⚠️ Jira connection failed - check settings"
                settingsStatusLabel.foreground = java.awt.Color(244, 67, 54)  // Red
                createFromCodeButton.isEnabled = false
                createFromTextButton.isEnabled = false
                createFromCodeButton.toolTipText = "Jira connection failed"
                createFromTextButton.toolTipText = "Jira connection failed"
            }
        }

        private fun createAssignedTicketsPanel(): JBPanel<*> {
            val mainPanel = JBPanel<JBPanel<*>>(BorderLayout())
            assignedTicketsMainPanel = mainPanel

            // Top section: title + banner only (compact, fixed height)
            val topSection = JBPanel<JBPanel<*>>()
            topSection.layout = BoxLayout(topSection, BoxLayout.Y_AXIS)

            // Info banner with refresh button
            val bannerPanel = JBPanel<JBPanel<*>>(BorderLayout())
            bannerPanel.background = java.awt.Color(227, 242, 253)  // Light blue
            bannerPanel.border = BorderFactory.createEmptyBorder(5, 15, 5, 15)

            val infoLabel = JBLabel("ℹ️ Completed tickets limited to last 30 days")
            infoLabel.foreground = java.awt.Color(25, 118, 210)  // Dark blue
            infoLabel.font = infoLabel.font.deriveFont(11.5f)  // Increased from 10pt to 11.5pt
            bannerPanel.add(infoLabel, BorderLayout.WEST)

            val refreshButton = JButton("Refresh")
            refreshButton.addActionListener {
                refreshAssignedTicketsPanel()
            }
            bannerPanel.add(refreshButton, BorderLayout.EAST)

            topSection.add(bannerPanel)

            mainPanel.add(topSection, BorderLayout.NORTH)

            // 3. Status category tabs (in CENTER for scrolling!)
            val statusTabs = JTabbedPane()
            mainPanel.add(statusTabs, BorderLayout.CENTER)

            // Store reference
            mainPanel.putClientProperty("statusTabs", statusTabs)

            // Load and display tickets
            refreshAssignedTicketsPanel()

            return mainPanel
        }

        private fun createCreatedTicketsPanel(): JBPanel<*> {
            val mainPanel = JBPanel<JBPanel<*>>(BorderLayout())
            createdTicketsMainPanel = mainPanel

            // Top section: info banner only (compact, fixed height)
            val topSection = JBPanel<JBPanel<*>>()
            topSection.layout = BoxLayout(topSection, BoxLayout.Y_AXIS)

            // Info banner with refresh button
            val bannerPanel = JBPanel<JBPanel<*>>(BorderLayout())
            bannerPanel.background = java.awt.Color(227, 242, 253)  // Light blue
            bannerPanel.border = BorderFactory.createEmptyBorder(5, 15, 5, 15)

            val infoLabel = JBLabel("ℹ️ Completed tickets limited to last 30 days")
            infoLabel.foreground = java.awt.Color(25, 118, 210)  // Dark blue
            infoLabel.font = infoLabel.font.deriveFont(11.5f)  // Increased from 10pt to 11.5pt
            bannerPanel.add(infoLabel, BorderLayout.WEST)

            val refreshButton = JButton("Refresh")
            refreshButton.addActionListener {
                refreshCreatedTicketsPanel()
            }
            bannerPanel.add(refreshButton, BorderLayout.EAST)

            topSection.add(bannerPanel)

            mainPanel.add(topSection, BorderLayout.NORTH)

            // Status category tabs (in CENTER for scrolling!)
            val statusTabs = JTabbedPane()
            mainPanel.add(statusTabs, BorderLayout.CENTER)

            // Store reference
            mainPanel.putClientProperty("statusTabs", statusTabs)

            // Load and display tickets
            refreshCreatedTicketsPanel()

            return mainPanel
        }


        private fun refreshAssignedTicketsPanel() {
            val mainPanel = assignedTicketsMainPanel ?: return
            val statusTabs = mainPanel.getClientProperty("statusTabs") as? JTabbedPane ?: return

            // Clear tabs
            statusTabs.removeAll()

            // Add loading tab
            val loadingPanel = JBPanel<JBPanel<*>>()
            loadingPanel.add(JBLabel("Loading assigned tickets..."))
            statusTabs.addTab("Loading...", loadingPanel)

            // Load tickets from Jira API in background thread
            Thread {
                val result = jiraApiService.getRecentAssignedIssues(50)

                javax.swing.SwingUtilities.invokeLater {
                    statusTabs.removeAll()

                    result.onSuccess { recentTickets ->
                        if (recentTickets.isEmpty()) {
                            val emptyPanel = JBPanel<JBPanel<*>>()
                            emptyPanel.add(JBLabel("No assigned tickets found"))
                            statusTabs.addTab("All (0)", emptyPanel)
                        } else {
                            createStatusCategoryTabs(statusTabs, recentTickets)
                        }
                    }.onFailure { error ->
                        val errorPanel = JBPanel<JBPanel<*>>()
                        val errorLabel = JBLabel("Failed to load tickets: ${error.message}")
                        errorLabel.foreground = java.awt.Color.RED
                        errorPanel.add(errorLabel)
                        statusTabs.addTab("Error", errorPanel)
                    }

                    statusTabs.revalidate()
                    statusTabs.repaint()
                }
            }.start()
        }

        private fun refreshCreatedTicketsPanel() {
            val mainPanel = createdTicketsMainPanel ?: return
            val statusTabs = mainPanel.getClientProperty("statusTabs") as? JTabbedPane ?: return

            // Clear tabs
            statusTabs.removeAll()

            // Add loading tab
            val loadingPanel = JBPanel<JBPanel<*>>()
            loadingPanel.add(JBLabel("Loading created tickets..."))
            statusTabs.addTab("Loading...", loadingPanel)

            // Load tickets from Jira API in background thread
            Thread {
                val result = jiraApiService.getRecentCreatedIssues(50)

                javax.swing.SwingUtilities.invokeLater {
                    statusTabs.removeAll()

                    result.onSuccess { recentTickets ->
                        if (recentTickets.isEmpty()) {
                            val emptyPanel = JBPanel<JBPanel<*>>()
                            emptyPanel.add(JBLabel("No created tickets found"))
                            statusTabs.addTab("All (0)", emptyPanel)
                        } else {
                            createStatusCategoryTabs(statusTabs, recentTickets)
                        }
                    }.onFailure { error ->
                        val errorPanel = JBPanel<JBPanel<*>>()
                        val errorLabel = JBLabel("Failed to load tickets: ${error.message}")
                        errorLabel.foreground = java.awt.Color.RED
                        errorPanel.add(errorLabel)
                        statusTabs.addTab("Error", errorPanel)
                    }

                    statusTabs.revalidate()
                    statusTabs.repaint()
                }
            }.start()
        }

        private fun createStatusCategoryTabs(tabbedPane: JTabbedPane, tickets: List<RecentIssue>) {
            // Group tickets by status category
            val ticketsByCategory = tickets.groupBy { it.statusCategory }

            // Create "All" tab first
            val allPanel = createTicketListPanel(tickets)
            tabbedPane.addTab("All (${tickets.size})", allPanel)

            // Create tabs for each status category
            com.github.gyeom.jiraautomation.model.StatusCategory.values().forEach { category ->
                val categoryTickets = ticketsByCategory[category.key] ?: emptyList()

                if (categoryTickets.isNotEmpty()) {
                    val categoryPanel = createTicketListPanel(categoryTickets)
                    val tabTitle = "${category.displayName} (${categoryTickets.size})"
                    tabbedPane.addTab(tabTitle, categoryPanel)
                }
            }

            // Add tabs for any other status categories not in the enum
            ticketsByCategory.forEach { (categoryKey, categoryTickets) ->
                if (categoryKey != null &&
                    categoryKey !in com.github.gyeom.jiraautomation.model.StatusCategory.values().map { it.key }) {
                    val categoryPanel = createTicketListPanel(categoryTickets)
                    tabbedPane.addTab("$categoryKey (${categoryTickets.size})", categoryPanel)
                }
            }
        }

        private fun createTicketListPanel(tickets: List<RecentIssue>): JBScrollPane {
            val panel = JBPanel<JBPanel<*>>()
            panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
            panel.border = BorderFactory.createEmptyBorder(10, 15, 10, 15)

            if (tickets.isEmpty()) {
                val emptyLabel = JBLabel("No tickets in this category")
                emptyLabel.foreground = java.awt.Color.GRAY
                panel.add(emptyLabel)
            } else {
                tickets.forEach { ticket ->
                    panel.add(createTicketItem(ticket))
                    panel.add(Box.createVerticalStrut(8))
                }
            }

            val scrollPane = JBScrollPane(panel)
            scrollPane.border = null
            return scrollPane
        }

        private fun createTicketItem(ticket: RecentIssue): JBPanel<*> {
            val itemPanel = JBPanel<JBPanel<*>>(GridBagLayout())
            itemPanel.border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(java.awt.Color(60, 60, 60)),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
            )
            itemPanel.maximumSize = Dimension(Int.MAX_VALUE, 100)

            // Add right-click context menu
            val popupMenu = javax.swing.JPopupMenu()
            val createSubtaskItem = javax.swing.JMenuItem("Create Subtask for ${ticket.key}")
            createSubtaskItem.addActionListener {
                createSubtaskForIssue(ticket.key)
            }
            popupMenu.add(createSubtaskItem)

            itemPanel.componentPopupMenu = popupMenu

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

            // Project and type info (moved before status)
            gbc.gridx = 1
            gbc.weightx = 1.0
            gbc.insets = Insets(2, 10, 2, 10)
            val infoText = buildString {
                append("${ticket.projectKey} | ${ticket.issueType}")
                ticket.priority?.let { append(" | $it") }
            }
            val infoLabel = JBLabel(infoText)
            infoLabel.foreground = java.awt.Color.GRAY
            infoLabel.font = infoLabel.font.deriveFont(11f)
            itemPanel.add(infoLabel, gbc)

            // Status ComboBox (right side)
            gbc.gridx = 2
            gbc.weightx = 0.0
            gbc.anchor = GridBagConstraints.EAST
            gbc.insets = Insets(2, 10, 2, 0)

            val statusPanel = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.RIGHT, 5, 0))
            statusPanel.isOpaque = false

            val statusLabel = JBLabel("Status:")
            statusLabel.foreground = java.awt.Color.GRAY
            statusLabel.font = statusLabel.font.deriveFont(10f)
            statusPanel.add(statusLabel)

            val statusComboBox = ComboBox<String>()
            statusComboBox.isEditable = false
            statusComboBox.preferredSize = Dimension(120, 24)
            statusComboBox.maximumSize = Dimension(120, 24)

            // Store issue key in client property for later use
            statusComboBox.putClientProperty("issueKey", ticket.key)

            // Set initial value
            statusComboBox.addItem(ticket.status)
            statusComboBox.selectedItem = ticket.status

            // Style the combo box based on status
            val statusColor = when (ticket.status.lowercase()) {
                "done", "closed", "resolved" -> java.awt.Color(0, 150, 0)
                "in progress", "in review" -> java.awt.Color(0, 100, 200)
                else -> java.awt.Color.GRAY
            }
            statusComboBox.foreground = statusColor
            statusComboBox.font = statusComboBox.font.deriveFont(Font.BOLD, 11f)

            // Disable initially - will be enabled after loading transitions
            statusComboBox.isEnabled = false
            statusComboBox.toolTipText = "Loading transitions..."

            statusPanel.add(statusComboBox)
            itemPanel.add(statusPanel, gbc)

            // Load transitions asynchronously
            loadTransitionsForComboBox(ticket, statusComboBox)

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

        private fun createSubtaskForIssue(parentIssueKey: String) {
            val diffResult = diffAnalysisService.analyzeUncommittedChanges()

            if (diffResult == null) {
                Messages.showWarningDialog(
                    project,
                    "No uncommitted changes found.\n\nPlease make some code changes before creating a subtask.",
                    "No Changes"
                )
                return
            }

            // Open dialog with parent issue pre-filled
            val dialog = CreateJiraTicketDialog(project, diffResult, parentIssueKey)
            val dialogResult = dialog.showAndGet()

            // Refresh both panels if dialog was successful
            if (dialogResult) {
                refreshCreatedTicketsPanel()
                refreshAssignedTicketsPanel()
            }
        }

        private fun createTicketFromText() {
            // 1단계: 텍스트 입력 Dialog
            val inputDialog = QuickTextInputDialog(project)
            if (!inputDialog.showAndGet()) {
                return  // 취소
            }

            val inputText = inputDialog.getInputText()

            // 2단계: 텍스트를 diffContent에 넣은 더미 DiffResult 생성
            val textBasedDiffResult = DiffAnalysisService.DiffAnalysisResult(
                filesChanged = 0,  // ← 0이면 Text 모드로 감지
                linesAdded = 0,
                linesDeleted = 0,
                fileList = emptyList(),
                diffContent = inputText,  // ← 여기에 텍스트!
                branchName = null,
                commits = emptyList()
            )

            // 3단계: 기존 CreateJiraTicketDialog 그대로 사용!
            val dialog = CreateJiraTicketDialog(project, textBasedDiffResult)
            val success = dialog.showAndGet()

            // 4단계: 성공 시 패널 갱신
            if (success) {
                refreshCreatedTicketsPanel()
                refreshAssignedTicketsPanel()
            }
        }

        private fun loadTransitionsForComboBox(ticket: RecentIssue, comboBox: ComboBox<String>) {
            Thread {
                val result = jiraApiService.getIssueTransitions(ticket.key)

                javax.swing.SwingUtilities.invokeLater {
                    result.onSuccess { transitions ->
                        // Clear and repopulate combo box
                        comboBox.removeAllItems()

                        // Add current status first
                        comboBox.addItem(ticket.status)

                        // Store transitions as user data for later use
                        val transitionMap = mutableMapOf<String, com.github.gyeom.jiraautomation.model.IssueTransition>()
                        transitionMap[ticket.status] = com.github.gyeom.jiraautomation.model.IssueTransition(
                            id = "",
                            name = ticket.status,
                            to = null
                        )

                        // Add available transitions
                        transitions.forEach { transition ->
                            val targetStatus = transition.to?.name ?: transition.name
                            if (targetStatus != ticket.status) {
                                comboBox.addItem(targetStatus)
                                transitionMap[targetStatus] = transition
                            }
                        }

                        // Enable combo box if there are transitions
                        if (transitions.isNotEmpty()) {
                            comboBox.isEnabled = true
                            comboBox.toolTipText = "Select status to change"

                            // Store transition map in client property
                            comboBox.putClientProperty("transitionMap", transitionMap)
                            comboBox.putClientProperty("currentStatus", ticket.status)

                            // Only add listener if not already added
                            val hasListener = comboBox.getClientProperty("hasListener") as? Boolean ?: false
                            if (!hasListener) {
                                // Add single selection listener
                                comboBox.addActionListener { e ->
                                    val isProcessing = comboBox.getClientProperty("isProcessing") as? Boolean ?: false
                                    if (isProcessing) {
                                        println("Already processing transition, ignoring")
                                        return@addActionListener
                                    }

                                    val selectedStatus = comboBox.selectedItem as? String
                                    val currentStatus = comboBox.getClientProperty("currentStatus") as? String
                                    val storedTransitionMap = comboBox.getClientProperty("transitionMap") as? Map<String, com.github.gyeom.jiraautomation.model.IssueTransition>

                                    if (selectedStatus != null && selectedStatus != currentStatus && storedTransitionMap != null) {
                                        comboBox.putClientProperty("isProcessing", true)
                                        val transition = storedTransitionMap[selectedStatus]
                                        if (transition != null) {
                                            executeTransition(ticket, transition, comboBox)
                                        }
                                        // Reset flag after a delay
                                        javax.swing.Timer(1500) {
                                            comboBox.putClientProperty("isProcessing", false)
                                        }.apply {
                                            isRepeats = false
                                            start()
                                        }
                                    }
                                }
                                comboBox.putClientProperty("hasListener", true)
                            }
                        } else {
                            comboBox.isEnabled = false
                            comboBox.toolTipText = "No transitions available"
                        }
                    }.onFailure { error ->
                        comboBox.isEnabled = false
                        comboBox.toolTipText = "Failed to load transitions: ${error.message}"
                        println("Failed to load transitions for ${ticket.key}: ${error.message}")
                    }
                }
            }.start()
        }

        private fun executeTransition(
            ticket: RecentIssue,
            transition: com.github.gyeom.jiraautomation.model.IssueTransition,
            comboBox: ComboBox<String>
        ) {
            // Check if there are required fields OR if transition has a screen
            val requiredFields = transition.fields?.filter { it.value.required } ?: emptyMap()

            // If hasScreen is true, Jira may require fields even if API says not required
            // So we need to collect common fields like duedate and start date
            val fieldsToCollect = if (transition.hasScreen && transition.fields != null) {
                // For transitions with screens, collect date fields even if not marked required
                transition.fields.filter { (fieldId, field) ->
                    field.required ||
                    fieldId == "duedate" ||
                    fieldId.contains("customfield_10015") ||
                    field.name.contains("Start", ignoreCase = true) ||
                    field.name.contains("기한", ignoreCase = true)
                }
            } else {
                requiredFields
            }

            if (fieldsToCollect.isNotEmpty()) {
                // Show dialog to collect fields
                showRequiredFieldsDialog(ticket, transition, comboBox, fieldsToCollect)
            } else {
                // No fields to collect, proceed directly
                performTransition(ticket, transition, comboBox, null)
            }
        }

        private fun createDatePickerPanel(fieldId: String, fieldName: String): javax.swing.JPanel {
            val panel = javax.swing.JPanel(java.awt.BorderLayout(5, 0))
            val dateField = com.intellij.ui.components.JBTextField(15)
            dateField.toolTipText = "Format: YYYY-MM-DD"
            dateField.text = java.time.LocalDate.now().toString()

            val calendarButton = javax.swing.JButton("📅")
            calendarButton.toolTipText = "Pick a date"
            calendarButton.preferredSize = java.awt.Dimension(40, 24)

            calendarButton.addActionListener {
                val calendar = java.util.Calendar.getInstance()
                val currentDate = dateField.text

                // Try to parse existing date
                if (currentDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                    try {
                        val parts = currentDate.split("-")
                        calendar.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
                    } catch (e: Exception) {
                        // Use current date if parsing fails
                    }
                }

                // Create simple date picker dialog
                val pickerPanel = com.intellij.ui.components.JBPanel<com.intellij.ui.components.JBPanel<*>>(
                    java.awt.GridBagLayout()
                )
                val gbc = java.awt.GridBagConstraints()
                gbc.insets = java.awt.Insets(5, 5, 5, 5)
                gbc.fill = java.awt.GridBagConstraints.HORIZONTAL

                val yearField = com.intellij.ui.components.JBTextField(calendar.get(java.util.Calendar.YEAR).toString(), 6)
                val monthField = com.intellij.ui.components.JBTextField((calendar.get(java.util.Calendar.MONTH) + 1).toString(), 4)
                val dayField = com.intellij.ui.components.JBTextField(calendar.get(java.util.Calendar.DAY_OF_MONTH).toString(), 4)

                gbc.gridx = 0
                gbc.gridy = 0
                pickerPanel.add(com.intellij.ui.components.JBLabel("Year:"), gbc)
                gbc.gridx = 1
                pickerPanel.add(yearField, gbc)

                gbc.gridx = 0
                gbc.gridy = 1
                pickerPanel.add(com.intellij.ui.components.JBLabel("Month:"), gbc)
                gbc.gridx = 1
                pickerPanel.add(monthField, gbc)

                gbc.gridx = 0
                gbc.gridy = 2
                pickerPanel.add(com.intellij.ui.components.JBLabel("Day:"), gbc)
                gbc.gridx = 1
                pickerPanel.add(dayField, gbc)

                val pickerDialog = object : com.intellij.openapi.ui.DialogWrapper(project) {
                    init {
                        title = "Select Date for $fieldName"
                        init()
                    }
                    override fun createCenterPanel() = pickerPanel
                }

                if (pickerDialog.showAndGet()) {
                    try {
                        val year = yearField.text.toInt()
                        val month = monthField.text.toInt()
                        val day = dayField.text.toInt()
                        dateField.text = String.format("%04d-%02d-%02d", year, month, day)
                    } catch (e: Exception) {
                        Messages.showErrorDialog(project, "Invalid date format", "Error")
                    }
                }
            }

            panel.add(dateField, java.awt.BorderLayout.CENTER)
            panel.add(calendarButton, java.awt.BorderLayout.EAST)

            // Store the text field reference so we can retrieve the value later
            panel.putClientProperty("dateField", dateField)

            return panel
        }

        private fun showRequiredFieldsDialog(
            ticket: RecentIssue,
            transition: com.github.gyeom.jiraautomation.model.IssueTransition,
            comboBox: ComboBox<String>,
            requiredFields: Map<String, com.github.gyeom.jiraautomation.model.TransitionField>
        ) {
            val dialog = object : com.intellij.openapi.ui.DialogWrapper(project) {
                private val fieldInputs = mutableMapOf<String, javax.swing.JComponent>()

                init {
                    title = "Required Fields for ${transition.name}"
                    init()
                }

                override fun createCenterPanel(): javax.swing.JComponent {
                    val panel = com.intellij.ui.components.JBPanel<com.intellij.ui.components.JBPanel<*>>(
                        java.awt.GridBagLayout()
                    )
                    val gbc = java.awt.GridBagConstraints()
                    gbc.fill = java.awt.GridBagConstraints.HORIZONTAL
                    gbc.insets = java.awt.Insets(5, 5, 5, 5)
                    gbc.weightx = 1.0

                    var row = 0

                    // Info label
                    gbc.gridx = 0
                    gbc.gridy = row++
                    gbc.gridwidth = 2
                    panel.add(com.intellij.ui.components.JBLabel(
                        "<html>The following fields are required to transition to <b>${transition.to?.name}</b>:</html>"
                    ), gbc)

                    gbc.gridwidth = 1

                    // Sort fields: Start date first, then due date, then others
                    val sortedFields = requiredFields.entries.sortedWith(compareBy { (fieldId, field) ->
                        when {
                            fieldId.contains("customfield_10015") || field.name.contains("Start", ignoreCase = true) -> 0
                            fieldId == "duedate" || field.name.contains("기한", ignoreCase = true) -> 1
                            fieldId == "resolution" -> 2
                            else -> 3
                        }
                    })

                    sortedFields.forEach { (fieldId, field) ->
                        // Label
                        gbc.gridx = 0
                        gbc.gridy = row
                        gbc.weightx = 0.3
                        panel.add(com.intellij.ui.components.JBLabel("${field.name}:"), gbc)

                        // Input field
                        gbc.gridx = 1
                        gbc.weightx = 0.7

                        val input: javax.swing.JComponent = when {
                            // Resolution field with allowed values - use ComboBox
                            fieldId == "resolution" && field.allowedValues != null -> {
                                val comboBox = ComboBox<String>()
                                field.allowedValues.forEach { value ->
                                    val valueMap = value as? Map<*, *>
                                    val name = valueMap?.get("name")?.toString() ?: value.toString()
                                    comboBox.addItem(name)
                                }
                                if (comboBox.itemCount > 0) {
                                    comboBox.selectedIndex = 0
                                }
                                comboBox
                            }
                            // Date fields - use panel with text field + calendar button
                            fieldId == "duedate" || field.schema?.type == "date" ||
                            fieldId.contains("customfield_10015") || field.name.contains("Start") -> {
                                createDatePickerPanel(fieldId, field.name)
                            }
                            else -> {
                                com.intellij.ui.components.JBTextField(30)
                            }
                        }

                        fieldInputs[fieldId] = input
                        panel.add(input, gbc)
                        row++
                    }

                    panel.preferredSize = java.awt.Dimension(500, 100 + (requiredFields.size * 40))
                    return panel
                }

                override fun doOKAction() {
                    // Collect field values
                    val fieldValues = mutableMapOf<String, Any>()

                    requiredFields.forEach { (fieldId, field) ->
                        val input = fieldInputs[fieldId]
                        val value: Any? = when (input) {
                            is com.intellij.ui.components.JBTextField -> input.text.trim()
                            is javax.swing.JPanel -> {
                                // Date picker panel - extract the text field
                                val dateField = input.getClientProperty("dateField") as? com.intellij.ui.components.JBTextField
                                dateField?.text?.trim()
                            }
                            is ComboBox<*> -> {
                                // For resolution field, we need to send an object with id
                                if (fieldId == "resolution") {
                                    val selectedName = input.selectedItem as? String
                                    // Find the resolution ID from allowedValues
                                    val resolutionValue = field.allowedValues?.find { value ->
                                        val valueMap = value as? Map<*, *>
                                        valueMap?.get("name")?.toString() == selectedName
                                    } as? Map<*, *>

                                    if (resolutionValue != null) {
                                        mapOf("id" to resolutionValue["id"]?.toString())
                                    } else {
                                        selectedName
                                    }
                                } else {
                                    input.selectedItem
                                }
                            }
                            else -> null
                        }

                        // Skip empty optional fields
                        if (value == null || (value is String && value.isEmpty())) {
                            if (field.required) {
                                Messages.showErrorDialog(
                                    project,
                                    "Please fill in all required fields: ${field.name}",
                                    "Validation Error"
                                )
                                return
                            }
                            // Skip optional empty fields
                            return@forEach
                        }

                        fieldValues[fieldId] = value
                    }

                    super.doOKAction()

                    // Perform transition with fields
                    performTransition(ticket, transition, comboBox, fieldValues)
                }
            }

            // If dialog is cancelled, restore original selection
            if (!dialog.showAndGet()) {
                comboBox.selectedItem = ticket.status
            }
        }

        private fun performTransition(
            ticket: RecentIssue,
            transition: com.github.gyeom.jiraautomation.model.IssueTransition,
            comboBox: ComboBox<String>,
            fields: Map<String, Any>?
        ) {
            // Disable combo box during transition
            val originalEnabled = comboBox.isEnabled
            comboBox.isEnabled = false
            comboBox.toolTipText = "Updating status..."

            Thread {
                val result = jiraApiService.transitionIssue(ticket.key, transition.id, fields)

                javax.swing.SwingUtilities.invokeLater {
                    result.onSuccess {
                        val newStatus = transition.to?.name ?: "Updated"

                        // Show success notification
                        com.intellij.notification.NotificationGroupManager.getInstance()
                            .getNotificationGroup("Jira Notifications")
                            .createNotification(
                                "Status Changed",
                                "${ticket.key}: ${ticket.status} → $newStatus",
                                com.intellij.notification.NotificationType.INFORMATION
                            )
                            .notify(project)

                        // Update only this ticket's status in the combo box
                        updateTicketStatus(comboBox, newStatus)

                        // Update the ticket object's status
                        // Note: This is a local update - the ticket object in memory is updated
                        // We don't need to refresh the entire list from API
                    }.onFailure { error ->
                        // Restore original selection on failure
                        comboBox.selectedItem = ticket.status
                        comboBox.isEnabled = originalEnabled
                        comboBox.toolTipText = "Select status to change"

                        Messages.showErrorDialog(
                            project,
                            "Failed to change status:\n${error.message}",
                            "Error"
                        )
                    }
                }
            }.start()
        }

        private fun updateTicketStatus(comboBox: ComboBox<String>, newStatus: String) {
            // Update the combo box to show the new status
            comboBox.removeAllItems()
            comboBox.addItem(newStatus)
            comboBox.selectedItem = newStatus

            // Update the status color
            val statusColor = when (newStatus.lowercase()) {
                "done", "closed", "resolved", "완료", "취소" -> java.awt.Color(0, 150, 0)
                "in progress", "in review", "진행 중" -> java.awt.Color(0, 100, 200)
                else -> java.awt.Color.GRAY
            }
            comboBox.foreground = statusColor

            // Disable temporarily while reloading transitions
            comboBox.isEnabled = false
            comboBox.toolTipText = "Reloading transitions..."

            // Reload transitions for the new status
            // We need to get the issue key from somewhere - store it in client property
            val issueKey = comboBox.getClientProperty("issueKey") as? String
            if (issueKey != null) {
                reloadTransitionsAfterUpdate(issueKey, newStatus, comboBox)
            } else {
                // Fallback: just enable with updated status
                comboBox.isEnabled = true
                comboBox.toolTipText = "Select status to change"
            }
        }

        private fun reloadTransitionsAfterUpdate(issueKey: String, currentStatus: String, comboBox: ComboBox<String>) {
            Thread {
                val result = jiraApiService.getIssueTransitions(issueKey)

                javax.swing.SwingUtilities.invokeLater {
                    result.onSuccess { transitions ->
                        // Clear and repopulate combo box
                        comboBox.removeAllItems()

                        // Add current status first
                        comboBox.addItem(currentStatus)

                        // Store transitions as user data for later use
                        val transitionMap = mutableMapOf<String, com.github.gyeom.jiraautomation.model.IssueTransition>()
                        transitionMap[currentStatus] = com.github.gyeom.jiraautomation.model.IssueTransition(
                            id = "",
                            name = currentStatus,
                            to = null
                        )

                        // Add available transitions
                        transitions.forEach { transition ->
                            val targetStatus = transition.to?.name ?: transition.name
                            if (targetStatus != currentStatus) {
                                comboBox.addItem(targetStatus)
                                transitionMap[targetStatus] = transition
                            }
                        }

                        // Enable combo box if there are transitions
                        if (transitions.isNotEmpty()) {
                            comboBox.isEnabled = true
                            comboBox.toolTipText = "Select status to change"

                            // Update stored data in client properties
                            comboBox.putClientProperty("transitionMap", transitionMap)
                            comboBox.putClientProperty("currentStatus", currentStatus)

                            // Listener is already added in initial load, just update the data
                            // No need to add listener again
                        } else {
                            comboBox.isEnabled = false
                            comboBox.toolTipText = "No more transitions available"
                        }
                    }.onFailure { error ->
                        comboBox.isEnabled = true
                        comboBox.toolTipText = "Failed to reload transitions - click refresh"
                        println("Failed to reload transitions for $issueKey: ${error.message}")
                    }
                }
            }.start()
        }
    }
}
