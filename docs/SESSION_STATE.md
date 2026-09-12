# Session State

## 현재 Phase

Phase 3 — ChatModerationService 완료

## 완료된 것

- Java 21 + Gradle 프로젝트 뼈대
- 핵심 모델 정의
- 핵심 interface 정의
- JUnit 5 설정
- Architecture / Roadmap / Codex 작업 규칙
- 보수적인 기본 문자열 정규화 구현
- Aho-Corasick Trie 및 failure link 구현
- 모든 keyword match 반환
- 중복/중첩/접두사/한글/긴 문자열/reuse 테스트
- 전화번호/이메일/URL 탐지 Rule
- 동일 문자 반복 탐지 Rule과 단일 threshold 설정
- Rule 결과 모델 및 기본 Rule Filter
- reason별로 설정 가능한 ALLOW/MASK/BLOCK 정책
- 정상 숫자/날짜/유사 문자열/짧은 반복 false positive 테스트
- Normalizer, Keyword Matcher, Rule Filter, Policy 조합 서비스
- keyword 목록 사전 normalization 및 automaton 재사용
- normalized keyword reason-only finding과 canonical RuleMatch range 분리
- ALLOW/MASK/BLOCK 전체 흐름 integration test

## 아직 구현하지 않은 것

- Spring 연동
- Redis 연동
- AI Moderation
- WebSocket 연동
- Grafana metric

## 다음 작업

Phase 4를 진행할 때 Core와 분리된 Spring Adapter를 설계한다.

1. YoungManRest_BE에서 Core를 포함할 방식 결정
2. keyword 설정을 읽어 서비스 인스턴스를 한 번 구성
3. WebSocket broadcast 전에 `moderate(message)` 호출
4. ALLOW/MASK만 `outputMessage()`로 broadcast하고 BLOCK은 중단

original ↔ normalized offset mapping, Redis, AI moderation은 각각의 이후 Phase까지
추가하지 않는다.
