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

## Phase 3.7 — Obfuscation Detection

- canonical output과 분리된 detection view
- dictionary-aware 숫자/공백/제한된 separator 제거
- 명시적 shorthand 및 known-variant alias
- exception과 transformed view 상호작용 회귀 테스트

---

## Phase 3.8 — Accuracy Evaluation & Policy Calibration

- regression corpus와 독립 evaluation dataset 분리
- precision/recall/F1, FPR/FNR, reason별 지표
- ALLOW/MASK/BLOCK confusion matrix와 오판 사례 출력
- 고정 조건 benchmark 및 policy calibration baseline

---

## Phase 3.9 — Deterministic Policy Calibration

- calibration/dev dataset과 독립 holdout 분리
- `개년` false-positive 완화
- dictionary-aware `/`와 명시 shorthand/known alias
- 제한 TLD 기반 scheme-less URL
- 고정 구현의 calibration/holdout 비교

---

## Phase 3.10 — Semantic Moderation Experiment

- production core와 분리된 provider abstraction 및 routing layer
- `CLEAR_ALLOW`, `CLEAR_MASK`, `CLEAR_BLOCK`, `NEEDS_SEMANTIC_REVIEW`
- 개인정보 masking 선행과 provider failure 정책 실험
- 108건 semantic dataset에서 deterministic/semantic/hybrid 비교
- credential 없는 local heuristic stand-in; 실제 AI 가치는 후속 실험에서 검증

---

## Phase 3.11 — Real Semantic Moderation Provider Experiment

- production core와 분리된 OpenAI Responses API provider
- frozen `semantic-evaluation.tsv`와 frozen router를 사용하는 5-way 비교
- structured output 및 malformed response의 `UNKNOWN` 처리
- request/success/error/timeout, network latency, token, configurable cost 계측
- credential이 없을 때 전체 test와 기존 task를 보존하는 전용 실행 task

현재 상태:
- provider, runner, unit/privacy/failure 검증 구현 완료
- 실제 credential이 없어 frozen dataset API 실행 및 도입 가치 판단은 보류

완료 조건:
- 실제 provider로 108건 frozen evaluation 실행
- category별 결과, holdout routing rate, latency, token/cost, FP/FN 기록
- deterministic baseline 대비 서비스 도입 가치 판단

---

## Phase 3.11b — OpenAI Free Moderation API Experiment

- `omni-moderation-latest`와 `/v1/moderations`를 사용하는 별도 provider
- prompt 없이 공식 `flagged`, `categories`, `category_scores`를 그대로 평가
- provider-native category 동적 보존 및 집계
- sequential 250ms 간격 호출과 429/timeout/error/network latency 계측
- frozen 108건 5-way 비교 및 holdout routing rate 재계산
- API cost를 유료 Responses provider와 분리해 `$0`으로 보고

현재 상태:
- provider, runner, unit/privacy/rate-limit/failure 검증 및 live 199회 호출 완료
- API-only accuracy 49.07%, precision 100%, recall 14.06%, FPR 0%, FNR 85.94%
- error/timeout/429 0건, API cost $0

완료 조건:
- 실제 API로 frozen 108건 평가
- category 통계, hybrid 성능, latency, 429/error/timeout, FP/FN 기록
- 서비스 전용 의미 판단을 보완할 수 있는지 한계를 포함해 판단

---

## Phase 3.12 — Policy Adjudication and Moderation Score Calibration

- `DIRECT_ABUSE`와 `MENTION_OR_REPORT` 정책 분리
- 기존 semantic 108건을 historical benchmark로 동결
- 독립 합성 calibration 220건과 sealed holdout 165건
- provider-native category score의 positive/negative distribution
- FPR ceiling별 abuse/sexual threshold 후보 탐색
- threshold 선택 후 deterministic/default/custom hybrid holdout 비교

현재 상태:
- 정책, dataset, 분석/선택/holdout runner와 단위 테스트 구현 완료
- credential이 없어 실제 score distribution, threshold와 holdout 결과는 미확정

완료 조건:
- calibration 220건 실제 API score 수집
- FPR ≤ 5% 우선순위로 threshold 확정
- threshold 변경 없이 sealed holdout 165건 평가
- default flagged 대비 개선과 서비스 도입 가치 판단

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
