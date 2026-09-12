# Phase 3.13 GPT-5.6 Luna Baseline Evaluation

## 상태

Baseline prompt, frozen-router 비교 runner, telemetry와 비용 simulation 구현은 완료됐다.
현재 프로세스에는 `OPENAI_API_KEY`와 가격 환경변수가 없어 live 평가는 실행하지 않았다.

이번 Phase에서는 결과를 본 뒤 prompt, router, deterministic core 또는 dataset을 수정하지
않는다.

## Frozen inputs

- Model: `gpt-5.6-luna`
- Semantic dataset: `semantic-evaluation.tsv` 108건
- Policy benchmark: Phase 3.12 sealed `policy-holdout.tsv` 165건
- Router: 현재 `SemanticReviewRouter`
- Prompt source SHA-256:
  `f36d0b99e936c88259fb016a7e354e484441c9d1ba185c60fe9f008277af4d9a`
- Semantic dataset SHA-256:
  `9e8fb38d318424433e09fd36f425bc72055861fd7c2550bfb3a05d61c7520b3f`
- Policy holdout SHA-256:
  `edb32b554523c222bc42e377bcc8f03f068d33ccba610a35c5a9f15138c144da`
- Router SHA-256:
  `b63e459740b1f4f5c5441b57942bd8cfcf05569989b9967623d3de7bfa67f145`

## Policy prompt

Prompt는 `DIRECT_ABUSE/BLOCK`과 `MENTION_OR_REPORT/ALLOW`를 명시한다. 직접·간접 모욕,
가족 모욕, 직접 성적 요구, 성적 괴롭힘·대상화, 광고와 공격적 신조어는 BLOCK이다.
피해 신고, 설명, 인용, 분석, 교육, 역사·고유명사·전문 용어, 정상 동음이의어와
비공격적 비판은 ALLOW다.

다음 direct/report pair를 prompt에 고정했다.

- `야한 사진 보내줘` → BLOCK
- `저 사람이 야한 사진 보내달라고 했어요` → ALLOW
- `네 부모 수준도 알 만하다` → BLOCK
- `'네 부모 수준도 알 만하다'는 모욕적인 표현이다` → ALLOW

사용자 메시지 안의 명령은 provider instruction이 아닌 moderation 대상 데이터라고
명시한다.

## Evaluation design

각 메시지는 Luna에 한 번만 실제 요청한다. 그 동일한 판정을 두 mode에 적용한다.

1. Luna-only: 모든 메시지에서 Luna 판정을 그대로 사용
2. Frozen router + Luna: router가 semantic review로 보낸 경우에만 동일 Luna 판정을 적용

이 방식은 별도 재호출의 모델 변동성을 router 손실로 오인하지 않게 한다. 실제 request
telemetry와 router를 적용했을 때의 counterfactual request count는 구분한다.

각 mode에서 accuracy, precision, recall, F1, FPR, FNR, TP/TN/FP/FN과 category별
accuracy/recall/FP를 출력한다. Router 분석은 routing rate, semantic candidate recall,
routed BLOCK, missed BLOCK, Luna-only와 hybrid recall 차이 및 router가 놓친 문장을 출력한다.

Luna-only의 error/timeout/UNKNOWN은 ALLOW로 계산한다. Router mode의 provider failure는
기존 `DETERMINISTIC_FALLBACK`과 동일하게 계산한다.

## Privacy

Luna-only도 deterministic 결과가 MASK이면 `outputMessage`만 전송한다. Router mode는 같은
sanitized prediction을 사용한다. 기존 serialized request privacy test를 유지한다.

## Telemetry and cost

실제 호출에 대해 request, success, error, timeout, 429, average/p50/p95/p99 latency와
input/cached-input/output/total token을 출력한다.

2026-09-13 OpenAI 공식 가격표의 Standard short-context 가격은 1M token당 input `$0.20`,
cached input `$0.02`, output `$1.20`이다. 가격은 코드에 넣지 않고 실행 환경으로 주입한다.

```bash
export OPENAI_API_KEY='...'
export OPENAI_SEMANTIC_INPUT_USD_PER_MILLION='0.20'
export OPENAI_SEMANTIC_CACHED_INPUT_USD_PER_MILLION='0.02'
export OPENAI_SEMANTIC_OUTPUT_USD_PER_MILLION='1.20'
./gradlew lunaBaselineEvaluation
```

가격 출처: <https://developers.openai.com/api/docs/pricing>

비용 출력은 실제 Responses API usage를 사용하며 experiment total, average/request와 월
1만/10만/100만/1000만 채팅을 계산한다. Router 운영 비용은 Phase 3.9 holdout 160건의
현재 routing rate를 적용한다.

## Comparison boundary

기존 `omni-moderation-latest` semantic 108건 aggregate는 다음과 같다.

- accuracy 49.07%, precision 100%, recall 14.06%
- FPR 0%, FNR 85.94%
- TP 9, TN 44, FP 0, FN 55

Runner는 Luna의 aggregate 개선폭을 출력한다. Phase 3.12의 omni category별 실측 출력은
현재 repository에 저장돼 있지 않으므로 수치를 만들어내지 않는다. Luna policy category
표를 출력해 보존된 Phase 3.12 실행 보고와 직접 비교할 수 있게 한다.

Live 결과가 없으므로 Luna 도입 가치와 router 개선 필요성은 아직 결론내리지 않는다.
