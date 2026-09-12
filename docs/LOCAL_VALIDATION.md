# Local Validation Harness

이 도구들은 `src/test`에만 있으며 production artifact와 공개 Core API에 포함되지 않는다.

## Playground

```bash
./gradlew moderationPlayground
```

한 줄에 메시지 하나를 입력한다. 각 입력에 대해 `action`, `reasons`,
`outputMessage`를 출력하며 EOF(`Ctrl-D`)로 종료한다. 사전은 다음 resource에서 읽는다.

- `profanity-keywords.txt`
- `sexual-keywords.txt`
- `keyword-exceptions.txt`
- `obfuscation-aliases.tsv`

Exception 파일은 `차단 keyword<TAB>허용 표현` 형식이다. 현재 `시발<TAB>시발점`은
`시발점`이 덮는 `시발` match만 무효화하고 같은 메시지의 다른 욕설에는 영향을 주지 않는다.
Alias 파일은 `alias<TAB>reason` 형식이며 `ㅅㅂ`, `ㅆㅂ`, `ㅈㄴ`, `씨아발`처럼
검토된 표현만 등록한다.

## Corpus

`src/test/resources/moderation/corpus.tsv`는 탭으로 구분된 다음 다섯 필드를 가진다.

```text
category  message  expectedAction  expectedReason  expectedOutput
```

- category: `NORMAL`, `PROFANITY`, `SEXUAL_CONTENT`, `OBFUSCATION`, `PHONE`, `EMAIL`, `URL`, `SPAM`, `EDGE_CASE`
- expectedReason: 검증하지 않을 때 `-`
- expectedOutput: 검증하지 않을 때 `-`, BLOCK output은 `<NULL>`, 빈 output은 `<EMPTY>`
- message와 output 안의 `\t`, `\n`은 실제 탭과 줄바꿈으로 해석한다.

JUnit parameterized test가 모든 행을 독립된 test case로 실행한다. 별도 coverage
test는 corpus가 200건 이상이며 모든 category를 포함하는지 확인한다.

`보지`, `자지`, `애널`은 정상 문맥과 구분할 context rule이 없으므로 unconditional
사전에서 제외한다. 숫자/공백/제한된 구분자 삽입은 사전의 인접 한글 음절 쌍에
한정해 detection view에서 제거한다. 범용 문자 삭제나 fuzzy matching은 사용하지 않는다.

현재 지원하지 않는 `/`, `+`, `@` separator, 자모 변형, 유사문자, leetspeak,
phonetic similarity는 corpus에서 허용 동작 또는 알려진 한계로 관리한다.

## Accuracy evaluation

```bash
./gradlew moderationEvaluation
```

`evaluation.tsv`는 Phase 3.8 이후 calibration/dev dataset으로 사용한다.
`evaluation-holdout.tsv`는 문장이 겹치지 않는 독립 합성 holdout 160건이며 Phase 3.9
구현을 고정한 뒤 최종 평가에만 사용한다. 현재 미지원 기능과 ambiguous 문맥도 기대
정책대로 라벨링하므로 오판이 있어도 task는 성공한다. Runner는 두 dataset 각각의
TP/TN/FP/FN, precision/recall/F1, FPR/FNR, reason별 지표, action confusion matrix,
category 정확도와 전체 오판 사례를 구분해 출력한다.

실제 개인정보는 사용하지 않으며 모든 전화번호와 이메일은 평가용 가짜 값이다.
Calibration 해석은 [Policy Calibration](POLICY_CALIBRATION.md)에 기록한다.

## Benchmark

```bash
./gradlew moderationBenchmark
```

`benchmark-messages.txt`의 고정 메시지 50건을 사용한다. Warmup 20회와 측정 200회를
고정하고 dataset 수, 총 측정 메시지 수, 총 처리 시간, 평균 latency, p50, p95,
p99를 출력한다. `System.nanoTime()` 기반의 로컬 비교 도구이므로 JMH를 대체하지
않으며 CI 성공/실패 조건으로 사용하지 않는다. 서로 다른 머신/JVM 실행은 직접
비교하지 않는다.

## Backend contract simulation

`BackendContractSimulationTest`는 실제 backend 없이 persistence 직전 계약을 검증한다.

- BLOCK: 저장할 content가 없다.
- MASK: 원문이 아니라 마스킹된 `result.outputMessage()`를 저장한다.
- ALLOW: 반환된 canonical `result.outputMessage()`를 저장한다.
