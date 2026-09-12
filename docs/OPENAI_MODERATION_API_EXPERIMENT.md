# Phase 3.11b OpenAI Free Moderation API Experiment

## 상태와 실험 경계

`OpenAiModerationApiProvider`와 frozen evaluation runner 구현 후 실제 108건 비교를
완료했다. 아래 aggregate 결과는 보존했지만 per-case score export는 저장하지 않았으므로
Phase 3.12 threshold 선택에는 사용하지 않는다.

다음 항목은 수정하지 않았다.

- `semantic-evaluation.tsv` 108건
- Phase 3.10 `SemanticReviewRouter`
- production `src/main`
- `YoungManRest_BE`

Dataset SHA-256은
`9e8fb38d318424433e09fd36f425bc72055861fd7c2550bfb3a05d61c7520b3f`, router SHA-256은
`b63e459740b1f4f5c5441b57942bd8cfcf05569989b9967623d3de7bfa67f145`이다.

## 공식 API 계약

- Endpoint: `POST https://api.openai.com/v1/moderations`
- Model: `omni-moderation-latest`
- Authentication: `Authorization: Bearer ${OPENAI_API_KEY}`
- Request: `{"model":"omni-moderation-latest","input":"..."}`

OpenAI 공식 문서는 이 모델을 무료 harmful-content 분류 모델로 설명한다. 2026-09-12
표시 기준 Free tier 제한은 250 RPM, 5,000 RPD, 10,000 TPM이다.

- Model 및 rate limit: https://developers.openai.com/api/docs/models/omni-moderation-latest
- Moderations API: https://developers.openai.com/api/reference/cli/resources/moderations

이 모델은 custom prompt나 structured output을 받는 일반 semantic LLM으로 취급하지 않는다.
쉬었음 전용 광고·가족 모욕·신조어 정책을 지시할 수 있다고 가정하지 않는다.

## 응답과 category 보존

Provider는 `results[0].flagged`, `categories`, `category_scores`를 파싱한다.
Category 이름은 미리 열거하지 않고 실제 응답의 모든 key/value를
`OpenAiModerationObservation`에 보존한다. Evaluation은 각 category의 evaluated/flagged
횟수를 동적으로 출력한다.

현재 core reason과 공식 Moderation category는 동일한 taxonomy가 아니므로 provider에서
억지로 매핑하지 않는다. `flagged=true`이면 semantic decision만 `BLOCK`으로 반환하고,
native category/score는 observation에 보존한다. 기존 hybrid 계약은 reason 없는 semantic
BLOCK을 최종 `OTHER` reason으로 표현한다.

정확도와 category별 정확도는 provider taxonomy가 frozen dataset reason과 다르므로
action 기준으로 계산한다. Native category와 score는 별도 통계로 유지한다.

## Privacy와 failure

Provider-only와 hybrid 모두 deterministic 결과가 `MASK`이면 sanitized `outputMessage`를
전송한다. 실제 provider의 serialized request body에 원문 전화번호가 없음을 테스트한다.

- HTTP non-2xx 또는 malformed response: `UNKNOWN/ERROR`
- Timeout: `UNKNOWN/TIMEOUT`
- HTTP 429: error와 별도로 rate-limit count 및 HTTP status에 기록
- Hybrid failure: 기존 `ProviderFailurePolicy` 적용

## 실행

API key는 process environment에서만 읽는다.

```bash
export OPENAI_API_KEY='...'
./gradlew openAiModerationEvaluation
```

키가 없으면 live task만 안내 후 skip한다. 호출은 병렬화하지 않으며 각 실제 호출 뒤
250ms를 기다린다. API 자체 latency가 추가되므로 이론상 최대 호출률은 240 RPM보다 낮다.

Runner는 frozen dataset에서 다음을 비교한다.

1. deterministic only
2. local-heuristic-v1 only
3. deterministic + local heuristic hybrid
4. OpenAI Moderation API only
5. deterministic + OpenAI Moderation API hybrid

각 mode의 accuracy, precision, recall, F1, FPR, FNR, TP/TN/FP/FN과 9개 category별
accuracy를 출력한다. API-only/hybrid request, success, error, timeout, 429, HTTP status,
network latency average/p50/p95/p99, native category 통계와 hybrid FP/FN도 출력한다.

API token cost는 `$0`으로 보고하며 Responses API provider의 token 비용 계산과 섞지 않는다.

## Historical live 결과

총 199회 호출에서 API error, timeout, 429는 모두 0건이었고 비용은 `$0`이었다.

Moderation API-only 결과:

| accuracy | precision | recall | FPR | FNR |
|---:|---:|---:|---:|---:|
| 49.07% | 100.00% | 14.06% | 0.00% | 85.94% |

이는 TP 9, TN 44, FP 0, FN 55에 해당한다. False positive가 없었던 대신 대부분의
서비스 정책 위반을 놓쳤다.

Latency baseline:

- API-only: average 약 438ms, p95 약 928ms
- Hybrid: average 약 383ms, p95 약 548ms

이 108건은 이제 historical benchmark이며 threshold tuning에 사용하지 않는다.
