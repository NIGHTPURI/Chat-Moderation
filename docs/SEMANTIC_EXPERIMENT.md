# Phase 3.10 Semantic Moderation Experiment

이 Phase의 코드는 모두 `src/test`에 있으며 production core의 API나 동작을 변경하지
않는다. 실제 AI provider 또는 사용자 데이터도 사용하지 않는다.

## 실험 구조

`SemanticModerationProvider`는 메시지를 받아 decision, reasons, confidence, status,
provider 이름을 반환한다. `SemanticReviewRouter`는 deterministic 결과를 다음 네 상태로
나눈다.

- `CLEAR_ALLOW`: 명시적 문맥 검토 trigger가 없는 deterministic ALLOW
- `CLEAR_MASK`: 문맥 검토 trigger가 없는 deterministic MASK
- `CLEAR_BLOCK`: 명확한 keyword, URL, SPAM BLOCK
- `NEEDS_SEMANTIC_REVIEW`: 명시적으로 등록한 동음이의어, 간접 모욕, 성적 요구,
  가족 모욕, 광고 의도, 신조어 후보

라우터는 deterministic에서 ALLOW된 모든 메시지를 보내지 않는다. Phase 3.9 holdout
160건을 운영 분포의 작은 proxy로 사용한 호출률은 6.88%(11건)였다. 반면 문맥 사례만
모은 semantic dataset에서는 84.26%(91/108)가 routed 되므로 이 비율을 서비스 호출률로
해석하면 안 된다.

## Provider와 결과 해석

현재 포함한 `LocalHeuristicSemanticProvider`는 evaluation harness를 credential 없이
재현하고 failure/privacy 계약을 테스트하기 위한 stand-in이다. 실제 semantic model이
아니므로 아래 결과는 AI 정확도나 도입 가치를 입증하지 않는다.

| mode | accuracy | precision | recall | F1 | FPR | FNR |
|---|---:|---:|---:|---:|---:|---:|
| deterministic only | 26.85% | 0.00% | 0.00% | 0.00% | 34.09% | 100.00% |
| local heuristic only | 75.93% | 85.19% | 71.88% | 77.97% | 18.18% | 28.13% |
| deterministic + routed local heuristic | 75.00% | 84.91% | 70.31% | 76.92% | 18.18% | 29.69% |

Deterministic 점수가 낮은 것은 이 데이터셋이 기존 keyword/rule이 아니라 문맥 판단이
필요한 사례를 의도적으로 집중 표집했기 때문이다. Hybrid는 정상 동음이의어와
고유명사 false positive를 해소했지만, 인용/분석 문맥의 광고·신조어 8건을 오탐했다.
Recall 증대와 함께 FPR이 커질 수 있다는 위험이 확인됐다.

## Dataset

`semantic-evaluation.tsv`는 다음 네 열을 사용한다.

```text
category  message  expectedAction  expectedReason
```

총 108건이며 9개 category에 각 12건을 배치했다.

- `CONTEXTUAL_PROFANITY`
- `CONTEXTUAL_SEXUAL`
- `IMPLICIT_INSULT`
- `FAMILY_INSULT`
- `BENIGN_HOMONYM`
- `PROPER_NOUN`
- `TECHNICAL_TERM`
- `ADVERTISEMENT_INTENT`
- `NEW_SLANG`

ALLOW 44건, BLOCK 64건이다. 모두 합성 문장이며 실제 개인정보를 포함하지 않는다.

## Privacy와 failure 정책

전화번호/이메일을 포함한 메시지가 review 대상이면 provider에는 deterministic MASK가
적용된 `outputMessage`만 전달한다. Semantic ALLOW 또는 provider failure에서도 MASK
결과를 원문 ALLOW로 되돌리지 않는다. 이 계약은 JUnit에서 원문 전화번호가 provider
입력에 존재하지 않는지 직접 검증한다.

- `FAIL_OPEN`: MASK는 보존하고 그 외 review message는 허용
- `FAIL_CLOSED`: review message를 `OTHER` reason으로 차단
- `DETERMINISTIC_FALLBACK`: provider가 없을 때 기존 deterministic 결과 보존

Production 기본값은 이번 Phase에서 선택하지 않았다. 로컬 provider 실행에서는 error와
timeout이 모두 0건이었고, 세 failure 동작은 가짜 provider로 테스트했다.

## Policy label review

기존 `evaluation.tsv`와 holdout은 수정하지 않았다. 다음은 서비스 정책 확인이 필요한
라벨이다.

| case | 현재 라벨 | 재검토 사항 |
|---|---|---|
| `example.com이라는 문자열을 문서에 썼어요` | ALLOW | URL 문자열 자체 금지 정책이면 BLOCK이 맞다. |
| `http:/example.com은 ...`, `https//example.com은 ...` | ALLOW | 링크가 아니어도 domain 표기를 금지할지 정해야 한다. |
| `개년이라고 욕하지 마` | BLOCK | 욕설 인용·제지 문장도 zero-tolerance로 차단할지 정해야 한다. |
| `병신년`, `야동리/야동초등학교`, `후장식` | ALLOW | 언어적으로 정상인 고유명사·전문 용어를 서비스가 허용할지 정해야 한다. |
| `보지/자지/애널`의 직접 성적 사용 | BLOCK | 형태만으로 정상 활용형과 구분할 수 없어 semantic 판단이 필요하다. |

평가 기준은 일반적인 언어 유해성이 아니라 쉬었음 서비스의 실제 허용 정책이어야 하며,
정책 확정 후 versioned dataset으로 별도 변경해야 한다.

## 실행과 latency

```bash
./gradlew semanticModerationEvaluation
```

한 로컬 실행의 stand-in provider latency는 semantic-only 평균 56.308 us,
p50 18.100 us, p95 128.701 us, p99 342.204 us였다. Routed 호출은 평균 19.954 us,
p50 12.400 us, p95 70.901 us, p99 114.001 us였다. 이는 network/model inference가 없는
문자열 휴리스틱 수치이므로 실제 AI latency나 비용의 proxy가 아니다. API 호출 비용은
없다.

실제 AI 도입 가치는 아직 판단할 수 없다. 다음 실험에서는 이 abstraction의 provider만
격리 교체하고 동일한 frozen dataset과 라우팅을 사용해 75.00% hybrid, 18.18% FPR,
29.69% FNR보다 유의미하게 개선되는지 확인해야 한다. 특히 FPR 억제와 timeout/error,
비용, 개인정보 처리 검증을 통과하기 전에는 production policy에 연결하지 않는다.
