package com.github.gyeom.jiraautomation.ui

import com.github.gyeom.jiraautomation.model.*
import com.github.gyeom.jiraautomation.services.AIService
import com.github.gyeom.jiraautomation.services.DiffAnalysisService
import com.github.gyeom.jiraautomation.services.JiraApiService
import com.github.gyeom.jiraautomation.services.TicketHistoryService
import com.github.gyeom.jiraautomation.settings.JiraSettingsState
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.TextFieldWithAutoCompletion
import com.intellij.ui.TextFieldWithAutoCompletionListProvider
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.*

class CreateJiraTicketDialog(
    private val project: Project,
    private val diffResult: DiffAnalysisService.DiffAnalysisResult,
    private val parentIssueKey: String? = null  // Optional parent issue key for creating subtasks
) : DialogWrapper(project) {

    private val settings = JiraSettingsState.getInstance(project)
    private val aiService = project.service<AIService>()
    private val jiraApiService = project.service<JiraApiService>()
    private val diffAnalysisService = project.service<DiffAnalysisService>()
    private val historyService = project.service<TicketHistoryService>()

    private val languageComboBox = ComboBox(OutputLanguage.values())
    private val titleField = JBTextField(60)
    private val descriptionArea = JBTextArea(15, 60)

    // ComboBoxes with search functionality
    private val projectKeyComboBox = ComboBox<ProjectItem>()
    private val issueTypeComboBox = ComboBox<IssueTypeItem>()
    private val priorityComboBox = ComboBox<PriorityItem>()
    private val assigneeComboBox = ComboBox<JiraUser>()
    private val reporterComboBox = ComboBox<JiraUser>()
    private val epicComboBox = ComboBox<Epic>()
    // Sprint removed - customfield not available in all projects
    // private val sprintComboBox = ComboBox<Sprint>()

    // ComboBoxes for labels and components like Epic
    private val labelsComboBox = ComboBox<String>()
    private val componentsComboBox = ComboBox<JiraComponent>()
    private val availableLabels = mutableListOf<String>()

    // Time tracking fields
    private val storyPointsField = JBTextField(10)
    private val originalEstimateField = JBTextField(20)

    // Date pickers
    private val startDatePicker = com.intellij.ui.components.fields.ExtendableTextField()
    private val dueDatePicker = com.intellij.ui.components.fields.ExtendableTextField()

    // Subtask fields
    private val parentIssueField = JBTextField(20)
    private val searchParentButton = JButton("Search")
    private val validateParentButton = JButton("Validate")
    private var validatedParent: ParentIssue? = null

    private var isGenerating = false
    private var isLoadingMetadata = false
    private var currentUser: JiraUser? = null
    private val assigneeSearchTimer = Timer(300) { searchUsers(true) }
    private val reporterSearchTimer = Timer(300) { searchUsers(false) }
    private var isSearchingUsers = false

    // Loading state tracking
    private var isLoadingProjects = false
    private var isLoadingIssueTypes = false
    private var isLoadingPriorities = false
    private var isLoadingEpics = false
    private var isLoadingUsers = false
    private var isLoadingComponents = false
    private var isLoadingLabels = false

    // Loading indicator UI (will be set in createCenterPanel)
    private lateinit var contentPanel: JPanel
    private lateinit var loadingPanel: com.intellij.ui.components.JBLoadingPanel

    // Store all loaded data for filtering
    private var allSprints = listOf<Sprint>()
    private var allComponents = listOf<JiraComponent>()
    private var regularIssueTypes = listOf<IssueTypeItem>()
    private var subtaskIssueTypes = listOf<IssueTypeItem>()

    // Data classes for ComboBox items
    data class ProjectItem(val key: String, val name: String) {
        override fun toString() = "$key - $name"
    }

    data class IssueTypeItem(val id: String, val name: String) {
        override fun toString() = name
    }

    data class PriorityItem(val id: String, val name: String) {
        override fun toString() = name
    }

    init {
        title = "Create Jira Ticket from Code Changes"
        init()

        // Make project combo editable for filtering and manual entry
        projectKeyComboBox.isEditable = true
        setupProjectComboBoxFilter()

        // Make user combo boxes editable for search
        assigneeComboBox.isEditable = true
        reporterComboBox.isEditable = true
        setupUserComboBoxes()

        // Setup Epic combo box
        epicComboBox.isEditable = true
        setupEpicComboBox()

        // Sprint removed - customfield not available in all projects
        // setupSprintComboBox()

        // Setup Labels and Components combo boxes
        labelsComboBox.isEditable = true
        setupLabelsComboBox()

        componentsComboBox.isEditable = true
        setupComponentsComboBox()

        // Set default language
        val defaultLang = OutputLanguage.fromCode(settings.state.defaultLanguage)
        languageComboBox.selectedItem = defaultLang

        // Setup listeners
        projectKeyComboBox.addActionListener { loadIssueTypesForProject() }

        // Load current user first, then load other metadata
        SwingUtilities.invokeLater {
            loadCurrentUserThenMetadata()
            generateTicket()

            // If parent issue key is provided, auto-configure for subtask creation
            parentIssueKey?.let { parentKey ->
                parentIssueField.text = parentKey
                // Auto-validate the parent issue
                validateParentIssue()
            }
        }
    }

    private fun setupProjectComboBoxFilter() {
        // Add a simple filter functionality using the editor
        val editor = projectKeyComboBox.editor
        if (editor?.editorComponent is JTextField) {
            val textField = editor.editorComponent as JTextField
            textField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
                override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = filterProjects()
                override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = filterProjects()
                override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = filterProjects()

                private fun filterProjects() {
                    val text = textField.text.lowercase()
                    if (text.isEmpty()) return

                    // Simple filtering - shows popup when typing
                    if (!projectKeyComboBox.isPopupVisible && projectKeyComboBox.itemCount > 0) {
                        projectKeyComboBox.showPopup()
                    }
                }
            })
        }
    }

    override fun createCenterPanel(): JComponent {
        // Create the main content panel
        contentPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = Insets(5, 5, 5, 5)

        var row = 0

        // Language selection
        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        contentPanel.add(JBLabel("Output Language:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        val languagePanel = JPanel(BorderLayout(5, 0))
        languagePanel.add(languageComboBox, BorderLayout.WEST)

        val regenerateButton = JButton("Regenerate")
        regenerateButton.addActionListener { generateTicket() }
        languagePanel.add(regenerateButton, BorderLayout.EAST)

        contentPanel.add(languagePanel, gbc)
        row++

        // Title
        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        contentPanel.add(JBLabel("Title:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        contentPanel.add(titleField, gbc)
        row++

        // Description
        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        gbc.anchor = GridBagConstraints.NORTHWEST
        contentPanel.add(JBLabel("Description:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        gbc.weighty = 1.0
        gbc.fill = GridBagConstraints.BOTH
        descriptionArea.lineWrap = true
        descriptionArea.wrapStyleWord = true
        val descScrollPane = JBScrollPane(descriptionArea)
        descScrollPane.preferredSize = Dimension(600, 300)
        contentPanel.add(descScrollPane, gbc)
        row++

        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weighty = 0.0
        gbc.anchor = GridBagConstraints.WEST

        // Separator
        gbc.gridx = 0
        gbc.gridy = row
        gbc.gridwidth = 2
        contentPanel.add(JSeparator(), gbc)
        gbc.gridwidth = 1
        row++

        // Project Key
        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        contentPanel.add(JBLabel("Project Key:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        contentPanel.add(projectKeyComboBox, gbc)
        row++

        // Issue Type
        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        contentPanel.add(JBLabel("Issue Type:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        contentPanel.add(issueTypeComboBox, gbc)
        row++

        // Parent Issue (optional - for creating subtasks)
        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        contentPanel.add(JBLabel("Parent Issue (optional):"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        val parentPanel = JPanel(BorderLayout(5, 0))
        parentIssueField.toolTipText = "Enter parent issue key to create as subtask (e.g., PROJECT-123)"
        parentPanel.add(parentIssueField, BorderLayout.CENTER)
        val parentButtonPanel = JPanel()
        parentButtonPanel.layout = BoxLayout(parentButtonPanel, BoxLayout.X_AXIS)
        parentButtonPanel.add(searchParentButton)
        parentButtonPanel.add(Box.createHorizontalStrut(5))
        parentButtonPanel.add(validateParentButton)
        parentPanel.add(parentButtonPanel, BorderLayout.EAST)
        contentPanel.add(parentPanel, gbc)
        row++

        // Setup search button listener
        searchParentButton.addActionListener {
            showParentIssueSearch()
        }

        // Setup validate button listener
        validateParentButton.addActionListener {
            validateParentIssue()
        }

        // Add listener to parent field to detect clearing
        parentIssueField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = checkParentField()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = checkParentField()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = checkParentField()

            private fun checkParentField() {
                if (parentIssueField.text.trim().isEmpty() && validatedParent != null) {
                    // Reset when parent field is cleared
                    validatedParent = null

                    // Restore regular issue types
                    issueTypeComboBox.removeAllItems()
                    regularIssueTypes.forEach { issueType ->
                        issueTypeComboBox.addItem(issueType)
                    }

                    issueTypeComboBox.isEnabled = true
                    projectKeyComboBox.isEnabled = true

                    println("Parent cleared - restored to regular issue types")
                }
            }
        })

        // Priority
        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        contentPanel.add(JBLabel("Priority:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        contentPanel.add(priorityComboBox, gbc)
        row++

        // Epic Link
        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        contentPanel.add(JBLabel("Epic Link:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        contentPanel.add(epicComboBox, gbc)
        row++

        // Sprint removed - customfield not available in all projects
        /*
        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        contentPanel.add(JBLabel("Sprint:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        contentPanel.add(sprintComboBox, gbc)
        row++
        */

        // Separator for Assignment section
        gbc.gridx = 0
        gbc.gridy = row
        gbc.gridwidth = 2
        contentPanel.add(JSeparator(), gbc)
        gbc.gridwidth = 1
        row++

        // Assignee
        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        contentPanel.add(JBLabel("Assignee:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        val assigneePanel = JPanel(BorderLayout(5, 0))
        assigneePanel.add(assigneeComboBox, BorderLayout.CENTER)
        val assignToMeButton = JButton("Assign to me")
        assignToMeButton.addActionListener {
            currentUser?.let { assigneeComboBox.selectedItem = it }
        }
        assigneePanel.add(assignToMeButton, BorderLayout.EAST)
        contentPanel.add(assigneePanel, gbc)
        row++

        // Reporter
        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        contentPanel.add(JBLabel("Reporter:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        contentPanel.add(reporterComboBox, gbc)
        row++

        // Separator for Labels and Components section
        gbc.gridx = 0
        gbc.gridy = row
        gbc.gridwidth = 2
        contentPanel.add(JSeparator(), gbc)
        gbc.gridwidth = 1
        row++

        // Labels (like Epic)
        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        contentPanel.add(JBLabel("Labels:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        contentPanel.add(labelsComboBox, gbc)
        row++

        // Components (like Epic)
        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        contentPanel.add(JBLabel("Components:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        contentPanel.add(componentsComboBox, gbc)
        row++

        // Separator for Time tracking section
        gbc.gridx = 0
        gbc.gridy = row
        gbc.gridwidth = 2
        contentPanel.add(JSeparator(), gbc)
        gbc.gridwidth = 1
        row++

        // Story Points
        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        contentPanel.add(JBLabel("Story Points:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        storyPointsField.toolTipText = "Enter story points (e.g., 3, 5, 8)"
        contentPanel.add(storyPointsField, gbc)
        row++

        // Original Estimate
        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        contentPanel.add(JBLabel("Original Estimate:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        originalEstimateField.toolTipText = "Enter time (e.g., 3h, 2d, 1w)"
        contentPanel.add(originalEstimateField, gbc)
        row++

        // Start Date with date picker
        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        contentPanel.add(JBLabel("Start Date:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        startDatePicker.toolTipText = "Select or enter date (YYYY-MM-DD)"
        // Set start date to today
        val today = java.time.LocalDate.now()
        startDatePicker.text = today.toString()
        val startCalendarButton = JButton("\uD83D\uDCC5")
        startCalendarButton.toolTipText = "Pick a date"
        startCalendarButton.addActionListener {
            showDatePicker(startDatePicker, "Select Start Date")
        }
        val startDatePanel = JPanel(BorderLayout(5, 0))
        startDatePanel.add(startDatePicker, BorderLayout.CENTER)
        startDatePanel.add(startCalendarButton, BorderLayout.EAST)
        contentPanel.add(startDatePanel, gbc)
        row++

        // Due Date with date picker
        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        contentPanel.add(JBLabel("Due Date:"), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        dueDatePicker.toolTipText = "Select or enter date (YYYY-MM-DD)"
        // Set due date to today + 7 days
        val dueDate = java.time.LocalDate.now().plusDays(7)
        dueDatePicker.text = dueDate.toString()
        val dueCalendarButton = JButton("\uD83D\uDCC5")
        dueCalendarButton.toolTipText = "Pick a date"
        dueCalendarButton.addActionListener {
            showDatePicker(dueDatePicker, "Select Due Date")
        }
        val dueDatePanel = JPanel(BorderLayout(5, 0))
        dueDatePanel.add(dueDatePicker, BorderLayout.CENTER)
        dueDatePanel.add(dueCalendarButton, BorderLayout.EAST)
        contentPanel.add(dueDatePanel, gbc)
        row++

        // Wrap in scroll pane with reasonable size
        val mainScrollPane = JBScrollPane(contentPanel)
        mainScrollPane.preferredSize = Dimension(900, 700)
        mainScrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        mainScrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER

        // Wrap scroll pane in loading panel for center overlay
        loadingPanel = com.intellij.ui.components.JBLoadingPanel(BorderLayout(), disposable)
        loadingPanel.add(mainScrollPane)

        return loadingPanel
    }

    private fun loadJiraMetadata() {
        if (isLoadingMetadata) return
        isLoadingMetadata = true
        isLoadingProjects = true
        SwingUtilities.invokeLater { updateLoadingIndicator() }

        Thread {
            try {
                // Load projects
                val projectsResult = jiraApiService.getProjects()
                projectsResult.onSuccess { projects ->
                    SwingUtilities.invokeLater {
                        projectKeyComboBox.removeAllItems()
                        val projectItems = projects.map { project ->
                            ProjectItem(project.key, project.name)
                        }
                        projectItems.forEach { projectKeyComboBox.addItem(it) }

                        // Select last used project if exists, otherwise use default
                        val lastKey = settings.state.lastUsedProjectKey.takeIf { it.isNotEmpty() }
                            ?: settings.state.defaultProjectKey
                        val projectToSelect = projectItems.find { it.key == lastKey }
                        if (projectToSelect != null) {
                            projectKeyComboBox.selectedItem = projectToSelect
                        }

                        isLoadingProjects = false
                        updateLoadingIndicator()

                        // Load issue types for selected project
                        loadIssueTypesForProject()
                    }
                }.onFailure { error ->
                    SwingUtilities.invokeLater {
                        isLoadingProjects = false
                        updateLoadingIndicator()

                        // Fall back to manual entry with last used or default value
                        val fallbackKey = settings.state.lastUsedProjectKey.takeIf { it.isNotEmpty() }
                            ?: settings.state.defaultProjectKey
                        projectKeyComboBox.removeAllItems()
                        projectKeyComboBox.addItem(
                            ProjectItem(fallbackKey, "Manual Entry")
                        )
                        println("Failed to load projects: ${error.message}")
                    }
                }

                // Don't load global priorities here - they will be loaded per project
                // Just set initial state
                SwingUtilities.invokeLater {
                    priorityComboBox.removeAllItems()
                    priorityComboBox.addItem(PriorityItem("", "(No Priority)"))
                    println("Priorities will be loaded when project is selected")
                }

            } catch (e: Exception) {
                println("Error loading Jira metadata: ${e.message}")
            } finally {
                isLoadingMetadata = false
            }
        }.start()
    }

    private fun loadIssueTypesForProject() {
        val selectedProject = projectKeyComboBox.selectedItem as? ProjectItem ?: return

        // Set loading states
        isLoadingComponents = true
        isLoadingLabels = true
        isLoadingIssueTypes = true
        isLoadingPriorities = true
        SwingUtilities.invokeLater { updateLoadingIndicator() }

        Thread {
            // Load components for the project
            println("Loading components for project: ${selectedProject.key}")
            val componentsResult = jiraApiService.getProjectComponents(selectedProject.key)
            componentsResult.onSuccess { components ->
                allComponents = components
                println("Successfully loaded ${components.size} components")
                SwingUtilities.invokeLater {
                    componentsComboBox.removeAllItems()
                    componentsComboBox.addItem(null) // "(No Component)"
                    components.forEach { comp ->
                        componentsComboBox.addItem(comp)
                    }
                    // Select last used component if exists
                    val lastComponentId = settings.state.lastUsedComponentId
                    if (lastComponentId.isNotEmpty()) {
                        val lastComponent = components.find { it.id == lastComponentId }
                        if (lastComponent != null) {
                            componentsComboBox.selectedItem = lastComponent
                        } else {
                            componentsComboBox.selectedIndex = 0
                        }
                    } else {
                        componentsComboBox.selectedIndex = 0
                    }

                    isLoadingComponents = false
                    updateLoadingIndicator()
                }
            }.onFailure { error ->
                println("ERROR: Failed to load components: ${error.message}")
                SwingUtilities.invokeLater {
                    allComponents = emptyList()
                    componentsComboBox.removeAllItems()
                    componentsComboBox.addItem(null)

                    isLoadingComponents = false
                    updateLoadingIndicator()
                }
            }

            // Load labels for the project
            val labelsResult = jiraApiService.getProjectLabels(selectedProject.key)
            labelsResult.onSuccess { labels ->
                availableLabels.clear()
                availableLabels.addAll(labels)
                SwingUtilities.invokeLater {
                    labelsComboBox.removeAllItems()
                    labelsComboBox.addItem(null) // "(No Label)"
                    labels.forEach { label ->
                        labelsComboBox.addItem(label)
                    }
                    // Select last used label if exists
                    val lastLabel = settings.state.lastUsedLabel
                    if (lastLabel.isNotEmpty() && labels.contains(lastLabel)) {
                        labelsComboBox.selectedItem = lastLabel
                    } else {
                        labelsComboBox.selectedIndex = 0
                    }
                    println("Loaded ${labels.size} labels for project ${selectedProject.key}")

                    isLoadingLabels = false
                    updateLoadingIndicator()
                }
            }.onFailure { error ->
                println("Failed to load labels: ${error.message}")
                SwingUtilities.invokeLater {
                    availableLabels.clear()
                    labelsComboBox.removeAllItems()
                    labelsComboBox.addItem(null)

                    isLoadingLabels = false
                    updateLoadingIndicator()
                }
            }

            // Load issue types
            val issueTypesResult = jiraApiService.getIssueTypesForProject(selectedProject.key)
            issueTypesResult.onSuccess { issueTypes ->
                SwingUtilities.invokeLater {
                    issueTypeComboBox.removeAllItems()

                    // Separate regular and subtask types
                    val regularTypes = issueTypes.filter { !it.subtask }
                    val subtaskTypes = issueTypes.filter { it.subtask }

                    // Store both types for later use
                    regularIssueTypes = regularTypes.map { IssueTypeItem(it.id, it.name) }
                    subtaskIssueTypes = subtaskTypes.map { IssueTypeItem(it.id, it.name) }

                    // Check if we're in subtask mode (parent is validated)
                    if (validatedParent != null) {
                        // Maintain subtask mode - add only subtask types
                        println("Parent is validated, loading subtask types only")
                        subtaskIssueTypes.forEach { subtaskType ->
                            issueTypeComboBox.addItem(subtaskType)
                        }
                        if (subtaskIssueTypes.isNotEmpty()) {
                            issueTypeComboBox.selectedIndex = 0
                        }
                        issueTypeComboBox.isEnabled = false
                    } else {
                        // Normal mode - add regular types
                        regularIssueTypes.forEach { issueType ->
                            issueTypeComboBox.addItem(issueType)
                        }

                        // Select last used issue type if exists, otherwise use default
                        val lastType = settings.state.lastUsedIssueType.takeIf { it.isNotEmpty() }
                            ?: settings.state.defaultIssueType
                        for (i in 0 until issueTypeComboBox.itemCount) {
                            val item = issueTypeComboBox.getItemAt(i)
                            if (item.name == lastType) {
                                issueTypeComboBox.selectedIndex = i
                                break
                            }
                        }
                    }

                    println("Loaded ${regularTypes.size} regular issue types and ${subtaskTypes.size} subtask types")
                    if (subtaskTypes.isNotEmpty()) {
                        println("Subtask types available: ${subtaskTypes.map { it.name }.joinToString(", ")}")
                    }

                    isLoadingIssueTypes = false
                    updateLoadingIndicator()
                }
            }.onFailure { error ->
                SwingUtilities.invokeLater {
                    // Fall back to common issue types
                    issueTypeComboBox.removeAllItems()
                    arrayOf("Task", "Story", "Bug", "Epic").forEach {
                        issueTypeComboBox.addItem(IssueTypeItem(it, it))
                    }
                    println("Failed to load issue types: ${error.message}")

                    isLoadingIssueTypes = false
                    updateLoadingIndicator()
                }
            }

            // Load project-specific priorities
            val prioritiesResult = jiraApiService.getProjectPriorities(selectedProject.key)
            prioritiesResult.onSuccess { priorities ->
                SwingUtilities.invokeLater {
                    priorityComboBox.removeAllItems()
                    priorityComboBox.addItem(PriorityItem("", "(No Priority)"))
                    priorities.forEach { priority ->
                        priorityComboBox.addItem(PriorityItem(priority.id, priority.name))
                    }
                    // Select last used priority if exists
                    val lastPriorityId = settings.state.lastUsedPriorityId
                    if (lastPriorityId.isNotEmpty()) {
                        for (i in 0 until priorityComboBox.itemCount) {
                            val item = priorityComboBox.getItemAt(i)
                            if (item.id == lastPriorityId) {
                                priorityComboBox.selectedIndex = i
                                break
                            }
                        }
                    }

                    isLoadingPriorities = false
                    updateLoadingIndicator()
                }
            }.onFailure { error ->
                SwingUtilities.invokeLater {
                    // Fall back to no priority selection
                    priorityComboBox.removeAllItems()
                    priorityComboBox.addItem(PriorityItem("", "(No Priority)"))
                    println("Failed to load project priorities: ${error.message}")

                    isLoadingPriorities = false
                    updateLoadingIndicator()
                }
            }

            // Load assignable users for the project
            loadAssignableUsers()

            // Load epics for the project
            loadEpics()

            // Sprint removed - customfield not available in all projects
            // loadSprints()
        }.start()
    }

    private fun updateLoadingIndicator() {
        val loadingItems = mutableListOf<String>()

        if (isLoadingProjects) loadingItems.add("Projects")
        if (isLoadingIssueTypes) loadingItems.add("Issue Types")
        if (isLoadingPriorities) loadingItems.add("Priorities")
        if (isLoadingEpics) loadingItems.add("Epics")
        if (isLoadingUsers) loadingItems.add("Users")
        if (isLoadingComponents) loadingItems.add("Components")
        if (isLoadingLabels) loadingItems.add("Labels")

        if (loadingItems.isEmpty()) {
            loadingPanel.stopLoading()
        } else {
            val loadingText = "Loading: ${loadingItems.joinToString(", ")}"
            loadingPanel.setLoadingText(loadingText)
            loadingPanel.startLoading()
        }
    }

    private fun generateTicket() {
        if (isGenerating) return

        isGenerating = true
        titleField.text = "Generating..."
        descriptionArea.text = "Please wait while AI generates the ticket content..."
        titleField.isEnabled = false
        descriptionArea.isEnabled = false

        Thread {
            try {
                val language = languageComboBox.selectedItem as OutputLanguage

                // Use AI provider and model from settings
                println("Generating ticket with AI: ${settings.state.aiProvider} / ${settings.state.aiModel}")

                val diffSummary = diffAnalysisService.formatDiffSummary(diffResult)
                val result = aiService.generateTicketFromDiff(
                    diffSummary,
                    diffResult.diffContent,
                    language
                )

                SwingUtilities.invokeLater {
                    result.onSuccess { ticket ->
                        titleField.text = ticket.title
                        descriptionArea.text = ticket.description
                    }.onFailure { error ->
                        titleField.text = "[AI Error] Code Changes"
                        descriptionArea.text = "Failed to generate with AI: ${error.message}\n\n" +
                                "Please edit manually.\n\n$diffSummary"
                        Messages.showErrorDialog(
                            project,
                            "Failed to generate ticket: ${error.message}",
                            "AI Generation Error"
                        )
                    }

                    titleField.isEnabled = true
                    descriptionArea.isEnabled = true
                    isGenerating = false
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    titleField.text = "Error generating ticket"
                    descriptionArea.text = e.message ?: "Unknown error"
                    titleField.isEnabled = true
                    descriptionArea.isEnabled = true
                    isGenerating = false
                }
            }
        }.start()
    }

    private fun showParentIssueSearch() {
        val searchQuery = parentIssueField.text.trim().ifEmpty { "" }

        val searchDialog = object : DialogWrapper(project) {
            private val searchField = JBTextField(30)
            private val resultsList = JBList<ParentIssue>()
            private var searchResults = listOf<ParentIssue>()

            init {
                title = "Search Parent Issue"
                init()

                searchField.text = searchQuery

                // Setup search on text change
                val searchTimer = Timer(500) { performSearch() }
                searchTimer.isRepeats = false

                searchField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
                    override fun insertUpdate(e: javax.swing.event.DocumentEvent?) {
                        searchTimer.restart()
                    }
                    override fun removeUpdate(e: javax.swing.event.DocumentEvent?) {
                        searchTimer.restart()
                    }
                    override fun changedUpdate(e: javax.swing.event.DocumentEvent?) {
                        searchTimer.restart()
                    }
                })

                // Setup list renderer
                resultsList.cellRenderer = object : DefaultListCellRenderer() {
                    override fun getListCellRendererComponent(
                        list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
                    ): Component {
                        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                        if (value is ParentIssue) {
                            text = "${value.key} - ${value.summary} [${value.status}]"
                        }
                        return this
                    }
                }

                // Double-click to select
                resultsList.addMouseListener(object : java.awt.event.MouseAdapter() {
                    override fun mouseClicked(e: java.awt.event.MouseEvent?) {
                        if (e?.clickCount == 2) {
                            doOKAction()
                        }
                    }
                })

                // Initial search if there's text
                if (searchQuery.isNotEmpty()) {
                    performSearch()
                }
            }

            override fun createCenterPanel(): JComponent {
                val panel = JPanel(BorderLayout(0, 10))

                // Search field
                val searchPanel = JPanel(BorderLayout(5, 0))
                searchPanel.add(JBLabel("Search:"), BorderLayout.WEST)
                searchPanel.add(searchField, BorderLayout.CENTER)
                panel.add(searchPanel, BorderLayout.NORTH)

                // Results list
                val scrollPane = JBScrollPane(resultsList)
                scrollPane.preferredSize = Dimension(600, 400)
                panel.add(scrollPane, BorderLayout.CENTER)

                return panel
            }

            private fun performSearch() {
                val query = searchField.text.trim()
                if (query.length < 2) {
                    resultsList.setListData(emptyArray())
                    return
                }

                Thread {
                    val selectedProject = projectKeyComboBox.selectedItem as? ProjectItem
                    val result = jiraApiService.searchIssues(query, selectedProject?.key, 20)

                    SwingUtilities.invokeLater {
                        result.onSuccess { issues ->
                            searchResults = issues
                            resultsList.setListData(issues.toTypedArray())
                        }.onFailure { error ->
                            println("Search failed: ${error.message}")
                            resultsList.setListData(emptyArray())
                        }
                    }
                }.start()
            }

            override fun doOKAction() {
                val selected = resultsList.selectedValue
                if (selected != null) {
                    parentIssueField.text = selected.key
                    super.doOKAction()
                    // Auto-validate after selection
                    validateParentIssue()
                }
            }
        }

        searchDialog.show()
    }

    private fun validateParentIssue() {
        val parentKey = parentIssueField.text.trim()

        if (parentKey.isEmpty()) {
            Messages.showWarningDialog(project, "Please enter a parent issue key", "Validation")
            return
        }

        validateParentButton.isEnabled = false
        validateParentButton.text = "Validating..."

        Thread {
            val result = jiraApiService.validateParentIssue(parentKey)

            SwingUtilities.invokeLater {
                validateParentButton.isEnabled = true
                validateParentButton.text = "Validate"

                result.onSuccess { parent ->
                    validatedParent = parent

                    // Clear and reload issue type combo with subtask types
                    issueTypeComboBox.removeAllItems()

                    // Add subtask types
                    if (subtaskIssueTypes.isNotEmpty()) {
                        subtaskIssueTypes.forEach { subtaskType ->
                            issueTypeComboBox.addItem(subtaskType)
                        }
                        issueTypeComboBox.selectedIndex = 0 // Select first subtask type
                        issueTypeComboBox.isEnabled = false // Lock to subtask type
                        println("Auto-selected Sub-task issue type: ${subtaskIssueTypes[0].name}")
                    } else {
                        // No subtask types found - show error
                        Messages.showErrorDialog(
                            project,
                            "Sub-task issue type not found in project '${parent.projectKey}'.\n\n" +
                                    "This project may not support subtasks.\n" +
                                    "Please check your Jira project configuration.",
                            "Subtask Not Supported"
                        )
                        validatedParent = null
                        parentIssueField.text = ""
                        return@invokeLater
                    }

                    // Auto-fill project from parent
                    val parentProjectItem = (0 until projectKeyComboBox.itemCount)
                        .map { projectKeyComboBox.getItemAt(it) }
                        .find { it.key == parent.projectKey }

                    if (parentProjectItem != null) {
                        projectKeyComboBox.selectedItem = parentProjectItem
                        projectKeyComboBox.isEnabled = false // Lock project to parent's project
                    }

                    // Auto-inherit Epic from parent if available
                    parent.epicKey?.let { parentEpicKey ->
                        println("Auto-inheriting Epic from parent: $parentEpicKey")
                        // Find and select the epic in combo box
                        for (i in 0 until epicComboBox.itemCount) {
                            val epic = epicComboBox.getItemAt(i)
                            if (epic?.key == parentEpicKey) {
                                epicComboBox.selectedIndex = i
                                println("Epic auto-selected: ${epic.key}")
                                break
                            }
                        }
                    }

                    val epicInfo = parent.epicKey?.let { "\nEpic: $it" } ?: ""

                    Messages.showInfoMessage(
                        project,
                        "Parent Issue: ${parent.key}\n" +
                                "Summary: ${parent.summary}\n" +
                                "Project: ${parent.projectKey}\n" +
                                "Status: ${parent.status}$epicInfo\n\n" +
                                "This will be created as a Subtask.",
                        "Parent Issue Validated"
                    )
                }.onFailure { error ->
                    validatedParent = null
                    // Re-enable fields if validation fails
                    issueTypeComboBox.isEnabled = true
                    projectKeyComboBox.isEnabled = true
                    Messages.showErrorDialog(
                        project,
                        "Failed to validate parent issue:\n${error.message}",
                        "Validation Error"
                    )
                }
            }
        }.start()
    }

    override fun doOKAction() {
        val title = titleField.text.trim()
        val description = descriptionArea.text.trim()

        val selectedProject = projectKeyComboBox.selectedItem as? ProjectItem
        val projectKey = selectedProject?.key
            ?: projectKeyComboBox.editor?.item?.toString()?.trim()
            ?: ""

        val issueType = (issueTypeComboBox.selectedItem as? IssueTypeItem)?.name
            ?: issueTypeComboBox.editor?.item?.toString()?.trim()
            ?: "Task"

        println("DEBUG: Issue Type from combo: $issueType")
        println("DEBUG: Issue Type combo box item count: ${issueTypeComboBox.itemCount}")
        println("DEBUG: Validated parent: ${validatedParent?.key}")
        println("DEBUG: Parent field text: ${parentIssueField.text.trim()}")

        // Use priority ID instead of name for Jira API
        val selectedPriority = priorityComboBox.selectedItem as? PriorityItem
        val priority = if (selectedPriority != null && selectedPriority.id.isNotEmpty()) {
            println("Selected priority: id=${selectedPriority.id}, name=${selectedPriority.name}")
            selectedPriority.id  // Use ID for API call
        } else {
            println("No priority selected")
            null
        }

        if (title.isEmpty()) {
            Messages.showErrorDialog(project, "Title is required", "Validation Error")
            return
        }

        if (projectKey.isEmpty()) {
            Messages.showErrorDialog(project, "Project Key is required", "Validation Error")
            return
        }

        // Check if creating subtask (parent field is filled)
        val parentKey = if (parentIssueField.text.trim().isNotEmpty()) {
            if (validatedParent == null) {
                Messages.showErrorDialog(
                    project,
                    "Please validate the parent issue before creating.\nClick the 'Validate' button next to the Parent Issue field.",
                    "Validation Error"
                )
                return
            }

            // Verify project matches parent
            if (validatedParent!!.projectKey != projectKey) {
                Messages.showErrorDialog(
                    project,
                    "Subtask must be in the same project as parent issue.\n" +
                            "Parent project: ${validatedParent!!.projectKey}\n" +
                            "Selected project: $projectKey",
                    "Validation Error"
                )
                return
            }

            validatedParent!!.key
        } else {
            null
        }

        // Get assignee and reporter
        val assignee = assigneeComboBox.selectedItem as? JiraUser
        val reporter = reporterComboBox.selectedItem as? JiraUser
        val epic = epicComboBox.selectedItem as? Epic
        // Sprint removed
        // val sprint = sprintComboBox.selectedItem as? Sprint

        // Get selected label and component
        val selectedLabel = labelsComboBox.selectedItem as? String
        val labels = if (selectedLabel != null) listOf(selectedLabel) else emptyList()

        val selectedComponent = componentsComboBox.selectedItem as? JiraComponent
        val selectedComponents = if (selectedComponent != null) listOf(selectedComponent) else emptyList()

        // Get time tracking fields
        val storyPoints = storyPointsField.text.trim().toDoubleOrNull()
        val originalEstimate = originalEstimateField.text.trim().takeIf { it.isNotEmpty() }
        val startDate = startDatePicker.text.trim().takeIf { it.isNotEmpty() }
        val dueDate = dueDatePicker.text.trim().takeIf { it.isNotEmpty() }

        // Create Jira issue
        val result = jiraApiService.createIssue(
            title = title,
            description = description,
            projectKey = projectKey,
            issueType = issueType,
            priority = priority,
            assigneeAccountId = assignee?.accountId,
            reporterAccountId = reporter?.accountId,
            parentKey = parentKey,  // Add parent key for subtasks
            epicKey = epic?.key,
            sprintId = null,  // Sprint removed
            labels = labels,
            components = selectedComponents,
            storyPoints = storyPoints,
            originalEstimate = originalEstimate,
            startDate = startDate,
            dueDate = dueDate
        )

        result.onSuccess { issue ->
            val jiraUrl = "${settings.state.jiraUrl}/browse/${issue.key}"

            // Save to history
            historyService.addTicket(
                TicketHistory(
                    key = issue.key,
                    title = title,
                    url = jiraUrl,
                    projectKey = projectKey,
                    issueType = issueType
                )
            )

            // Save last used values for next time
            settings.state.lastUsedProjectKey = projectKey
            settings.state.lastUsedIssueType = issueType
            settings.state.lastUsedPriorityId = priority ?: ""
            settings.state.lastUsedEpicKey = epic?.key ?: ""
            settings.state.lastUsedLabel = selectedLabel ?: ""
            settings.state.lastUsedComponentId = selectedComponent?.id ?: ""

            Messages.showInfoMessage(
                project,
                "Jira ticket created successfully!\n\nKey: ${issue.key}\nURL: $jiraUrl",
                "Success"
            )
            super.doOKAction()
        }.onFailure { error ->
            Messages.showErrorDialog(
                project,
                "Failed to create Jira ticket:\n${error.message}",
                "Error"
            )
        }
    }

    private fun setupUserComboBoxes() {
        // Setup custom renderer for both combo boxes
        val userRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?, value: Any?,
                index: Int, isSelected: Boolean, cellHasFocus: Boolean
            ): Component {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                when (value) {
                    is JiraUser -> text = value.getDisplayText()
                    is String -> text = value  // For when user is typing
                }
                return this
            }
        }

        assigneeComboBox.renderer = userRenderer
        reporterComboBox.renderer = userRenderer

        // Setup search functionality for assignee
        setupUserSearchField(assigneeComboBox, assigneeSearchTimer, true)

        // Setup search functionality for reporter
        setupUserSearchField(reporterComboBox, reporterSearchTimer, false)

        assigneeSearchTimer.isRepeats = false
        reporterSearchTimer.isRepeats = false
    }

    private fun setupUserSearchField(comboBox: ComboBox<JiraUser>, timer: Timer, isAssignee: Boolean) {
        val editor = comboBox.editor
        if (editor?.editorComponent is JTextField) {
            val textField = editor.editorComponent as JTextField

            // Add placeholder text
            val placeholderText = if (isAssignee) "Search assignee by name or email..." else "Search reporter by name or email..."

            textField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
                override fun insertUpdate(e: javax.swing.event.DocumentEvent?) {
                    handleUserInput(comboBox, timer)
                }
                override fun removeUpdate(e: javax.swing.event.DocumentEvent?) {
                    handleUserInput(comboBox, timer)
                }
                override fun changedUpdate(e: javax.swing.event.DocumentEvent?) {
                    handleUserInput(comboBox, timer)
                }
            })

            // Add focus listener for placeholder behavior
            textField.addFocusListener(object : java.awt.event.FocusAdapter() {
                override fun focusGained(e: java.awt.event.FocusEvent?) {
                    if (textField.text.isEmpty() && comboBox.itemCount == 0) {
                        // Load initial users when focused
                        loadInitialUsers(isAssignee)
                    }
                }
            })
        }
    }

    private fun handleUserInput(comboBox: ComboBox<JiraUser>, timer: Timer) {
        val text = (comboBox.editor?.item as? String) ?: ""
        if (text.length >= 2) {
            // Only search if user typed at least 2 characters
            timer.restart()
        } else if (text.isEmpty()) {
            // Clear results when empty
            timer.stop()
        }
    }

    private fun loadCurrentUserThenMetadata() {
        Thread {
            val result = jiraApiService.getCurrentUser()
            result.onSuccess { user ->
                SwingUtilities.invokeLater {
                    currentUser = user.toJiraUser()
                    println("Current user loaded: ${currentUser?.displayName}")

                    // Add current user to combo boxes immediately
                    assigneeComboBox.addItem(currentUser)
                    assigneeComboBox.selectedItem = currentUser
                    reporterComboBox.addItem(currentUser)
                    reporterComboBox.selectedItem = currentUser

                    // Now load metadata which will call loadAssignableUsers
                    loadJiraMetadata()
                }
            }.onFailure { error ->
                println("Failed to load current user: ${error.message}")
                // Still load metadata even if current user fails
                SwingUtilities.invokeLater {
                    loadJiraMetadata()
                }
            }
        }.start()
    }

    private fun loadCurrentUser() {
        Thread {
            val result = jiraApiService.getCurrentUser()
            result.onSuccess { user ->
                SwingUtilities.invokeLater {
                    currentUser = user.toJiraUser()

                    // Find if current user is already in the combo boxes
                    var assigneeHasCurrentUser = false
                    for (i in 0 until assigneeComboBox.itemCount) {
                        val item = assigneeComboBox.getItemAt(i)
                        if (item?.accountId == currentUser?.accountId) {
                            assigneeComboBox.selectedIndex = i
                            assigneeHasCurrentUser = true
                            break
                        }
                    }

                    var reporterHasCurrentUser = false
                    for (i in 0 until reporterComboBox.itemCount) {
                        val item = reporterComboBox.getItemAt(i)
                        if (item?.accountId == currentUser?.accountId) {
                            reporterComboBox.selectedIndex = i
                            reporterHasCurrentUser = true
                            break
                        }
                    }

                    // If not in combo boxes yet, add and select
                    if (!assigneeHasCurrentUser) {
                        assigneeComboBox.insertItemAt(currentUser, 0)
                        assigneeComboBox.selectedIndex = 0
                    }
                    if (!reporterHasCurrentUser) {
                        reporterComboBox.insertItemAt(currentUser, 0)
                        reporterComboBox.selectedIndex = 0
                    }
                }
            }
        }.start()
    }

    private fun searchUsers(isAssignee: Boolean) {
        if (isSearchingUsers) return  // Prevent concurrent searches

        val comboBox = if (isAssignee) assigneeComboBox else reporterComboBox
        val query = (comboBox.editor?.item as? String) ?: return
        if (query.length < 2) return

        isSearchingUsers = true

        val selectedProject = projectKeyComboBox.selectedItem as? ProjectItem
        val projectKey = selectedProject?.key

        // Show loading state
        SwingUtilities.invokeLater {
            val currentText = comboBox.editor?.item as? String ?: ""
            comboBox.removeAllItems()
            // Keep the search text
            comboBox.editor?.item = currentText
        }

        Thread {
            try {
                // Search users with the query
                val result = jiraApiService.searchUsers(query, projectKey, maxResults = 50)
                result.onSuccess { users ->
                    SwingUtilities.invokeLater {
                        // Store current text to restore after updating items
                        val currentText = comboBox.editor?.item as? String ?: ""

                        // Clear and add new items
                        comboBox.removeAllItems()

                        if (users.isEmpty()) {
                            // Show "no results" message
                            comboBox.hidePopup()
                            // Could add a placeholder item indicating no results
                        } else {
                            // Add search results
                            users.forEach { user ->
                                comboBox.addItem(user)
                            }

                            // Keep the typed text in the editor
                            comboBox.editor?.item = currentText

                            // Show popup with results
                            if (!comboBox.isPopupVisible) {
                                comboBox.showPopup()
                            }
                        }
                    }
                }.onFailure { error ->
                    SwingUtilities.invokeLater {
                        val currentText = comboBox.editor?.item as? String ?: ""
                        comboBox.removeAllItems()
                        comboBox.editor?.item = currentText
                        println("Failed to search users: ${error.message}")
                    }
                }
            } finally {
                isSearchingUsers = false
            }
        }.start()
    }

    private fun loadInitialUsers(isAssignee: Boolean) {
        val comboBox = if (isAssignee) assigneeComboBox else reporterComboBox
        val selectedProject = projectKeyComboBox.selectedItem as? ProjectItem ?: return

        Thread {
            // Load assignable users for quick selection
            val result = jiraApiService.getAssignableUsers(selectedProject.key)
            result.onSuccess { users ->
                SwingUtilities.invokeLater {
                    if (comboBox.itemCount == 0) {  // Only populate if still empty
                        users.take(10).forEach { user ->  // Show first 10 users
                            comboBox.addItem(user)
                        }
                        if (isAssignee && currentUser != null) {
                            // Add current user at the top if not already there
                            val hasCurrentUser = (0 until comboBox.itemCount).any {
                                (comboBox.getItemAt(it) as? JiraUser)?.accountId == currentUser?.accountId
                            }
                            if (!hasCurrentUser) {
                                comboBox.insertItemAt(currentUser, 0)
                            }
                        }
                    }
                }
            }
        }.start()
    }

    private fun loadAssignableUsers() {
        val selectedProject = projectKeyComboBox.selectedItem as? ProjectItem ?: return

        isLoadingUsers = true
        SwingUtilities.invokeLater { updateLoadingIndicator() }

        Thread {
            val result = jiraApiService.getAssignableUsers(selectedProject.key)
            result.onSuccess { users ->
                SwingUtilities.invokeLater {
                    println("loadAssignableUsers called, currentUser: ${currentUser?.displayName}")

                    // Don't load all users - too many for large organizations
                    // Only show current user and a few common ones
                    assigneeComboBox.removeAllItems()
                    reporterComboBox.removeAllItems()

                    // Add current user first if available
                    if (currentUser != null) {
                        val matchingUser = users.find { it.accountId == currentUser?.accountId }
                        if (matchingUser != null) {
                            println("Adding current user as first item: ${matchingUser.displayName}")
                            assigneeComboBox.addItem(matchingUser)
                            reporterComboBox.addItem(matchingUser)
                            // Select current user as default
                            assigneeComboBox.selectedIndex = 0
                            reporterComboBox.selectedIndex = 0
                        } else {
                            println("Current user not found in assignable users, adding directly")
                            assigneeComboBox.addItem(currentUser)
                            reporterComboBox.addItem(currentUser)
                            assigneeComboBox.selectedIndex = 0
                            reporterComboBox.selectedIndex = 0
                        }
                    } else {
                        println("WARNING: currentUser is null in loadAssignableUsers!")
                    }

                    // Add a few recent/common users (first 5 that aren't current user)
                    users.filter { it.accountId != currentUser?.accountId }
                        .take(5)
                        .forEach { user ->
                            assigneeComboBox.addItem(user)
                            reporterComboBox.addItem(user)
                        }

                    println("Final assignee selection: ${assigneeComboBox.selectedItem?.let { (it as JiraUser).displayName }}")

                    isLoadingUsers = false
                    updateLoadingIndicator()
                }
            }
        }.start()
    }

    private fun setupEpicComboBox() {
        // Setup epic combo box renderer
        epicComboBox.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?, value: Any?,
                index: Int, isSelected: Boolean, cellHasFocus: Boolean
            ): Component {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                if (value is Epic) {
                    text = "${value.key} - ${value.name}"
                    toolTipText = value.summary
                }
                return this
            }
        }

        // Add "(No Epic)" option
        epicComboBox.addItem(null)
    }

    private fun loadEpics() {
        val selectedProject = projectKeyComboBox.selectedItem as? ProjectItem ?: return

        isLoadingEpics = true
        SwingUtilities.invokeLater { updateLoadingIndicator() }

        Thread {
            val result = jiraApiService.getEpics(selectedProject.key)
            result.onSuccess { epics ->
                SwingUtilities.invokeLater {
                    epicComboBox.removeAllItems()
                    // Add "(No Epic)" option
                    epicComboBox.addItem(null)
                    epics.forEach { epic ->
                        epicComboBox.addItem(epic)
                    }
                    // Select last used epic if exists
                    val lastEpicKey = settings.state.lastUsedEpicKey
                    if (lastEpicKey.isNotEmpty()) {
                        val lastEpic = epics.find { it.key == lastEpicKey }
                        if (lastEpic != null) {
                            epicComboBox.selectedItem = lastEpic
                        } else {
                            epicComboBox.selectedIndex = 0
                        }
                    } else {
                        epicComboBox.selectedIndex = 0  // Select "(No Epic)" by default
                    }

                    isLoadingEpics = false
                    updateLoadingIndicator()
                }
            }.onFailure { error ->
                SwingUtilities.invokeLater {
                    epicComboBox.removeAllItems()
                    epicComboBox.addItem(null) // Just "(No Epic)" option
                    println("Note: Epic loading not available for this project (${error.message})")

                    isLoadingEpics = false
                    updateLoadingIndicator()
                }
            }
        }.start()
    }

    private fun setupLabelsComboBox() {
        // Setup renderer for labels
        labelsComboBox.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?, value: Any?,
                index: Int, isSelected: Boolean, cellHasFocus: Boolean
            ): Component {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                when (value) {
                    null -> text = "(No Label)"
                    is String -> text = value
                }
                return this
            }
        }
        labelsComboBox.addItem(null) // Add "(No Label)" option
    }

    private fun setupComponentsComboBox() {
        // Setup renderer for components
        componentsComboBox.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?, value: Any?,
                index: Int, isSelected: Boolean, cellHasFocus: Boolean
            ): Component {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                when (value) {
                    null -> text = "(No Component)"
                    is JiraComponent -> {
                        text = value.name
                        toolTipText = value.description ?: value.name
                    }
                }
                return this
            }
        }
        componentsComboBox.addItem(null) // Add "(No Component)" option
    }

    private fun showDatePicker(dateField: com.intellij.ui.components.fields.ExtendableTextField, dialogTitle: String) {
        // Simple date picker dialog
        val calendar = java.util.Calendar.getInstance()
        val currentDate = dateField.text

        // Try to parse existing date
        if (currentDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
            val parts = currentDate.split("-")
            calendar.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
        }

        // Create a simple date selection dialog
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        gbc.insets = Insets(5, 5, 5, 5)
        gbc.fill = GridBagConstraints.HORIZONTAL

        val yearField = JBTextField(calendar.get(java.util.Calendar.YEAR).toString(), 6)
        val monthField = JBTextField((calendar.get(java.util.Calendar.MONTH) + 1).toString(), 4)
        val dayField = JBTextField(calendar.get(java.util.Calendar.DAY_OF_MONTH).toString(), 4)

        gbc.gridx = 0
        gbc.gridy = 0
        panel.add(JBLabel("Year:"), gbc)
        gbc.gridx = 1
        panel.add(yearField, gbc)

        gbc.gridx = 0
        gbc.gridy = 1
        panel.add(JBLabel("Month:"), gbc)
        gbc.gridx = 1
        panel.add(monthField, gbc)

        gbc.gridx = 0
        gbc.gridy = 2
        panel.add(JBLabel("Day:"), gbc)
        gbc.gridx = 1
        panel.add(dayField, gbc)

        val dialog = object : DialogWrapper(project) {
            init {
                title = dialogTitle
                init()
            }

            override fun createCenterPanel(): JComponent = panel
        }

        if (dialog.showAndGet()) {
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

    /*
    private fun setupSprintComboBox() {
        // Setup sprint combo box renderer
        sprintComboBox.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?, value: Any?,
                index: Int, isSelected: Boolean, cellHasFocus: Boolean
            ): Component {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                when (value) {
                    null -> text = "(Backlog)"
                    is Sprint -> {
                        val icon = when (value.state.uppercase()) {
                            "ACTIVE" -> "⚡"
                            "FUTURE" -> "📅"
                            "CLOSED" -> "✓"
                            else -> "📋"
                        }
                        text = "$icon ${value.name}"
                        if (value.startDate != null && value.endDate != null) {
                            val start = value.startDate.substringBefore("T")
                            val end = value.endDate.substringBefore("T")
                            toolTipText = "${value.name} ($start - $end)"
                        }
                    }
                }
                return this
            }
        }

        // Add "(Backlog)" option
        sprintComboBox.addItem(null)

        // Sprint search is handled via editable ComboBox - no need for custom filtering
        // Users can type to search through the dropdown naturally
    }

    private fun loadSprints() {
        val selectedProject = projectKeyComboBox.selectedItem as? ProjectItem ?: return

        Thread {
            val result = jiraApiService.getSprints(selectedProject.key)
            result.onSuccess { sprints ->
                // Store all sprints for filtering (only Active and Future)
                allSprints = sprints.filter {
                    val state = it.state.uppercase()
                    state == "ACTIVE" || state == "FUTURE"
                }.sortedWith(compareBy(
                    { it.state.uppercase() != "ACTIVE" },  // Active first
                    { it.name }
                ))

                SwingUtilities.invokeLater {
                    sprintComboBox.removeAllItems()
                    sprintComboBox.addItem(null)  // "(Backlog)"

                    allSprints.forEach { sprint ->
                        sprintComboBox.addItem(sprint)
                    }

                    // Select active sprint if available, otherwise backlog
                    val activeSprint = allSprints.find { it.state.uppercase() == "ACTIVE" }
                    if (activeSprint != null) {
                        sprintComboBox.selectedItem = activeSprint
                    } else {
                        sprintComboBox.selectedIndex = 0  // Select "(Backlog)"
                    }
                }
            }.onFailure { error ->
                SwingUtilities.invokeLater {
                    allSprints = emptyList()
                    sprintComboBox.removeAllItems()
                    sprintComboBox.addItem(null) // Just "(Backlog)" option
                    println("Note: Sprint loading not available for this project (${error.message})")
                }
            }
        }.start()
    }
    */
}