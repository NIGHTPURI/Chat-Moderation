# Session State

## 현재 Phase

Phase 4A — Production Library Promotion and JAR Packaging 완료

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
- production core와 분리된 confidence-aware semantic gate 4-state 계약
- 독립 gate calibration 480건, sealed holdout 320건, production-like 500건
- Phase 3.15 calibration candidate recall 91.43%, routing rate 51.46%
- Phase 3.15 sealed candidate recall 95.71%, routing rate 55.63%, missed BLOCK 6
- production-like candidate recall 90.00%, routing rate 29.80%
- sealed deterministic false positive 24건 모두 semantic review 도달
- gate 동결 후 sealed holdout 1회 평가 및 결과 확인 뒤 무수정
- frozen Phase 3.10 / Phase 3.14 / Phase 3.15 semantic-oracle 비교 runner
- historical `$0.00014119/request` 기반 production-like offline 비용 projection
- frozen Phase 3.13 prompt/model/provider를 재사용하는 final Luna A/B/C harness
- credential·가격 미설정 시 live task만 명확히 skip하는 실행 계약
- 전체/category 지표, routing loss, latency, token, 실제 비용과 월 projection 출력 준비
- Phase 3.16 frozen sealed holdout live 실행 완료
- Phase 3.15 + Luna accuracy 98.13%, precision 100%, recall 96.25%, FPR 0%
- Luna latency average 942 ms, p95 1306 ms, p99 1951 ms
- Luna average API cost `$0.00014198/request`
- validated confidence-aware gate와 semantic contracts를 `src/main`에 승격
- 기존 synchronous `ChatModerationService.moderate(String)` public contract 유지
- default production dictionary/exception/alias resources 패키징
- runtime-configured `OpenAiLunaModerationProvider`와 frozen policy prompt 승격
- `DETERMINISTIC_FALLBACK` / `FAIL_OPEN` / `FAIL_CLOSED` production failure policy
- BLOCK precedence에서도 provider 전송 전 phone/email을 독립 sanitization
- semantic ALLOW와 FAIL_OPEN이 개인정보 원문을 복원하지 않는 production test
- external package consumer smoke test
- Java module export를 통한 matcher/normalizer/rule/gate 내부 경계
- version `0.2.0`, `chat-moderation-0.2.0.jar` 생성 및 contents 검사

## 아직 구현하지 않은 것

- Spring 연동
- Redis 연동
- YoungManRest_BE Spring adapter와 dependency 통합
- WebSocket 연동
- Grafana metric

## 다음 작업

Production library는 `build/libs/chat-moderation-0.2.0.jar`로 준비됐다. 다음 작업은 사람의
승인 후 YoungManRest_BE Gradle dependency와 Spring-only adapter를 추가하는 것이다. Adapter는
semantic 호출을 bounded worker에서 수행하고 explicit timeout/failure policy를 설정하며,
반드시 persistence와 WebSocket broadcast 전에 `moderate`를 호출해야 한다. BLOCK은 두 작업을
모두 중단하고 MASK는 `outputMessage`만 저장·전송하는 integration test가 필요하다. Phase 3.15
gate와 sealed dataset은 historical 결과를 고치기 위해 변경하지 않는다. Redis와 original ↔
normalized offset mapping은 별도 범위다.
