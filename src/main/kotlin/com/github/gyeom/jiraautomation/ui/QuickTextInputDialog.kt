package com.github.gyeom.jiraautomation.ui

import com.github.gyeom.jiraautomation.model.OutputLanguage
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel

class QuickTextInputDialog(
    private val project: Project,
    initialText: String = ""
) : DialogWrapper(project) {

    private val inputTextArea = JBTextArea(20, 60)
    private val languageComboBox = ComboBox(OutputLanguage.values())

    init {
        title = "Create Jira Ticket from Text"
        init()

        inputTextArea.text = initialText
        inputTextArea.lineWrap = true
        inputTextArea.wrapStyleWord = true

        // Set default language
        val settings = com.github.gyeom.jiraautomation.settings.JiraSettingsState.getInstance(project)
        val defaultLang = OutputLanguage.fromCode(settings.state.defaultLanguage)
        languageComboBox.selectedItem = defaultLang
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, 10))

        // Info label
        val infoLabel = JBLabel("Paste meeting notes, emails, Slack messages, or any text to create a Jira ticket")
        infoLabel.foreground = java.awt.Color.GRAY
        panel.add(infoLabel, BorderLayout.NORTH)

        // Text input area
        val scrollPane = JBScrollPane(inputTextArea)
        scrollPane.preferredSize = Dimension(700, 400)
        panel.add(scrollPane, BorderLayout.CENTER)

        // Language selection at bottom
        val bottomPanel = JPanel(BorderLayout(5, 0))
        bottomPanel.add(JBLabel("Output Language:"), BorderLayout.WEST)
        bottomPanel.add(languageComboBox, BorderLayout.CENTER)
        panel.add(bottomPanel, BorderLayout.SOUTH)

        return panel
    }

    fun getInputText(): String = inputTextArea.text.trim()

    fun getLanguage(): OutputLanguage = languageComboBox.selectedItem as OutputLanguage

    override fun getOKAction() = super.getOKAction().apply {
        putValue(Action.NAME, "Continue")
    }

    override fun doOKAction() {
        if (inputTextArea.text.trim().isEmpty()) {
            com.intellij.openapi.ui.Messages.showWarningDialog(
                project,
                "Please enter some text to create a ticket.",
                "No Input"
            )
            return
        }
        super.doOKAction()
    }
}
