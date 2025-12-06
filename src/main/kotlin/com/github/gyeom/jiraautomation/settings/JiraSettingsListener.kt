package com.github.gyeom.jiraautomation.settings

import com.intellij.util.messages.Topic

/**
 * Listener interface for Jira settings changes.
 * Components can subscribe to this topic to be notified when settings are applied.
 */
interface JiraSettingsListener {

    /**
     * Called when settings are successfully applied and validated.
     * @param isValid true if the settings passed validation (connection test successful)
     */
    fun onSettingsChanged(isValid: Boolean)

    companion object {
        val TOPIC = Topic.create("JiraSettingsChanged", JiraSettingsListener::class.java)
    }
}
