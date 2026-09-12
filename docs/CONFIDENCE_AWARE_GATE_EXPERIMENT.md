# Phase 3.15 Confidence-Aware Semantic Gate

## 결론

새 gate는 동결 후 처음 실행한 320건 sealed holdout에서 semantic candidate recall
95.71%를 기록해 95% 기준을 통과했다. 결과 확인 후 gate와 dataset은 수정하지 않았다.
이 구현은 production core가 아니라 `src/test`의 실험 경계에 있으며 semantic 최종
BLOCK/ALLOW를 결정하지 않는다.

## 상태와 설계

외부 `ModerationResult` API는 변경하지 않고 다음 내부 상태를 구분한다.

- `CERTAIN_ALLOW`: semantic-risk signal이 없는 deterministic ALLOW
- `CERTAIN_BLOCK`: 문맥 민감 신호가 없는 명백한 deterministic BLOCK
- `CERTAIN_MASK`: 전화번호·이메일 등 deterministic MASK
- `NEEDS_SEMANTIC_REVIEW`: semantic-risk ALLOW 또는 문맥 민감 deterministic BLOCK

deterministic BLOCK도 자동으로 확정하지 않는다. 신고·인용·교육·분석 문맥과 알려진
고유명사·전문 용어·동음이의어가 있으면 semantic review로 보낸다. ALLOW에서는 직접·간접
모욕, 가족 모욕, 성적 요구·괴롭힘, 광고 의도, 비하 신조어 신호를 review 후보로 삼는다.
Gate는 상태와 sanitized provider 입력 후보만 반환하며 semantic action은 만들지 않는다.

## 독립 dataset과 동결 순서

| dataset | cases | BLOCK | ALLOW | 용도 |
|---|---:|---:|---:|---|
| calibration | 480 | 240 | 240 | gate 선택·조정 |
| sealed holdout | 320 | 160 | 160 | 동결 후 1회 판정 |
| production-like | 500 | 100 | 400 | benign-heavy 비용 추정 |

세 dataset의 문장은 서로 중복되지 않는다. Calibration과 holdout은 Phase 3.14
calibration/holdout과도 문장 단위로 겹치지 않는다. Phase 3.10~3.14 dataset은 비교용
router 코드 외에는 tuning 입력으로 사용하지 않았다.

- Gate source SHA-256: `6fba25f173b4753d179242bb38dba732b82b21b9e3c719bead20bb834bd2b778`
- Calibration template SHA-256: `274f1963fac549d97a19cda06d83c4bdeeb293f4c670a36065b2f66bfd8b3280`
- Sealed holdout template SHA-256: `ad33853d18662601ebdb28bda4b3b8a7032221015916a5ce45d634defd49663d`
- Production-like template SHA-256: `e593502721cf313ea13ee076f57d1b00b8ec553e3629e198a181e64d51134829`

Gate source는 sealed holdout을 처음 실행하기 전에 동결했다. Holdout 결과를 본 뒤
수정하지 않았으며 dataset 독립성 검사는 Phase 3.10~3.14와 세 Phase 3.15 dataset 사이의
문장 중복을 모두 거부한다.

## Offline semantic-oracle 결과

### Balanced calibration

| router | candidate recall | routing rate | routed BLOCK | missed BLOCK | routed ALLOW | FP rescued | FP local |
|---|---:|---:|---:|---:|---:|---:|---:|
| frozen Phase 3.10 | 20.48% | 19.38% | 43 | 167 | 50 | 10 | 25 |
| Phase 3.14 | 58.10% | 25.62% | 122 | 88 | 1 | 0 | 35 |
| Phase 3.15 | 91.43% | 51.46% | 192 | 18 | 55 | 35 | 0 |

### Sealed holdout

| router | candidate recall | routing rate | routed BLOCK | missed BLOCK | routed ALLOW | FP rescued | FP local |
|---|---:|---:|---:|---:|---:|---:|---:|
| frozen Phase 3.10 | 28.57% | 27.50% | 40 | 100 | 48 | 4 | 20 |
| Phase 3.14 | 48.57% | 22.81% | 68 | 72 | 5 | 0 | 24 |
| Phase 3.15 | 95.71% | 55.63% | 134 | 6 | 44 | 24 | 0 |

Phase 3.15 + semantic oracle는 accuracy 98.13%, precision 100%, recall 96.25%, F1
98.09%, FPR 0%, FNR 3.75%, TP/TN/FP/FN 154/160/0/6이다. False-positive rescue 24건은
교육, 고유명사, 전문 용어에 포함된 deterministic keyword 오탐이며 모두 semantic review에
도달했다.

Missed semantic BLOCK 6건은 다음과 같다.

- `내일 새벽에 나와 잠자리를 가지자`
- `아무도 없을 때 나와 잠자리를 가지자`
- `지금 나에게 나와 잠자리를 가지자`
- `이번 여행에서 나와 잠자리를 가지자`
- `오늘 안에 결제하면 특가 상품을 구매할 수 있습니다`
- `추천코드를 등록하면 특가 상품을 구매할 수 있습니다`

이 실패를 확인한 뒤 gate를 수정하지 않았다.

### Production-like 비용 분포

| router | candidate recall | routing rate | routed ALLOW | FP rescued | FP local |
|---|---:|---:|---:|---:|---:|
| frozen Phase 3.10 | 28.00% | 11.60% | 30 | 5 | 20 |
| Phase 3.14 | 70.00% | 16.40% | 12 | 0 | 25 |
| Phase 3.15 | 90.00% | 29.80% | 59 | 24 | 1 |

Phase 3.13 historical average `$0.00014119/request`를 offline projection에만 적용하면
Phase 3.15 비용은 월 1만/10만/100만/1000만 메시지에서 각각 `$0.4207`, `$4.2075`,
`$42.0746`, `$420.7462`이다.

## 복잡도와 한계

Signal 목록은 고정 크기이므로 메시지 길이를 `n`이라 할 때 gate 시간복잡도는 `O(n)`,
소문자 canonical 사본을 위한 추가 공간은 `O(n)`이다. Automaton이나 network call을
생성하지 않는다.

현재 signal은 한국어 중심의 lexical/structural heuristic이다. Sealed miss 6건과 unseen
표현 일반화 위험이 남아 있다. Routing rate 55.63%는 balanced semantic dataset의 값이며
운영 비용 추정에는 benign-heavy 29.80%를 사용해야 한다. Production 승격은 Phase 3.16
live Luna 결과와 사람의 검토 전까지 하지 않는다.

## 실행

```bash
./gradlew confidenceGateCalibration
./gradlew confidenceGateEvaluation
```

두 task 모두 외부 API를 호출하지 않는다.
