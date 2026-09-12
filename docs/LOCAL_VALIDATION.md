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

Exception 파일은 `차단 keyword<TAB>허용 표현` 형식이다. 현재 `시발<TAB>시발점`은
`시발점`이 덮는 `시발` match만 무효화하고 같은 메시지의 다른 욕설에는 영향을 주지 않는다.

## Corpus

`src/test/resources/moderation/corpus.tsv`는 탭으로 구분된 다음 다섯 필드를 가진다.

```text
category  message  expectedAction  expectedReason  expectedOutput
```

- category: `NORMAL`, `PROFANITY`, `SEXUAL_CONTENT`, `PHONE`, `EMAIL`, `URL`, `SPAM`, `EDGE_CASE`
- expectedReason: 검증하지 않을 때 `-`
- expectedOutput: 검증하지 않을 때 `-`, BLOCK output은 `<NULL>`, 빈 output은 `<EMPTY>`
- message와 output 안의 `\t`, `\n`은 실제 탭과 줄바꿈으로 해석한다.

JUnit parameterized test가 모든 행을 독립된 test case로 실행한다. 별도 coverage
test는 corpus가 200건 이상이며 모든 category를 포함하는지 확인한다.

`보지`, `자지`, `애널`은 정상 문맥과 구분할 context rule이 없으므로 unconditional
사전에서 제외한다. 숫자/공백 삽입, 초성, 자모 변형, 유사문자, leetspeak 탐지는
Phase 3.7 이전에는 수행하지 않는다.

## Benchmark

```bash
./gradlew moderationBenchmark
./gradlew moderationBenchmark --args='200'
```

인자는 corpus 반복 횟수이며 기본값은 100이다. 5회 warmup 후 총 메시지 수,
총 처리 시간, 평균 latency, p50, p95, p99를 출력한다. `System.nanoTime()` 기반의
로컬 비교 도구이므로 JMH를 대체하지 않으며 CI 성공/실패 조건으로 사용하지 않는다.

## Backend contract simulation

`BackendContractSimulationTest`는 실제 backend 없이 persistence 직전 계약을 검증한다.

- BLOCK: 저장할 content가 없다.
- MASK: 원문이 아니라 마스킹된 `result.outputMessage()`를 저장한다.
- ALLOW: 반환된 canonical `result.outputMessage()`를 저장한다.
