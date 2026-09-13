# Chat Moderation

## 프로젝트 소개

Chat Moderation은 Java 21 기반의 재사용 가능한 채팅 moderation 라이브러리입니다. 특정 웹 프레임워크나 전송 계층에 의존하지 않는 `framework-independent core`를 제공하며, 명시적인 신호를 처리하는 deterministic moderation과 문맥 판단을 위한 optional semantic moderation을 하나의 API로 사용할 수 있습니다.

외부 진입점은 동기식 계약인 `ModerationResult moderate(String message)`로 유지됩니다. Spring Boot, WebSocket, Redis, 데이터베이스 같은 애플리케이션 인프라는 라이브러리 밖에 둡니다.

## 주요 기능

- 욕설과 강한 욕설 표현을 `PROFANITY`로 탐지하고 기본 정책에서 차단
- 지원 사전에 포함된 성적 표현을 `SEXUAL_CONTENT`로 탐지
- 전화번호와 이메일을 원문 길이만큼 `*`로 마스킹
- scheme 포함 URL과 제한된 TLD의 scheme-less URL 탐지
- 동일 문자의 과도한 반복을 spam 신호로 탐지
- Unicode NFC, 영문 소문자화, 양끝 공백 제거 및 제한적인 구분자 제거용 detection view
- 검토된 초성·변형 표현 alias와 keyword별 false-positive exception
- 여러 keyword를 한 번에 검색하고 구축된 automaton을 재사용하는 Aho-Corasick matcher
- deterministic 결과와 문맥 신호를 사용하는 confidence-aware semantic gate
- `SemanticModerationProvider` 확장 지점과 OpenAI Responses API provider
- `FAIL_OPEN`, `FAIL_CLOSED`, `DETERMINISTIC_FALLBACK` provider failure policy
- semantic provider 호출 전 전화번호·이메일 sanitization

## 아키텍처

```text
input
  -> canonical / normalized / detection views
  -> deterministic rules
       -> Aho-Corasick keywords
       -> phone / email / URL / repeated-character rules
       -> policy
  -> confidence-aware semantic gate
  -> optional semantic provider
  -> ALLOW / MASK / BLOCK
```

`canonical`은 입력의 양끝 공백을 제거한 문자열이며, normalization과 obfuscation detection view는 탐지에만 사용됩니다. 개인정보 마스킹은 canonical 원문의 range를 기준으로 수행합니다.

저장하거나 broadcast하는 consumer는 raw input을 다시 사용하면 안 됩니다. `result.allowed()`를 먼저 확인하고, 허용된 결과는 반드시 `result.outputMessage()`를 사용해야 합니다. `MASK` 결과의 `outputMessage()`에는 이미 개인정보가 마스킹되어 있습니다.

## 왜 Hybrid Moderation인가?

Deterministic moderation은 금칙어, 개인정보, URL, 반복 문자처럼 명시적인 신호를 빠르고 재현 가능하게 처리합니다. 네트워크 없이 동작하며 동일한 입력과 설정에 대해 일관된 결과를 냅니다.

반면 신고·인용·교육 문맥, 간접 모욕, 문맥에 따라 의미가 달라지는 표현은 단순 keyword match만으로 구분하기 어렵습니다. Semantic moderation은 이런 불확실성이 있는 후보에만 선택적으로 적용됩니다. 이 구조는 명확한 사례는 로컬에서 처리하고, 문맥 판단이 유용한 구간에만 외부 provider 비용과 latency를 사용하기 위한 것입니다.

## 요구사항

- Java 21
- 저장소에 포함된 Gradle Wrapper
- Semantic mode를 사용할 때만 `OPENAI_API_KEY`

## 빌드 / 테스트

```bash
./gradlew clean test
./gradlew clean build
```

빌드된 library JAR은 `build/libs/chat-moderation-0.2.0.jar`에 생성됩니다. 아직 Maven repository에 publish되지 않았으므로 다른 Gradle 프로젝트에서 파일로 사용할 때는 JAR과 현재 runtime dependency를 함께 선언해야 합니다.

```groovy
dependencies {
    implementation files('libs/chat-moderation-0.2.0.jar')
    implementation 'com.fasterxml.jackson.core:jackson-databind:2.19.2'
}
```

## Quick Start — Deterministic

```java
import dev.chatmoderation.core.ChatModerationService;
import dev.chatmoderation.core.ChatModerationServices;
import dev.chatmoderation.core.model.ModerationResult;

public class DeterministicExample {
    public static void main(String[] args) {
        ChatModerationService moderation = ChatModerationServices.deterministic();
        ModerationResult result = moderation.moderate("연락처는 010-1234-5678입니다");

        if (!result.allowed()) {
            return;
        }

        String safeMessage = result.outputMessage();
        System.out.println(safeMessage);
    }
}
```

`ChatModerationServices.deterministic()`은 기본 keyword, exception, alias와 rule을 로드하고 Aho-Corasick automaton을 한 번 구축합니다. 애플리케이션 lifecycle 동안 service instance를 재사용하는 것이 권장됩니다.

## Quick Start — Semantic

```java
import dev.chatmoderation.core.ChatModerationService;
import dev.chatmoderation.core.ChatModerationServices;
import dev.chatmoderation.core.model.ModerationResult;
import dev.chatmoderation.semantic.ProviderFailurePolicy;
import dev.chatmoderation.semantic.SemanticModerationProvider;
import dev.chatmoderation.semantic.openai.OpenAiLunaModerationProvider;
import dev.chatmoderation.semantic.openai.OpenAiLunaProviderConfig;

public class SemanticExample {
    public static void main(String[] args) {
        OpenAiLunaProviderConfig config = OpenAiLunaProviderConfig.fromEnvironment()
                .orElseThrow(() -> new IllegalStateException("OPENAI_API_KEY is required"));
        SemanticModerationProvider provider = new OpenAiLunaModerationProvider(config);
        ChatModerationService moderation = ChatModerationServices.withSemanticProvider(
                provider,
                ProviderFailurePolicy.DETERMINISTIC_FALLBACK
        );

        ModerationResult result = moderation.moderate("검토할 메시지");
        if (result.allowed()) {
            System.out.println(result.outputMessage());
        }
    }
}
```

`OpenAiLunaProviderConfig.fromEnvironment()`가 지원하는 runtime 환경 변수는 다음과 같습니다.

- `OPENAI_API_KEY`: 필수
- `OPENAI_SEMANTIC_MODEL`: 선택, 기본값 `gpt-5.6-luna`
- `OPENAI_SEMANTIC_TIMEOUT_MS`: 선택, 기본값 `30000`

`OPENAI_API_KEY`는 실행 환경에서 주입해야 하며 source, resource, 설정 예제 또는 Git history에 commit하면 안 됩니다. Semantic-enabled service는 동기식이므로 provider로 routing된 호출은 응답 또는 timeout까지 현재 thread를 점유합니다.

## 결과 계약

| `action()` | `allowed()` | `outputMessage()` | 의미 |
|---|---:|---|---|
| `ALLOW` | `true` | canonical message | 그대로 처리 가능 |
| `MASK` | `true` | 마스킹된 canonical message | 반환 메시지만 저장·전송 가능 |
| `BLOCK` | `false` | `null` | 저장·전송 중단 |

`reasons()`는 `PROFANITY`, `PERSONAL_INFORMATION`, `URL`, `SPAM`, `HARASSMENT`, `SEXUAL_CONTENT`, `OTHER` 중 탐지·판정 이유를 불변 목록으로 제공합니다. 여러 신호가 동시에 발견되면 기본 action 우선순위는 `BLOCK > MASK > ALLOW`입니다.

## 개인정보 및 보안

- Credential은 runtime configuration으로만 주입하며 JAR에 API key를 포함하지 않습니다.
- `OpenAiLunaProviderConfig.toString()`은 API key를 redaction합니다.
- 외부 semantic provider로 routing하기 전에 전화번호와 이메일을 별도로 sanitization합니다.
- OpenAI Responses API 요청은 `store=false`로 구성됩니다.
- Semantic `ALLOW` 또는 `FAIL_OPEN`이 반환되어도 이미 마스킹된 raw PII는 복원하지 않습니다.
- 로컬에서 최종 판정된 메시지는 semantic provider에 전송되지 않습니다.

## 테스트

현재 테스트 suite는 다음 범주를 포함합니다.

- 정상 문장과 차단 문장을 함께 다루는 deterministic moderation regression
- Aho-Corasick의 중복 pattern, 접두사·접미사 중첩, 다중 match, 한글·영문·특수문자, 긴 입력
- normalization, obfuscation alias, separator insertion, keyword exception과 false positive
- 전화번호, 이메일, URL, repeated-character rule 및 masking range
- policy action 우선순위와 `ModerationResult` 계약
- semantic gate routing, privacy sanitization, provider failure policy
- OpenAI Responses API request/structured output parsing을 fake transport로 검증하는 테스트
- 외부 package에서 public API를 생성·주입하는 consumer smoke test
- calibration/holdout dataset 독립성과 backend persistence 계약 simulation

일반 `test`와 `build`에는 API key나 실제 network 호출이 필요하지 않습니다. Credential-gated live evaluation task는 일반 테스트와 분리되어 있습니다.

## Evaluation

저장소에는 deterministic corpus/evaluation, semantic 비교, threshold calibration, router/gate calibration과 sealed holdout, credential-gated live provider 평가 작업이 포함되어 있습니다. 평가 runner와 dataset은 `src/test`에 있으며 production JAR에는 포함되지 않습니다.

동결된 320건 sealed holdout에서 수행한 Phase 3.16 live 실험 결과는 다음과 같습니다.

| mode | accuracy | precision | recall | F1 | FPR | FNR |
|---|---:|---:|---:|---:|---:|---:|
| Luna 100% | 93.75% | 100% | 87.50% | 93.33% | 0% | 12.50% |
| Phase 3.14 + Luna | 70.00% | 78.57% | 55.00% | 64.71% | 15.00% | 45.00% |
| Phase 3.15 + Luna | 98.13% | 100% | 96.25% | 98.09% | 0% | 3.75% |

이 수치는 고정된 실험 dataset, policy prompt, model 및 당시 실행 조건에서의 결과입니다. 임의의 실제 트래픽에 대한 정확도, latency, 비용 또는 production SLA를 보장하지 않습니다. 자세한 평가 조건과 해석은 [Final Luna Evaluation](docs/FINAL_LUNA_EVALUATION.md)과 [Local Validation](docs/LOCAL_VALIDATION.md)을 참고하세요.

## Spring Boot 통합

Core library 자체에는 Spring dependency가 없습니다. Host 애플리케이션에서 다음처럼 singleton Bean으로 등록할 수 있습니다.

```java
import dev.chatmoderation.core.ChatModerationService;
import dev.chatmoderation.core.ChatModerationServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ModerationConfiguration {
    @Bean
    ChatModerationService chatModerationService() {
        return ChatModerationServices.deterministic();
    }
}
```

별도의 익명 Spring Boot backend와 통합해 PostgreSQL, Redis, transaction/idempotency, bounded executor 및 integration test를 포함한 경로에서 library contract를 검증했습니다. 이는 이 저장소가 production에 배포되었다는 의미는 아닙니다.

Transaction, persistence, rate limiting, executor·timeout 운영, HTTP response mapping, metrics와 broadcast 순서는 host 애플리케이션의 책임입니다. 반드시 persistence와 broadcast 전에 moderation을 실행하고, `BLOCK`은 두 작업을 모두 중단하며, `ALLOW`와 `MASK`는 `outputMessage()`만 사용해야 합니다.

## 프로젝트 구조

```text
src/main/java       public API와 deterministic/semantic 구현
src/main/resources  기본 keyword, exception, obfuscation alias
src/test/java       단위·통합·평가 harness
src/test/resources  regression, calibration, holdout dataset
docs                아키텍처, 정책, 실험 및 검증 보고서
```

지원되는 Java module은 `dev.chatmoderation`이며 consumer API package는 다음 네 개입니다.

- `dev.chatmoderation.core`
- `dev.chatmoderation.core.model`
- `dev.chatmoderation.semantic`
- `dev.chatmoderation.semantic.openai`

## 설계 원칙

- Framework-independent core와 작은 public API
- 명시적 신호를 먼저 처리하는 deterministic-first
- 문맥 판단의 가치가 있는 경우에만 semantic provider 호출
- 외부 호출보다 먼저 개인정보 sanitization
- Provider 장애 동작을 `ProviderFailurePolicy`로 명시
- Automaton과 service instance 재사용
- 알고리즘, policy, provider를 독립적으로 검증할 수 있는 testability

## 제한사항

- Build target과 runtime 요구사항은 Java 21입니다.
- 기본 rule, keyword, alias 및 평가 dataset은 한국어 채팅에 초점을 둡니다.
- 지원하지 않는 자모 변형, 임의 문자 삽입, leetspeak, phonetic similarity가 있으며 범용 fuzzy matching을 제공하지 않습니다.
- Semantic provider는 network, 외부 API availability, latency와 비용에 의존합니다.
- Semantic 호출은 동기식이며 library는 executor, retry, circuit breaker, rate limiter 또는 metrics bridge를 제공하지 않습니다.
- Host 애플리케이션이 transaction, persistence, rate limiting, HTTP/WebSocket 처리와 broadcast 순서를 구현해야 합니다.
- 포함된 합성·고정 evaluation dataset의 결과는 임의의 production traffic을 보장하지 않습니다.
- 현재 artifact repository에 publish되지 않아 file-based JAR 사용 시 runtime dependency를 직접 추가해야 합니다.

## Roadmap

- Artifact repository / Maven publication
- Spring Boot starter
- 추가 언어와 rule coverage
- 더 세분화된 provider error taxonomy

## License

현재 저장소에는 license가 지정되어 있지 않습니다.
