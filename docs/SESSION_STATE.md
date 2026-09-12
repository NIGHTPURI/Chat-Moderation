# Session State

## 현재 Phase

Phase 3.6 — Moderation Dictionary & False Positive Refinement 완료

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
- 210건의 명시적 corpus와 parameterized test
- 수동 Moderation Playground
- 비게이팅 System.nanoTime benchmark runner
- YoungManRest_BE persistence 계약 simulation test
- PROFANITY/SEXUAL_CONTENT category 사전과 기본 BLOCK 정책
- category와 exception을 함께 구축한 단일 Aho-Corasick automaton
- keyword별 false-positive exception (`시발` -> `시발점`)

## 아직 구현하지 않은 것

- Spring 연동
- Redis 연동
- AI Moderation
- WebSocket 연동
- Grafana metric

## 다음 작업

Phase 3.7에서 별도 normalization 정책과 false-positive 회귀 테스트를 먼저 설계한다.

1. 숫자 삽입: `씨1발`, `지1랄`, `병1신`, `섹1스`
2. 공백 삽입: `씨 발`
3. 초성 표현: `ㅅㅂ`, `ㅆㅂ`
4. 자모 변형, 유사문자, leetspeak

그 이후 Phase 4에서 YoungManRest_BE와 Core를 분리한 Spring Adapter를 설계한다.
original ↔ normalized offset mapping, Redis, AI moderation은 각각의 이후 Phase까지
추가하지 않는다.
