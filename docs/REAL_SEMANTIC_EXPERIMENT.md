# Phase 3.11 Real Semantic Moderation Provider Experiment

## 상태와 경계

OpenAI Responses API provider와 frozen evaluation runner 구현은 완료됐다. 현재 개발
환경에는 credential이 없어 실제 108건 API 실행 결과는 아직 없다. 따라서 이 문서는
AI 도입 가치를 결론내리지 않는다.

다음 항목은 Phase 3.10 상태 그대로 유지한다.

- `src/test/resources/moderation/semantic-evaluation.tsv`: 108건,
  SHA-256 `9e8fb38d318424433e09fd36f425bc72055861fd7c2550bfb3a05d61c7520b3f`
- `SemanticReviewRouter`: SHA-256
  `b63e459740b1f4f5c5441b57942bd8cfcf05569989b9967623d3de7bfa67f145`
- production deterministic core와 `YoungManRest_BE`

Provider와 새 runner도 Phase 3.10 계층처럼 `src/test`에 격리되어 있다.

## Provider와 모델

`OpenAiSemanticModerationProvider`는 기존 `SemanticModerationProvider`를 구현하고
Responses API의 strict JSON schema structured output을 사용한다. 기본 모델은
`gpt-5.6-luna`이며 `OPENAI_SEMANTIC_MODEL`로 바꿀 수 있다.

2026-09-12 기준 OpenAI 공식 모델 문서에는 이 모델이 structured outputs와 Responses
API를 지원하고, 1M token당 input $0.20, cached input $0.02, output $1.20으로 기재되어
있다. 가격은 변할 수 있으므로 코드에 넣지 않고 실행 환경에서 명시한다.

- Model: https://developers.openai.com/api/docs/models/gpt-5.6-luna
- Responses structured output: https://developers.openai.com/api/reference/java/resources/beta/subresources/responses

## Prompt와 결과 계약

System instruction은 쉬었음 익명 채팅 정책을 명시한다.

- ALLOW: 욕설 설명·인용·제지·분석, 역사 용어, 고유명사, 전문 용어,
  비성적 동음이의어
- BLOCK: 직접 모욕, 가족 모욕, 암시적 성적 공격, 상대를 향한 성적 요구,
  문맥적 비하, 광고·도배 의도
- reason: `PROFANITY`, `SEXUAL_CONTENT`, `IMPLICIT_INSULT`, `FAMILY_INSULT`,
  `ADVERTISEMENT`, `OTHER`

Provider reason은 기존 production enum을 바꾸지 않기 위해 다음처럼 실험 결과에
매핑한다.

| provider reason | core reason |
|---|---|
| `PROFANITY` | `PROFANITY` |
| `SEXUAL_CONTENT` | `SEXUAL_CONTENT` |
| `IMPLICIT_INSULT`, `FAMILY_INSULT` | `HARASSMENT` |
| `ADVERTISEMENT` | `SPAM` |
| `OTHER` | `OTHER` |

응답이 schema/의미 계약을 지키지 않거나 HTTP error가 발생하면 decision은 `UNKNOWN`,
status는 `ERROR`가 된다. Request timeout은 `UNKNOWN/TIMEOUT`이다. Hybrid에서는 기존
`FAIL_OPEN`, `FAIL_CLOSED`, `DETERMINISTIC_FALLBACK` 정책이 그대로 적용된다.

## Credential과 실행

API key는 repository 파일이나 Gradle property가 아니라 process environment에서만
읽는다. `.env`는 gitignore 대상이지만 이 실험은 shell 또는 CI secret 주입을 권장한다.

```bash
export OPENAI_API_KEY='...'
export OPENAI_SEMANTIC_MODEL='gpt-5.6-luna'
export OPENAI_SEMANTIC_TIMEOUT_MS='30000'
export OPENAI_SEMANTIC_INPUT_USD_PER_MILLION='0.20'
export OPENAI_SEMANTIC_CACHED_INPUT_USD_PER_MILLION='0.02'
export OPENAI_SEMANTIC_OUTPUT_USD_PER_MILLION='1.20'
./gradlew realSemanticModerationEvaluation
```

가격 변수는 세 개 모두 설정하거나 모두 생략해야 한다. 생략하면 token 수는 출력하지만
비용은 계산하지 않는다. API key가 없으면 task는 안내 후 skip한다.

## 출력

Runner는 같은 frozen 108건에 대해 아래를 비교한다.

1. deterministic only
2. local-heuristic-v1 only
3. deterministic + local heuristic hybrid
4. real semantic provider only
5. deterministic + real semantic provider hybrid

각 mode의 accuracy, precision, recall, F1, FPR, FNR과 9개 category별 accuracy를
출력한다. 또한 Phase 3.9 holdout 160건 routing rate, real-only/hybrid별 request 수,
success/error/timeout, network latency average/p50/p95/p99, input/cached/output token,
전체 예상 비용 및 real-only/hybrid FP/FN을 출력한다.

Provider-only 평가도 deterministic MASK 결과가 있으면 sanitized `outputMessage`를
전송한다. Hybrid privacy 계약은 실제 provider의 serialized HTTP body까지 검사하는
단위 테스트로 고정했다.

## 도입 판단 기준

실제 실행 후에도 전체 accuracy만으로 도입하지 않는다. 특히
`BENIGN_HOMONYM`, `PROPER_NOUN`, `TECHNICAL_TERM`의 FP 감소와
`IMPLICIT_INSULT`, `FAMILY_INSULT` recall 증가를 함께 본다. 그 개선폭을 holdout
호출률, network latency, token 비용, timeout/error 및 개인정보 계약과 비교한다.
