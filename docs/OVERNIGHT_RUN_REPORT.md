# Overnight Run Report — Phases 3.15 and 3.16

## Repository boundary

- Starting commit: `6fb78fd` (`test: evaluate high-recall semantic router`)
- Implementation commit: `fc9c91f` (`feat: add confidence-aware semantic gate`)
- Report commit: `docs: record Phase 3.15 overnight results` (this document)
- Branch: `main`
- Push/deploy: not performed
- External API calls: not performed
- `YoungManRest_BE`: not modified
- Pre-existing untracked `overnight-codex.log`: not modified or committed

## Phase 3.15 result

**PASS** — the frozen confidence-aware gate reached 95.71% semantic candidate recall on
the independent sealed holdout, above the required 95% threshold. The gate and sealed dataset
were not modified after the first sealed evaluation.

| dataset | cases | BLOCK/ALLOW | candidate recall | routing rate | routed BLOCK | missed BLOCK | routed ALLOW |
|---|---:|---:|---:|---:|---:|---:|---:|
| calibration | 480 | 240/240 | 91.43% | 51.46% | 192 | 18 | 55 |
| sealed holdout | 320 | 160/160 | 95.71% | 55.63% | 134 | 6 | 44 |
| production-like | 500 | 100/400 | 90.00% | 29.80% | 90 | 10 | 59 |

Sealed comparison:

| router | candidate recall | routing rate | oracle accuracy | oracle recall | FPR |
|---|---:|---:|---:|---:|---:|
| frozen Phase 3.10 | 28.57% | 27.50% | 62.50% | 37.50% | 12.50% |
| Phase 3.14 | 48.57% | 22.81% | 70.00% | 55.00% | 15.00% |
| Phase 3.15 | 95.71% | 55.63% | 98.13% | 96.25% | 0.00% |

The Phase 3.15 oracle confusion counts are TP/TN/FP/FN `154/160/0/6`.

### Missed sealed BLOCK candidates

- `내일 새벽에 나와 잠자리를 가지자`
- `아무도 없을 때 나와 잠자리를 가지자`
- `지금 나에게 나와 잠자리를 가지자`
- `이번 여행에서 나와 잠자리를 가지자`
- `오늘 안에 결제하면 특가 상품을 구매할 수 있습니다`
- `추천코드를 등록하면 특가 상품을 구매할 수 있습니다`

These failures are recorded without changing the frozen gate.

### Deterministic false-positive rescue

| dataset | rescued to semantic review | still finalized locally |
|---|---:|---:|
| calibration | 35 | 0 |
| sealed holdout | 24 | 0 |
| production-like | 24 | 1 |

The sealed rescued cases cover education/report context, proper nouns, and technical terms that
the deterministic dictionary had blocked. This satisfies the architectural requirement that a
deterministic BLOCK is not automatically certain.

### Offline cost projection

The production-like Phase 3.15 routing rate is 29.80%. Applying the Phase 3.13 historical
average `$0.00014119/request` for offline projection only gives:

| monthly messages | Luna 100% | Phase 3.14 | Phase 3.15 |
|---:|---:|---:|---:|
| 10,000 | $1.4119 | $0.2316 | $0.4207 |
| 100,000 | $14.1190 | $2.3155 | $4.2075 |
| 1,000,000 | $141.1900 | $23.1552 | $42.0746 |
| 10,000,000 | $1,411.9000 | $231.5516 | $420.7462 |

## Phase 3.16 status

**PREPARED; LIVE RUN NOT EXECUTED.** `finalLunaEvaluation` compares Luna 100%, Phase 3.14
router + Luna, and Phase 3.15 gate + Luna using one shared Luna prediction per sealed case and
the frozen Phase 3.13 prompt/model/provider configuration.

The report includes aggregate and category metrics, routing and recall loss, routed ALLOW and
missed BLOCK cases, request status, latency, token usage, actual request cost, and monthly
projections. It requires `OPENAI_API_KEY` and all three execution-time token prices; otherwise it
skips before any request. Normal tests and builds do not require credentials.

## Verification

- `./gradlew test confidenceGateCalibration confidenceGateEvaluation` — passed
- `./gradlew clean build` — passed
- 441 JUnit tests — passed
- `git diff --check` — passed before the implementation commit; repeated for the report commit
- Dataset independence test — passed for Phase 3.10~3.14 and all Phase 3.15 datasets
- Live OpenAI task — deliberately not run

## Human next command

Confirm the token prices at execution time, then run exactly:

```bash
OPENAI_API_KEY='...' \
OPENAI_SEMANTIC_MODEL='gpt-5.6-luna' \
OPENAI_SEMANTIC_INPUT_USD_PER_MILLION='0.20' \
OPENAI_SEMANTIC_CACHED_INPUT_USD_PER_MILLION='0.02' \
OPENAI_SEMANTIC_OUTPUT_USD_PER_MILLION='1.20' \
./gradlew finalLunaEvaluation
```

Do not package or promote the production JAR until a human reviews that live result.
