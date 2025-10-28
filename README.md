# jira-automation

![Build](https://github.com/Gyeom/jira-automation/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)

<!-- Plugin description -->
**Jira Automation** is an IntelliJ IDEA plugin that automatically creates Jira tickets from your code changes using AI.

### Features

- **AI-Powered Ticket Generation**: Analyzes your code changes and generates comprehensive Jira tickets automatically
- **Multi-Language Support**: Create tickets in 8 languages (Korean, English, Japanese, Chinese, Spanish, French, German)
- **Smart Diff Analysis**: Automatically detects uncommitted changes and analyzes the impact
- **Seamless Jira Integration**: Direct integration with Jira Cloud via REST API
- **Multiple AI Providers**: Supports OpenAI and Anthropic (Claude) for ticket generation
- **Customizable**: Configure default project, issue type, and language preferences

### How It Works

1. Make code changes in your project
2. Open the "Jira Creator" tool window or use VCS menu
3. Click "Create Ticket from Changes"
4. AI analyzes your changes and generates a title and description
5. Review and customize the generated content
6. Click "Create" to publish to Jira

<!-- Plugin description end -->

## Installation

- **From Source** (Currently):

  1. Clone this repository: `git clone https://github.com/Gyeom/jira-automation.git`
  2. Open in IntelliJ IDEA
  3. Run the Gradle task: `./gradlew buildPlugin`
  4. Install the built plugin from `build/distributions/jira-automation-*.zip`

- **Future - Using the IDE built-in plugin system**:

  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "jira-automation"</kbd> >
  <kbd>Install</kbd>

## Configuration

After installation, configure the plugin:

1. Go to <kbd>Settings/Preferences</kbd> > <kbd>Tools</kbd> > <kbd>Jira Ticket Creator</kbd>

2. **Jira Configuration**:
   - **Jira URL**: Your Jira instance URL (e.g., `https://your-company.atlassian.net`)
   - **Username/Email**: Your Jira account email
   - **API Token**: Generate from [Jira Settings → Security → API tokens](https://id.atlassian.com/manage-profile/security/api-tokens)
   - **Default Project Key**: Your Jira project key (e.g., `PROJ`)
   - **Default Issue Type**: Task, Story, Bug, etc.

3. **AI Configuration**:
   - **AI Provider**: `openai` or `anthropic`
   - **API Key**: Your OpenAI or Anthropic API key
   - **Model**:
     - OpenAI: `gpt-4-turbo`, `gpt-4`, `gpt-3.5-turbo`
     - Anthropic: `claude-3-haiku-20240307`, `claude-3-sonnet-20240229`, `claude-3-opus-20240229`

4. **Language Settings**:
   - **Default Language**: Choose from `ko`, `en`, `ja`, `zh-CN`, `zh-TW`, `es`, `fr`, `de`
   - Enable "Auto-detect from IDE language" for automatic language selection

## Usage

### Method 1: Tool Window

1. Open the "Jira Creator" tool window (right sidebar)
2. Click "Create Ticket from Uncommitted Changes"
3. Review and edit the generated ticket
4. Click "OK" to create the Jira issue

### Method 2: VCS Menu

1. Make your code changes
2. Go to VCS menu → "Create Jira Ticket from Changes"
3. Review and create the ticket

### Example Workflow

```
1. You modify UserService.kt to add authentication
2. Click "Create from Changes" in Jira Creator tool window
3. AI generates:
   Title: [기능] 사용자 인증 로직 구현
   Description:
   ## What was changed
   - UserService에 JWT 기반 인증 추가
   - 로그인/로그아웃 엔드포인트 구현

   ## Why it was changed
   - 보안 강화를 위한 사용자 인증 필요

   ## Impact
   - 기존 API 하위 호환성 유지
   - 새로운 엔드포인트는 인증 필수

4. Review, adjust if needed, and create!
```


## Project Structure

```
src/main/kotlin/com/github/gyeom/jiraautomation/
├── actions/
│   └── CreateJiraFromDiffAction.kt        # VCS menu action
├── model/
│   └── JiraModels.kt                      # Data models for Jira API and AI
├── services/
│   ├── AIService.kt                       # AI integration (OpenAI/Anthropic)
│   ├── DiffAnalysisService.kt            # Git diff analysis
│   └── JiraApiService.kt                  # Jira REST API client
├── settings/
│   ├── JiraSettingsConfigurable.kt       # Settings UI
│   └── JiraSettingsState.kt              # Persistent settings storage
├── toolWindow/
│   └── JiraToolWindowFactory.kt          # Tool window UI
└── ui/
    └── CreateJiraTicketDialog.kt         # Main dialog for ticket creation
```

## Development

### Building from Source

```bash
git clone https://github.com/Gyeom/jira-automation.git
cd jira-automation
./gradlew buildPlugin
```

The plugin will be built to `build/distributions/jira-automation-*.zip`

### Running in Development Mode

```bash
./gradlew runIde
```

This will launch a new IntelliJ IDEA instance with the plugin installed for testing.

### Testing

```bash
./gradlew test
```

## Supported Languages

The plugin can generate Jira tickets in the following languages:

- **Korean** (한국어) - `ko`
- **English** - `en`
- **Japanese** (日本語) - `ja`
- **Chinese Simplified** (简体中文) - `zh-CN`
- **Chinese Traditional** (繁體中文) - `zh-TW`
- **Spanish** (Español) - `es`
- **French** (Français) - `fr`
- **German** (Deutsch) - `de`

## Troubleshooting

### "Failed to connect to Jira"
- Verify your Jira URL is correct (should end with `.atlassian.net` for cloud)
- Check your API token is valid
- Ensure your username/email matches your Jira account

### "AI generation failed"
- Verify your AI provider API key is valid
- Check you have credits/quota remaining
- Try a different model (e.g., switch from gpt-4 to gpt-3.5-turbo)

### "No uncommitted changes found"
- Make sure you have uncommitted changes in your Git repository
- The plugin only works in Git repositories

## License

This project is licensed under the MIT License.

---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
[docs:plugin-description]: https://plugins.jetbrains.com/docs/intellij/plugin-user-experience.html#plugin-description-and-presentation
