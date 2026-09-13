# Phase 3.16 Final Luna Evaluation Harness

## 상태

재현 가능한 live evaluation이 완료됐다. Frozen Phase 3.15 sealed holdout과 gate는 결과를
본 뒤 수정하지 않았다.

| mode | accuracy | precision | recall | F1 | FPR | FNR | TP/TN/FP/FN |
|---|---:|---:|---:|---:|---:|---:|---|
| Luna 100% | 93.75% | 100% | 87.50% | 93.33% | 0% | 12.50% | 140/160/0/20 |
| Phase 3.14 + Luna | 70.00% | 78.57% | 55.00% | 64.71% | 15.00% | 45.00% | 88/136/24/72 |
| Phase 3.15 + Luna | 98.13% | 100% | 96.25% | 98.09% | 0% | 3.75% | 154/160/0/6 |

관측 Luna latency는 평균 942 ms, p95 1306 ms, p99 1951 ms였고 평균 API 비용은
`$0.00014198/request`였다. Phase 3.15 routing rate는 balanced sealed data에서 55.63%,
production-like offline data에서 29.80%였다.

Harness는 Phase 3.15 sealed holdout 320건을 사용한다. 각 메시지를 Luna에 한 번만 요청해
동일 판정을 다음 세 mode에 재사용한다.

1. Luna 100%
2. Phase 3.14 router + Luna
3. Phase 3.15 confidence-aware gate + Luna

Provider는 Phase 3.13의 `gpt-5.6-luna`, frozen policy prompt, Responses API structured
output 설정을 그대로 사용한다. 모델 환경변수로 다른 모델을 지정하면 실행을 거부한다.

## 출력

- accuracy, precision, recall, F1, FPR, FNR, TP/TN/FP/FN
- category별 accuracy와 recall
- routing rate와 semantic candidate recall
- Luna-only 대비 각 gated mode의 recall loss
- routed BLOCK, missed BLOCK, routed ALLOW
- request/success/error/timeout/429
- average/p50/p95/p99 latency
- input/cached input/output/total token usage
- configured 실행 시점 가격에 따른 실제 total 및 average request cost
- 1만, 10만, 100만, 1000만 메시지 월 비용 projection

API key가 없으면 task는 안내와 함께 skip한다. 실제 비용을 빠뜨리지 않기 위해 API key가
있어도 세 token 가격 중 하나라도 없으면 API 호출 전에 skip한다. 일반 test/build는 어떤
credential도 요구하지 않는다.

## Human live command

가격은 실행 시점의 확인된 값을 넣는다. 아래 값은 Phase 3.13에서 사용한 설정이며 사람이
실행 전에 다시 검토해야 한다.

```bash
OPENAI_API_KEY='...' \
OPENAI_SEMANTIC_MODEL='gpt-5.6-luna' \
OPENAI_SEMANTIC_INPUT_USD_PER_MILLION='0.20' \
OPENAI_SEMANTIC_CACHED_INPUT_USD_PER_MILLION='0.02' \
OPENAI_SEMANTIC_OUTPUT_USD_PER_MILLION='1.20' \
./gradlew finalLunaEvaluation
```

Phase 3.13 historical `$0.00014119/request`는 Phase 3.15 offline projection에만 사용한다.
Live report의 actual request cost와 monthly projection은 Responses API usage와 위 가격을
사용한다.
