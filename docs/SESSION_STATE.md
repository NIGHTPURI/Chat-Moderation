# Session State

## 현재 Phase

Phase 3.14 — High-Recall Semantic Router Redesign 완료, 성공 기준 미달

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
- normalized baseline과 dictionary-aware separator-collapse detection view
- 숫자, 공백, `.`, `-`, `_`, `*` 삽입 탐지
- 명시적 alias (`ㅅㅂ`, `ㅆㅂ`, `ㅈㄴ`, `씨아발`)
- regression과 분리된 320건 evaluation dataset 및 분석 runner
- action confusion matrix와 overall/reason/category accuracy 지표
- 50건 고정 dataset, warmup 20회, 측정 200회 benchmark
- policy calibration 및 deterministic/semantic 개선 후보 분류
- unconditional `개년` 제거와 강한 phrase 사전
- dictionary-aware `/` separator와 명시 shorthand/known variant alias
- hostname 경계 및 제한 TLD 기반 scheme-less URL rule
- 구현 고정 후 평가한 독립 holdout 160건
- production core와 분리된 semantic provider abstraction 및 명시적 review router
- 108건 semantic context dataset과 3-way 비교 evaluation runner
- 개인정보 masking 선행 및 FAIL_OPEN/FAIL_CLOSED/DETERMINISTIC_FALLBACK 실험
- OpenAI Responses API 기반 실제 provider 격리 구현
- `gpt-5.6-luna` 기본 모델과 strict JSON schema 응답 계약
- malformed/non-2xx/timeout의 UNKNOWN 안전 처리
- frozen 108건의 5-way 비교 runner와 category별 accuracy 출력
- 실제 API request/success/error/timeout, network latency, token/cost 계측
- `OPENAI_API_KEY`가 없을 때 real evaluation만 안내 후 skip
- 실제 provider transport까지 MASK된 개인정보만 전달하는 단위 테스트
- `omni-moderation-latest` `/v1/moderations` provider 격리 구현
- `flagged`, 동적 `categories`, `category_scores` 보존과 native category 통계
- provider-native category를 core reason에 억지로 매핑하지 않고 hybrid에서 `OTHER` 처리
- 순차 호출과 250ms 간격, HTTP status/429/timeout/network latency 계측
- 무료 Moderation API 전용 5-way frozen evaluation task
- Moderation API serialized body까지 MASK된 개인정보만 전달하는 테스트
- Moderation API frozen 108건 live 199회 호출 완료, error/timeout/429 0건, 비용 $0
- Moderation API-only accuracy 49.07%, precision 100%, recall 14.06%, FPR 0%, FNR 85.94%
- `DIRECT_ABUSE` / `MENTION_OR_REPORT` 정책 계약과 historical ambiguous label 목록
- 기존 108건과 분리된 calibration 220건 및 sealed holdout 165건
- native category score positive/negative min/median/p90/p95/max 분석
- abuse/sexual threshold grid와 calibration FPR ceiling 기반 선택기
- threshold 선택 이후에만 holdout을 로드하는 5-way evaluation runner
- Phase 3.12 live default/custom threshold 실험 완료
- Phase 3.13 direct/report policy pair를 포함한 frozen Luna baseline prompt
- 동일 Luna 판정을 공유하는 Luna-only/frozen-router 평가 runner
- semantic candidate recall, routed/missed BLOCK 및 router 손실 사례 출력
- Responses API 429와 total token telemetry
- 실제 usage/configurable 가격 기반 평균 및 월 운영 비용 simulation
- Phase 3.13 Luna policy holdout accuracy 98.79%, recall 97.33%, FPR 0%
- 기존 frozen router + Luna recall 24.00%, semantic candidate recall 25.33%
- historical dataset과 분리된 router calibration 336건과 sealed holdout 224건
- `LOCAL_FINAL` / `NEEDS_SEMANTIC_REVIEW` 전용 high-recall router
- calibration candidate recall 100%, routing rate 50%, routed ALLOW 0
- sealed holdout candidate recall 87.50%, routing rate 43.75%, missed BLOCK 14
- frozen router 대비 candidate recall +57.14 percentage point
- historical Luna request cost 기반 월 1만~1000만 비용 projection
- holdout 95% 기준 미달로 production 후보 미채택

## 아직 구현하지 않은 것

- Spring 연동
- Redis 연동
- 새 router holdout의 optional 실제 Luna A/B/C 비교
- WebSocket 연동
- Grafana metric

## 다음 작업

현재 Phase 3.14 router는 sealed holdout candidate recall 95% 기준에 미달했으므로
production에 연결하지 않는다. 실제 Luna A/B/C 확인이 필요하면 `OPENAI_API_KEY`를 process
environment에만 설정하고 `./gradlew highRecallRouterLunaEvaluation`을 실행한다. Router와
holdout은 더 이상 수정하지 않는다. 다음 router iteration은 새로운 Phase와 새로운
calibration/holdout으로 시작해야 한다. YoungManRest_BE,
original ↔ normalized offset mapping, Redis와 Spring adapter는 별도 Phase로 유지한다.
