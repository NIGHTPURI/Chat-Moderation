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

Phase 3.16 live 평가와 Phase 4A production library 승격까지 완료되었습니다. Validated
Phase 3.15 gate + Luna는 sealed holdout에서 accuracy 98.13%, recall 96.25%, FPR 0%를
기록했습니다. Java 21 library JAR은 `build/libs/chat-moderation-0.2.0.jar`로 생성됩니다.
다음 순수 Java Core 및 검증 환경을 포함합니다.

- Unicode NFC, 영문 소문자화, 양끝 공백 제거를 수행하는 기본 Normalizer
- 한 번 구축한 automaton을 재사용하는 Aho-Corasick Keyword Matcher
- 중복·중첩·한글 패턴을 포함한 JUnit 5 테스트
- 전화번호, 이메일, URL, 과도한 동일 문자 반복 Rule
- 탐지 reason을 ALLOW, MASK, BLOCK으로 변환하는 Moderation Policy
- Normalizer, Keyword Matcher, Rule Filter, Policy를 조합하는 ChatModerationService
- normalized keyword match와 canonical 원문 range를 분리하는 정책 계약
- 단일 Aho-Corasick automaton을 사용하는 PROFANITY/SEXUAL_CONTENT 분류
- keyword별로 적용 범위를 제한한 false-positive exception
- canonical output과 분리된 dictionary-aware obfuscation detection view
- 명시적 초성 및 known-variant alias
- regression corpus와 분리된 320건 accuracy evaluation dataset
- 고정 dataset/조건의 개발용 benchmark baseline
- calibration 320건과 독립 holdout 160건의 분리 평가
- 제한된 `/`, shorthand/known alias, scheme-less URL calibration
- production core와 분리된 semantic provider/routing 실험 harness
- 108건 semantic 전용 dataset과 deterministic/semantic/hybrid 비교 runner
- OpenAI Responses API 기반 실제 semantic provider와 structured output parser
- credential-gated 5-way 비교 및 network latency/token/cost 계측 runner
- 무료 `omni-moderation-latest` 전용 provider와 native category 통계 runner
- DIRECT_ABUSE/MENTION_OR_REPORT 정책과 220건 calibration/165건 sealed holdout
- category score distribution 및 FPR 제한 기반 threshold 탐색 runner
- GPT-5.6 Luna-only와 동일 판정 기반 frozen-router 손실 비교 runner
- 336건 router calibration과 독립 sealed router holdout 224건
- lexical/structural high-recall router 및 oracle/live 비교 runner
- deterministic BLOCK의 문맥 민감도를 포함한 confidence-aware semantic gate
- 독립 calibration 480건, sealed holdout 320건, production-like cost dataset 500건
- Luna 100% / Phase 3.14 + Luna / Phase 3.15 + Luna 최종 live harness
- production semantic provider API와 explicit failure policy
- runtime-configured OpenAI Luna provider와 개인정보 sanitization
- Java module API 경계와 versioned JAR contents verification

## 로컬 테스트

프로젝트에 포함된 Gradle Wrapper로 실행합니다.

```bash
./gradlew test
./gradlew clean build
```

수동 playground와 개발용 benchmark는 다음처럼 실행합니다.

```bash
./gradlew moderationPlayground
./gradlew moderationEvaluation
./gradlew moderationBenchmark
./gradlew semanticModerationEvaluation
./gradlew realSemanticModerationEvaluation
./gradlew openAiModerationEvaluation
./gradlew moderationScoreCalibration
./gradlew lunaBaselineEvaluation
./gradlew highRecallRouterCalibration
./gradlew highRecallRouterEvaluation
./gradlew highRecallRouterLunaEvaluation
./gradlew confidenceGateCalibration
./gradlew confidenceGateEvaluation
./gradlew finalLunaEvaluation
```

`realSemanticModerationEvaluation`, `openAiModerationEvaluation`,
`moderationScoreCalibration`, `lunaBaselineEvaluation`,
`highRecallRouterLunaEvaluation`, `finalLunaEvaluation`만 `OPENAI_API_KEY`가 있을 때 실제 API를 호출합니다.
나머지 router task는 offline입니다. Luna baseline은 가격 환경변수도 필요합니다. 설정은
[Real Semantic Experiment](docs/REAL_SEMANTIC_EXPERIMENT.md)와
[OpenAI Moderation API Experiment](docs/OPENAI_MODERATION_API_EXPERIMENT.md)에 정리되어
있습니다.

정책 정의와 threshold 실험 절차는 [Policy Adjudication](docs/POLICY_ADJUDICATION.md),
[Moderation Score Calibration](docs/MODERATION_SCORE_CALIBRATION.md)을 참고하세요.
Luna baseline 계약과 비용 설정은
[Luna Baseline Experiment](docs/LUNA_BASELINE_EXPERIMENT.md)을 참고하세요.
Router 재설계 결과는
[High-Recall Router Experiment](docs/HIGH_RECALL_ROUTER_EXPERIMENT.md)을 참고하세요.
Confidence-aware gate 결과는
[Confidence-Aware Gate Experiment](docs/CONFIDENCE_AWARE_GATE_EXPERIMENT.md), 최종 live 실행은
[Final Luna Evaluation](docs/FINAL_LUNA_EVALUATION.md)을 참고하세요.
Production JAR API와 backend 사용 계약은
[Production Library](docs/PRODUCTION_LIBRARY.md)를 참고하세요.

Corpus 형식과 검증 항목은 [Local Validation](docs/LOCAL_VALIDATION.md)에 정리되어 있습니다.

IntelliJ에서는 Gradle 프로젝트로 열어 JDK 21을 지정하면 됩니다.

## 최종 목표

외부 사용자는 내부 구현을 몰라도 아래 수준으로 사용할 수 있어야 합니다.

```java
ModerationResult result = moderationService.moderate(message);

if (!result.allowed()) {
    return;
}

broadcast(result.outputMessage());
```

핵심 원칙은 **broadcast 전에 반드시 moderation을 수행하는 것**입니다.
