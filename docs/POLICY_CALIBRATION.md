# Phase 3.8 Policy Calibration

## Evaluation baseline

`evaluation.tsv`는 regression test와 분리된 320건의 합성 채팅 평가 데이터다.
현재 deterministic core의 기준 결과는 다음과 같다.

- accuracy: 85.63% (274/320)
- precision: 95.83%
- recall: 80.50%
- F1: 87.50%
- false positive rate: 5.83% (7/120)
- false negative rate: 19.50% (39/200)
- ambiguous accuracy: 43.33% (13/30)
- unsupported obfuscation: 전체의 5.94% (19/320), 현재 recall 0%

이 수치는 현재 데이터셋에 대한 baseline이며 실제 트래픽의 발생 비율을 뜻하지 않는다.
운영 적용 전 독립 리뷰와 실제 서비스 정책에 맞는 라벨 검증이 필요하다.

## A. 즉시 고쳐야 할 명백한 false positive

- `3개년`, `5개년`, `몇 개년`이 unconditional `개년` substring 때문에 BLOCK된다.
  숫자/수량 표현 뒤의 `개년`을 안전하게 구분하는 deterministic 조건을 별도 Phase에서
  우선 검토해야 한다.

이번 Phase에서는 production dictionary나 core를 변경하지 않았다.

## B. Deterministic rule로 개선 가능한 false negative

- dictionary-aware separator에 `/`를 제한적으로 추가하는 방안
- `ㅅ ㅂ`, `ㅆ.ㅂ`, `ㅈ-ㄴ`처럼 검토된 shorthand alias
- `시바`, `씨바`, `시팔`, `지럴`, `븅신` 같은 known variant alias
- scheme 없는 domain/URL을 경계 조건과 함께 탐지하는 별도 URL rule

`+`, `@`는 정상 기술 문자열과 이메일 문맥에 자주 나타나므로 `/`보다 더 보수적인
검토가 필요하다. 각 변경은 positive/negative corpus를 먼저 추가해야 한다.

## C. Rule 추가 시 false positive 위험이 큰 ambiguous case

- `병신년`: 육십갑자와 모욕 표현이 동일 문자열이다.
- `야동초등학교`, `후장식`: 고유명사나 전문 용어가 unconditional keyword를 포함한다.
- `보지`, `자지`, `애널`: 일반 활용형과 성적 의미가 겹친다.
- `한남`, `한녀`: 인용, 설명, 공격적 사용을 문자열만으로 구분하기 어렵다.

전역 exception이나 unconditional keyword 추가는 반대 방향의 오류를 만들 수 있다.

## D. Semantic/context moderation이 필요한 case

- dictionary에 없는 모욕 또는 혐오 문맥
- 직접적인 단어 없이 이루어지는 성적 요구와 성희롱
- 홍보 문구의 의도 및 대화방 전체에 걸친 반복 전송
- 새로 등장하는 은어와 문맥에 따라 의미가 달라지는 표현

이 그룹은 단일 메시지 keyword/rule만으로 안전하게 해결하기 어렵다. 향후 semantic
moderation을 평가할 때 같은 `evaluation.tsv`의 deterministic baseline과 비교한다.

## Fixed benchmark baseline

고정된 `benchmark-messages.txt` 50건을 사용하며 warmup 20회, 측정 200회로
총 10,000개 메시지를 처리한다. Phase 3.8 최초 기준값은 다음과 같다.

- average: 12.221 µs
- p50: 9.001 µs
- p95: 26.603 µs
- p99: 58.107 µs

이후 같은 머신/JDK/JVM 조건과 동일 dataset을 유지한 실행끼리만 직접 비교한다.
이 benchmark는 CI pass/fail 기준이 아니다.

## Phase 3.9 calibration result

Phase 3.8의 `evaluation.tsv`는 이제 calibration/dev dataset이며 문장이 겹치지 않는
`evaluation-holdout.tsv` 160건을 최종 일반화 평가에 사용한다. Holdout 결과를 본 뒤
개별 실패에 맞춘 production rule 변경은 하지 않았다.

### Calibration dataset

- accuracy: 90.00% (288/320), 이전 85.63%
- precision: 96.15%, 이전 95.83%
- recall: 87.50%, 이전 80.50%
- F1: 91.62%, 이전 87.50%
- FPR: 5.83% (7/120), 이전과 동일
- FNR: 12.50% (25/200), 이전 19.50%

`개년` 관련 FP 3건은 해소됐지만 scheme-less domain을 설명하는 정상 문장 3건이
새 URL FP가 되어 전체 FP 수는 그대로다. URL recall은 80%에서 100%로 개선됐지만
URL precision은 100%에서 89.29%로 하락했다.

### Holdout dataset

- accuracy: 83.13% (133/160)
- precision: 95.35%
- recall: 78.10%
- F1: 85.86%
- FPR: 7.27% (4/55)
- FNR: 21.90% (23/105)

Holdout은 NORMAL 49/50, PROFANITY 20/20, SEXUAL_CONTENT 15/15,
PERSONAL_INFORMATION 15/15, URL 11/15, SPAM 6/10, OBFUSCATION 15/25,
AMBIGUOUS 2/10이다.

새로 확인된 `십이 개년` FP는 `이개년` keyword의 인접 음절 사이 공백이 detection
view에서 제거되며 발생한다. 이는 holdout 관찰 결과로 기록하며 Phase 3.9에서 추가
예외나 rule로 보정하지 않는다.

### Phase 3.9 fixed benchmark

동일한 50개 메시지, warmup 20회, 측정 200회 조건의 결과다.

- average: 17.725 µs
- p50: 10.900 µs
- p95: 40.300 µs
- p99: 92.702 µs

Phase 3.8 기록보다 높지만 단일 `System.nanoTime()` 실행이므로 기능 변경에 의한
증가와 JVM/호스트 변동을 분리할 수 없다. CI 기준으로 사용하지 않는다.
