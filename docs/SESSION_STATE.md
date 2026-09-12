# Session State

## 현재 Phase

Phase 2 — Rule Filter + Moderation Policy 완료

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

## 아직 구현하지 않은 것

- ChatModerationService 조합 구현
- Spring 연동
- Redis 연동
- AI Moderation
- WebSocket 연동
- Grafana metric

## 다음 작업

Phase 3을 진행할 때 아래 범위만 구현한다.

1. Normalizer, KeywordMatcher, RuleFilter, Policy 조합
2. 외부 진입점을 `moderate(message)`로 단순화
3. 전체 처리 흐름 integration test
4. broadcast 전에 호출하는 Core 사용 계약 문서화

Spring 관련 코드는 Phase 4 전까지 만들지 않는다.
