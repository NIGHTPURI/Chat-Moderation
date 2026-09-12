# Roadmap

## Phase 0 — Baseline
현재 상태.

- Gradle Java Library 프로젝트
- 모델/interface 정의
- 기본 테스트 환경
- 문서/작업 규칙

완료 조건:
- 프로젝트 구조를 이해할 수 있다.
- `./gradlew test`가 통과한다.

---

## Phase 1 — Normalizer + Aho-Corasick

구현:
- DefaultMessageNormalizer
- AhoCorasickKeywordMatcher
- Trie Node
- Failure Link BFS
- Match 반환
- 중복/중첩 패턴 처리
- 충분한 JUnit 테스트

금지:
- Spring
- Redis
- OpenAI
- WebSocket
- DB

완료 조건:
- 금칙어 목록을 한 번 구축한 후 여러 메시지에서 재사용 가능
- 한글 테스트 통과
- 중첩 패턴 테스트 통과
- 시간복잡도 설명 가능

---

## Phase 2 — Rule Filter + Policy

구현:
- 전화번호 탐지
- 이메일 탐지
- URL 탐지
- 반복 문자/도배성 문자열 규칙
- ModerationReason
- ModerationPolicy
- ALLOW/MASK/BLOCK 결정

완료 조건:
- 정책을 코드 한 군데에서 조정 가능
- rule별 단위 테스트 존재

---

## Phase 3 — ChatModerationService

구현:
- Normalizer
- KeywordMatcher
- RuleFilter
- Policy 조합
- 외부에서는 `moderate(message)` 하나만 호출

완료 조건:
- 사용자가 내부 구조를 몰라도 사용 가능
- integration test 존재

---

## Phase 3.5 — Local Validation Harness

- 수동 Playground
- corpus parameterized test
- 개발용 benchmark
- backend persistence 계약 simulation

---

## Phase 3.6 — Dictionary & False Positive Refinement

- PROFANITY / SEXUAL_CONTENT 사전 분리
- 단일 automaton에서 category metadata 유지
- keyword별 false-positive exception
- 애매한 문맥 단어는 unconditional BLOCK에서 제외

---

## Phase 4 — Spring Adapter

별도 모듈 또는 패키지로 추가 고려.

구현 후보:
- Spring Configuration
- Bean 등록
- WebSocket 적용 예시
- application.yml 기반 설정

Core와 분리할 것.

---

## Phase 5 — Redis / ElastiCache Rate Limit

구현:
- 익명 사용자 ID 기준 rate limit
- TTL counter
- spam/temp block 정책

Core 알고리즘과 분리.

---

## Phase 6 — AI Moderation

필요할 때만 추가.

구현 후보:
- Spring AI 또는 OpenAI Moderation
- timeout
- circuit breaker/fallback
- AI 호출 비율 측정

모든 메시지를 생성형 LLM으로 판단하지 않는다.

---

## Phase 7 — Observability

- Micrometer
- moderation count
- block reason count
- latency
- AI call ratio
- Grafana dashboard 연동
