# Phase 3.14 High-Recall Semantic Router Redesign

## 결론

새 router는 calibration candidate recall 100%를 달성했지만, 설계 완료 후 연 sealed
holdout에서는 87.50%로 하락해 성공 기준 95%에 미달했다. 이 버전은 production 후보로
채택하지 않는다. Holdout 결과를 본 뒤 router를 수정하지 않았다.

실제 Luna API를 호출하지 않은 수치는 semantic 정답 label을 oracle로 사용한 router
upper-bound 평가다. 새 holdout에서 실제 A/B/C Luna 성능은 credential-gated task로
별도 측정한다.

## Dataset

고정 template의 Cartesian product로 합성하되 생성되는 모든 문장을 unique case로 검사한다.

| dataset | cases | BLOCK | ALLOW | category |
|---|---:|---:|---:|---:|
| router calibration | 336 | 168 | 168 | 15 |
| sealed router holdout | 224 | 112 | 112 | 15 |

BLOCK category는 DIRECT_INSULT, INDIRECT_INSULT, FAMILY_INSULT,
DIRECT_SEXUAL_REQUEST, SEXUAL_HARASSMENT, ADVERTISEMENT, NEW_SLANG이다.
ALLOW category는 NORMAL_CHAT, REPORT_OF_ABUSE, QUOTED_ABUSE, EDUCATIONAL,
BENIGN_HOMONYM, PROPER_NOUN, TECHNICAL_TERM, NORMAL_CRITICISM이다.

두 dataset은 서로 겹치지 않으며 historical semantic 108, Phase 3.12 calibration/holdout과도
문장 단위로 겹치지 않는다. 실제 개인정보를 포함하지 않는다.

- Router source SHA-256:
  `98bb1cc3f25686bfa1694f3370c52e5d43b9a5fee46b6f531e36e520bfb12997`
- Calibration template SHA-256:
  `508f0df10798ea4d4fe9e15240c1591d655efca1a6c9d58e5cf65a5dc68eb9aa`
- Holdout template SHA-256:
  `617c6b4283de3e68a4be68db8989b3ab7a06cf225cae61323898ffaf107b19b4`

## Router strategy

Router 출력은 `LOCAL_FINAL`과 `NEEDS_SEMANTIC_REVIEW`뿐이다. BLOCK 판정을 만들지 않는다.
Deterministic BLOCK/MASK는 LOCAL_FINAL이며, deterministic ALLOW에서 다음 저비용 signal을
조합한다.

- 2인칭 + 부정적 사람 평가
- 간접 공격 frame + 부정적 서술
- 가족 표현 + 공격 대상/평가
- 성적 맥락 + 요구·대상화·거절 무시 frame
- 상업 표현 + 가입·지급·보장 CTA
- 알려진 비하 신조어와 생산적인 `...충` 형태
- 명백한 신고·인용·교육 frame의 보수적 LOCAL_FINAL

Signal은 BLOCK 판정이 아니라 Luna candidate 선택에만 사용한다.

## Calibration result

| router | candidate recall | routing rate | routed BLOCK | missed BLOCK | routed ALLOW |
|---|---:|---:|---:|---:|---:|
| existing frozen | 37.50% | 37.50% | 63 | 105 | 63 |
| new high-recall | 100.00% | 50.00% | 168 | 0 | 0 |

균형 dataset에서 semantic BLOCK이 50%이므로 100% candidate recall을 유지할 때 routing
rate의 이론적 하한도 50%다. 따라서 이 분포에서 routing rate 30% 목표는 양립할 수 없다.

## Sealed holdout router result

| router | candidate recall | routing rate | routed BLOCK | missed BLOCK | routed ALLOW |
|---|---:|---:|---:|---:|---:|
| existing frozen | 30.36% | 30.36% | 34 | 78 | 34 |
| new high-recall | 87.50% | 43.75% | 98 | 14 | 0 |

새 router는 frozen router보다 candidate recall이 57.14 percentage point 높지만 목표에는
7.50 point 부족하다.

### Semantic-label oracle hybrid

| mode | accuracy | precision | recall | F1 | FPR | FNR | TP/TN/FP/FN |
|---|---:|---:|---:|---:|---:|---:|---|
| Luna 100% upper-bound oracle | 100.00% | 100.00% | 100.00% | 100.00% | 0.00% | 0.00% | 112/112/0/0 |
| frozen router + oracle | 64.29% | 94.44% | 30.36% | 45.95% | 1.79% | 69.64% | 34/110/2/78 |
| new router + oracle | 88.39% | 89.09% | 87.50% | 88.29% | 10.71% | 12.50% | 98/100/12/14 |

New router의 routed ALLOW는 0이지만 FPR 12건이 남는다. 이는 deterministic이 BLOCK한
고유명사/전문 용어를 새 router 역할 계약에 따라 LOCAL_FINAL로 유지한 결과다.

## Missed BLOCK 14건

- `이야기할수록 너는 대화할 가치가 전혀 없어`
- `솔직히 당신은 대화할 가치가 전혀 없어`
- `모두가 보기에 넌 대화할 가치가 전혀 없어`
- `이번 일에서 네가 대화할 가치가 전혀 없어`
- `그쪽 엄마 행동을 보면 참 부끄러워하겠다`
- `내게 숙소로 와서 같이 자자`
- `내게 옷을 벗고 영상 통화하자`
- `바로 지금 숙소로 와서 같이 자자`
- `바로 지금 옷을 벗고 영상 통화하자`
- `오늘 늦은 밤 숙소로 와서 같이 자자`
- `오늘 늦은 밤 옷을 벗고 영상 통화하자`
- `우리끼리 있을 때 숙소로 와서 같이 자자`
- `우리끼리 있을 때 옷을 벗고 영상 통화하자`
- `거절하는 모습을 보니 성적 대상으로만 느껴진다`

## Cost projection

Phase 3.13 historical average `$0.00014119/request`를 적용한다.

| monthly chats | frozen router | new router |
|---:|---:|---:|
| 10,000 | $0.4286 | $0.6177 |
| 100,000 | $4.2861 | $6.1771 |
| 1,000,000 | $42.8612 | $61.7706 |
| 10,000,000 | $428.6125 | $617.7062 |

## Commands

```bash
./gradlew highRecallRouterCalibration
./gradlew highRecallRouterEvaluation
./gradlew highRecallRouterLunaEvaluation
```

앞의 두 task는 credential 없이 실행한다. 마지막 task만 `OPENAI_API_KEY`가 있을 때 sealed
224건을 실제 Luna로 한 번씩 평가하며 A/B/C에 동일 판정을 적용한다.
