# Architecture

## 1. 목표

이 프로젝트는 특정 웹 서비스에 종속되지 않는 Java 기반 채팅 Moderation Core를 만든다.

현재 예상 실제 서비스 환경:

- Java 21
- Spring Boot 3.4 계열
- WebSocket
- Docker
- Redis/ElastiCache
- PostgreSQL
- Spring AI
- Grafana

하지만 Core는 위 인프라를 몰라야 한다.

## 2. Core Architecture

```text
                     +----------------------+
message ------------>| ChatModerationService|
                     +----------+-----------+
                                |
                                v
                     +----------------------+
                     | MessageNormalizer    |
                     +----------+-----------+
                                |
                                v
                     +----------------------+
                     | KeywordMatcher       |
                     | (Aho-Corasick)       |
                     +----------+-----------+
                                |
                                v
                     +----------------------+
                     | Rule / Regex Filter  |
                     +----------+-----------+
                                |
                                v
                     +----------------------+
                     | ModerationPolicy     |
                     +----------+-----------+
                                |
                                v
                     ALLOW / MASK / BLOCK
```

## 3. 책임

### MessageNormalizer
탐지 전에 문자열을 정규화한다.

초기에는 보수적으로 구현한다.
과도한 정규화로 정상 문장이 차단되는 것을 피한다.

### KeywordMatcher
여러 금칙어 패턴을 한 번에 찾는다.

MVP 구현은 Aho-Corasick을 직접 구현하는 것을 우선 검토한다.

### Rule Filter
정규식 기반 패턴을 담당한다.

예:
- 전화번호
- 이메일
- URL
- 반복 문자

Phase 1에는 넣지 않아도 된다.

### ModerationPolicy
발견된 Match/Reason을 최종 Action으로 변환한다.

예:
- PROFANITY -> BLOCK
- PHONE_NUMBER -> MASK
- URL -> 정책에 따라 BLOCK/ALLOW

Keyword Matcher의 index는 normalized 문자열 기준이므로 reason-only finding으로
정책에 전달한다. Rule Filter는 양끝 공백을 제거한 canonical 원문에서 실행하며,
MASK에는 RuleMatch의 canonical 원문 range만 사용한다. normalized index와 원문
range 사이의 offset mapping은 현재 구현하지 않는다.

## 4. Spring Integration

최종 Spring 프로젝트에서는 Core 외부에 Adapter를 둔다.

```text
WebSocket Handler
      |
      v
Spring Adapter
      |
      v
ChatModerationService
      |
      +------> Redis Rate Limit
      |
      +------> AI Moderation (선택)
      |
      v
Broadcast
```

Core에 `@Service`, `RedisTemplate`, WebSocket API를 직접 넣지 않는다.

## 5. 운영 원칙

- moderation은 broadcast 전에 수행
- automaton은 재사용
- 외부 AI 장애가 채팅 전체 장애로 번지지 않도록 fallback 정책 필요
- 로그에 원문 개인정보를 과도하게 남기지 않기
- 향후 Micrometer metric 연동 가능
