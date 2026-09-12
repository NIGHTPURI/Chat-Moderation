# Chat Moderation Starter

실시간 익명 채팅에서 메시지가 broadcast 되기 전에 검사하는 **Java 21 기반 재사용형 Moderation Core** 스타터팩입니다.

현재 목표는 Spring, WebSocket, Redis, OpenAI에 바로 종속되지 않는 순수 Java 코어를 먼저 만드는 것입니다.

## 핵심 처리 흐름

```text
Message
  ↓
Normalizer
  ↓
Aho-Corasick Keyword Matcher
  ↓
Rule / Regex Filter
  ↓
Policy
  ↓
ALLOW / MASK / BLOCK
```

추후 통합:

```text
Spring WebSocket
  ↓
ChatModerationService
  ↓
Redis/ElastiCache Rate Limit
  ↓
Spring AI / OpenAI Moderation (선택)
  ↓
Broadcast
```

## 기술 스택

- Java 21
- Gradle
- JUnit 5
- 순수 Java Core 우선
- Spring Boot 의존성은 MVP 이후 추가

## Codex 시작 순서

Codex에서 이 폴더를 연 뒤 다음 파일을 먼저 읽게 하세요.

1. `AGENTS.md`
2. `README.md`
3. `docs/ARCHITECTURE.md`
4. `docs/ROADMAP.md`
5. `docs/SESSION_STATE.md`
6. `docs/CODEX_FIRST_PROMPT.md`

그 다음 `docs/CODEX_FIRST_PROMPT.md`의 프롬프트를 그대로 입력하면 됩니다.

## 현재 상태

아직 Aho-Corasick 구현은 없습니다.

현재 포함된 코드는 **도메인 계약(interface/model)만 정의한 baseline**입니다.
Codex가 Phase 1부터 구현하도록 설계되어 있습니다.

## 로컬 테스트

Gradle이 설치되어 있다면:

```bash
gradle test
```

IntelliJ에서는 Gradle 프로젝트로 열어 JDK 21을 지정하면 됩니다.

## 최종 목표

외부 사용자는 내부 구현을 몰라도 아래 수준으로 사용할 수 있어야 합니다.

```java
ModerationResult result = moderationService.moderate(message);

if (!result.allowed()) {
    return;
}

broadcast(message);
```

핵심 원칙은 **broadcast 전에 반드시 moderation을 수행하는 것**입니다.
