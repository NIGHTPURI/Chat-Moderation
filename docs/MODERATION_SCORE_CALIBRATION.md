# Phase 3.12 Moderation Score Calibration

## 상태

정책, dataset, score distribution, threshold search와 sealed holdout runner 구현 및 live
default/custom threshold 실험은 완료됐다. 상세 category별 실행 출력은 현재 repository에
별도 artifact로 보존돼 있지 않으므로 Phase 3.13에서 임의로 복원하지 않는다.

기존 `semantic-evaluation.tsv` 108건은 historical benchmark로만 유지하며 calibration
코드는 이 resource를 로드하지 않는다.

## Dataset

| dataset | cases | BLOCK | ALLOW | 용도 |
|---|---:|---:|---:|---|
| `policy-calibration.tsv` | 220 | 100 | 120 | threshold 선택 전용 |
| `policy-holdout.tsv` | 165 | 75 | 90 | 선택 완료 후 1회 평가 |

두 dataset은 문장이 서로 겹치지 않고, 기존 108건을 복사하지 않았으며 실제 개인정보를
포함하지 않는다. 각 dataset에는 11개 category가 모두 포함된다.

- Calibration SHA-256:
  `e410f3efe635afbd3976a1d354a458373cce877deea19faab13e85c38ef7909f`
- Holdout SHA-256:
  `edb32b554523c222bc42e377bcc8f03f068d33ccba610a35c5a9f15138c144da`

Holdout은 live score를 보기 전에 seal했다. Runner도 calibration threshold 선택이 끝난
뒤에만 holdout resource를 로드한다. Holdout 결과를 본 뒤 dataset이나 threshold를
수정하면 안 된다.

## Score distribution

실제 API가 반환한 모든 category 이름을 동적으로 수집한다. Category마다 expected BLOCK
positive와 expected ALLOW negative를 분리해 다음을 출력한다.

- count
- min
- median
- p90
- p95
- max

Application policy가 현재 사용하는 score family는 다음과 같다.

- abuse score: `harassment`, `harassment/*`, `hate`, `hate/*`의 최대값
- sexual score: `sexual`, `sexual/*`의 최대값

광고는 Moderation API의 전용 service category가 아니므로 score threshold로 해결된다고
가정하지 않는다.

## Threshold 선택

Calibration에 실제 나타난 abuse/sexual score를 threshold 후보로 사용한다. 두 threshold
조합을 모두 평가하며 FPR ceiling 0%, 1%, 2%, 5%의 최선 후보를 출력한다.

최종 선택 규칙은 다음 순서다.

1. calibration FPR `≤ 5%`
2. recall 최대
3. FP 수 최소
4. precision, F1 순으로 최대
5. 동률이면 더 높은 threshold

Threshold는 코드나 문서에 미리 고정하지 않는다. 실제 calibration 출력이 있어야
확정된다.

## Holdout 비교

선택된 threshold를 변경하지 않고 sealed holdout에서 다음을 비교한다.

1. deterministic
2. Moderation API default `flagged`
3. Moderation API custom score threshold
4. deterministic + default moderation
5. deterministic + custom-threshold moderation

각 mode의 accuracy, precision, recall, F1, FPR, FNR, TP/TN/FP/FN과 category별 accuracy,
custom hybrid의 FP/FN을 출력한다.

## 실행

```bash
export OPENAI_API_KEY='...'
./gradlew moderationScoreCalibration
```

키가 없으면 이 live task만 안내 후 skip한다. 호출은 순차 실행하며 요청 뒤 250ms 간격을
둔다. 총 385건(220 calibration + 165 holdout)을 호출하며 API 비용은 `$0`이다.

Phase 3.11b historical latency baseline은 다음과 같다.

- API-only: average 약 438ms, p95 약 928ms
- Hybrid: average 약 383ms, p95 약 548ms

새 실행은 calibration과 holdout latency를 각각 다시 출력한다.
