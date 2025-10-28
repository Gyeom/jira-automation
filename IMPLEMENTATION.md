# Jira Automation Plugin - Implementation Summary

## 구현 완료 사항

### 1. 핵심 기능

#### ✅ Git Diff 분석
- **DiffAnalysisService**: Git의 uncommitted changes를 자동으로 감지하고 분석
- 파일 변경 목록, 추가/삭제된 라인 수 추출
- 브랜치 정보 및 커밋 메시지 파싱

#### ✅ AI 기반 티켓 생성
- **AIService**: OpenAI 및 Anthropic Claude API 통합
- Diff 정보를 분석하여 Jira 티켓 제목과 설명 자동 생성
- 다국어 지원 (8개 언어)
- JSON 파싱으로 구조화된 응답 처리

#### ✅ Jira API 연동
- **JiraApiService**: Jira Cloud REST API v3 통합
- Basic Authentication (username + API token)
- Issue 생성, 연결 테스트 기능
- Markdown → Jira ADF(Atlassian Document Format) 변환

#### ✅ 설정 관리
- **JiraSettingsState**: Persistent 설정 저장
- **JiraSettingsConfigurable**: Settings UI 제공
- Jira 연결 정보, AI API 키, 언어 설정 등 저장

#### ✅ 사용자 인터페이스
- **CreateJiraTicketDialog**: 티켓 생성 메인 다이얼로그
  - 언어 선택 및 실시간 재생성
  - 제목/설명 수정 가능
  - Project Key, Issue Type, Priority 설정
- **JiraToolWindowFactory**: Tool Window UI
  - 원클릭 티켓 생성
  - Jira 연결 테스트
  - 설정 바로가기
- **CreateJiraFromDiffAction**: VCS 메뉴 액션

### 2. 지원 기능

#### 언어 지원
- Korean (한국어)
- English
- Japanese (日本語)
- Chinese Simplified (简体中文)
- Chinese Traditional (繁體中文)
- Spanish (Español)
- French (Français)
- German (Deutsch)

#### AI Provider 지원
- **OpenAI**
  - Models: gpt-4-turbo, gpt-4, gpt-3.5-turbo
- **Anthropic Claude**
  - Models: claude-3-sonnet, claude-3-opus

### 3. 프로젝트 구조

```
jira-automation/
├── src/main/kotlin/com/github/gyeom/jiraautomation/
│   ├── actions/
│   │   └── CreateJiraFromDiffAction.kt
│   ├── model/
│   │   └── JiraModels.kt
│   ├── services/
│   │   ├── AIService.kt
│   │   ├── DiffAnalysisService.kt
│   │   └── JiraApiService.kt
│   ├── settings/
│   │   ├── JiraSettingsConfigurable.kt
│   │   └── JiraSettingsState.kt
│   ├── toolWindow/
│   │   └── JiraToolWindowFactory.kt
│   └── ui/
│       └── CreateJiraTicketDialog.kt
├── src/main/resources/
│   └── META-INF/
│       └── plugin.xml
├── build.gradle.kts
├── gradle.properties
└── README.md
```

### 4. 의존성

- **IntelliJ Platform SDK**: 2024.3.6
- **Git4Idea**: IntelliJ 번들 플러그인
- **OkHttp**: 4.12.0 (HTTP 클라이언트)
- **Gson**: 2.10.1 (JSON 처리)
- **Kotlin**: 2.1.0

### 5. 사용 워크플로우

1. 개발자가 코드 변경
2. Jira Creator Tool Window 또는 VCS 메뉴에서 "Create Ticket from Changes" 클릭
3. DiffAnalysisService가 Git diff 분석
4. AIService가 diff를 바탕으로 티켓 내용 생성 (선택한 언어로)
5. CreateJiraTicketDialog에서 내용 확인/수정
6. JiraApiService가 Jira에 이슈 생성
7. 생성된 티켓 URL 표시

## 구현된 기능 상세

### DiffAnalysisService
```kotlin
- analyzeUncommittedChanges(): 현재 uncommitted changes 분석
- analyzeChanges(changes, branch): 특정 변경사항 분석
- formatDiffSummary(result): 마크다운 형식으로 diff 요약 생성
- getCurrentBranch(): 현재 브랜치명 가져오기
```

### AIService
```kotlin
- generateTicketFromDiff(summary, diff, language): AI로 티켓 생성
- generateWithOpenAI(): OpenAI API 호출
- generateWithAnthropic(): Anthropic API 호출
- buildPrompt(): 언어별 프롬프트 생성
- parseGeneratedTicket(): AI 응답 파싱
```

### JiraApiService
```kotlin
- createIssue(title, description, projectKey, issueType, priority): Jira 이슈 생성
- testConnection(): Jira 연결 테스트
- convertMarkdownToJiraFormat(): Markdown → Jira ADF 변환
```

### JiraSettingsState
```kotlin
State {
  jiraUrl, jiraApiToken, jiraUsername,
  defaultProjectKey, defaultIssueType,
  aiProvider, aiApiKey, aiModel,
  defaultLanguage, rememberLastLanguage,
  autoDetectLanguage, includeDiffInDescription,
  linkToCommit
}
```

## 빌드 및 테스트

### 빌드
```bash
./gradlew buildPlugin
```
생성 위치: `build/distributions/jira-automation-0.0.1.zip`

### 개발 모드 실행
```bash
./gradlew runIde
```

### 테스트
```bash
./gradlew test
```

## 향후 개선 가능 사항

### Phase 2 (추가 기능)
- [ ] 브랜치 비교 모드 (feature branch vs main)
- [ ] 커밋 히스토리 분석 (여러 커밋 통합)
- [ ] 자동 라벨/컴포넌트 태깅
- [ ] 스토리 포인트 예측
- [ ] 티켓 생성 히스토리 저장

### Phase 3 (최적화)
- [ ] Diff 캐싱으로 성능 개선
- [ ] AI 응답 스트리밍
- [ ] 백그라운드 태스크로 비동기 처리
- [ ] 더 나은 에러 핸들링 및 재시도 로직
- [ ] 단위 테스트 및 통합 테스트 추가

### Phase 4 (확장성)
- [ ] GitHub Issues, Linear 등 다른 이슈 트래커 지원
- [ ] 템플릿 커스터마이징
- [ ] 팀 설정 공유 기능
- [ ] 커스텀 AI 프롬프트 편집기
- [ ] Jira 필드 매핑 커스터마이징

## 알려진 제약사항

1. **Git 전용**: Git repository에서만 동작
2. **Uncommitted Changes**: 현재는 uncommitted changes만 지원 (커밋 비교 기능 미구현)
3. **Diff 크기 제한**: 매우 큰 diff는 AI 토큰 제한으로 잘림 (3000자)
4. **Jira Cloud 전용**: Jira Server/Data Center는 API 차이로 지원 안 됨
5. **ADF 변환**: 복잡한 마크다운 변환은 제한적 (기본적인 헤딩, 리스트만 지원)

## 트러블슈팅

### 일반적인 문제
1. **"No uncommitted changes found"**: Git repo에서 변경사항 만들기
2. **"Jira credentials not configured"**: Settings에서 Jira 설정하기
3. **"AI generation failed"**: AI API 키 및 모델 확인
4. **"Failed to create Jira issue"**: Project Key와 Issue Type 확인

## 참고 자료

- [IntelliJ Platform Plugin SDK](https://plugins.jetbrains.com/docs/intellij/welcome.html)
- [Jira Cloud REST API](https://developer.atlassian.com/cloud/jira/platform/rest/v3/)
- [OpenAI API Documentation](https://platform.openai.com/docs/api-reference)
- [Anthropic Claude API](https://docs.anthropic.com/claude/reference)

## 라이선스

MIT License
