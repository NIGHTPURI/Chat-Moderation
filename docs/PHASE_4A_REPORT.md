# Phase 4A Completion Report

## 1. Files promoted to `src/main`

- Public construction API: `ChatModerationServices`
- Internal semantic orchestration: `DefaultSemanticChatModerationService`
- Frozen confidence-aware gate and internal route/state
- Semantic provider interface, result contract, and failure policy
- OpenAI Luna provider, explicit configuration, and frozen policy prompt
- Action-independent phone/email privacy sanitizer
- Validated profanity/sexual dictionaries, exception, and obfuscation aliases
- Java module descriptor exporting only supported consumer packages

Evaluation runners, telemetry/calibration utilities, test classes, and all evaluation/holdout
datasets remain outside `src/main`.

## 2. Final public API

The primary contract remains:

```java
ModerationResult ChatModerationService.moderate(String message);
```

`ChatModerationServices` constructs default deterministic or semantic-enabled implementations.
Consumers may inject any `SemanticModerationProvider` and must choose a `ProviderFailurePolicy`.
`OpenAiLunaModerationProvider` and `OpenAiLunaProviderConfig` provide the validated Luna adapter.

The library is synchronous. A routed `moderate` call waits for the provider or timeout; existing
deterministic callers do not incur a network call.

## 3. JAR

- Name: `build/libs/chat-moderation-0.2.0.jar`
- Exact size: 68,362 bytes
- Java: 21
- Module: `dev.chatmoderation`

## 4. JAR contents verification

`jar tf build/libs/chat-moderation-0.2.0.jar` and the build-time `verifyProductionJar` task confirm:

- production moderation and semantic provider classes are present;
- four required default policy resources are present;
- `module-info.class` is present;
- no test/validation classes are present;
- no evaluation, calibration, policy holdout, router holdout, or sealed gate dataset is present;
- no Markdown experiment document is present.

The JAR contains internal matcher/rule/gate bytecode required at runtime, while the Java module does
not export those packages or routing signals as supported consumer API.

## 5. Dependencies

Direct runtime dependency:

- `com.fasterxml.jackson.core:jackson-databind:2.19.2`

Transitive runtime dependencies:

- `com.fasterxml.jackson.core:jackson-core:2.19.2`
- `com.fasterxml.jackson.core:jackson-annotations:2.19.2`

HTTP and concurrency primitives use the Java 21 standard library. There are no Spring, Redis,
WebSocket, Python, or backend dependencies.

## 6. Semantic provider configuration

`OpenAiLunaProviderConfig` requires endpoint, API key, model, and timeout explicitly. The `luna`
factory uses the Responses endpoint and `gpt-5.6-luna`; its timeout remains caller-supplied.
`fromEnvironment()` optionally reads `OPENAI_API_KEY`, `OPENAI_SEMANTIC_MODEL`, and
`OPENAI_SEMANTIC_TIMEOUT_MS`. No secret is stored in source/resources/JAR, and config `toString()`
redacts the key.

## 7. Privacy behavior

Phone/email sanitization runs independently of the final deterministic action before an external
provider call. This covers MASK and BLOCK-precedence combinations. The provider receives the
sanitized string only. Semantic ALLOW and `FAIL_OPEN` cannot restore sensitive content; the final
result remains MASK when necessary.

## 8. Failure behavior

Timeout, HTTP error, malformed response, interruption, provider exception, and null/unavailable
provider results follow the explicitly selected policy:

- `DETERMINISTIC_FALLBACK`: retain the local result; recommended initial default
- `FAIL_CLOSED`: BLOCK with `OTHER`
- `FAIL_OPEN`: ALLOW while retaining any privacy MASK

The OpenAI adapter returns explicit `SUCCESS`, `TIMEOUT`, or `ERROR` status rather than inventing a
semantic decision.

## 9. Tests

- 453 tests
- 0 failures
- 0 errors
- 0 skipped
- External-package consumer construction/injection smoke test passed
- Production privacy/failure/provider parsing tests passed
- Existing frozen evaluation tests remain green

## 10. Build

- `./gradlew clean test` — passed
- `./gradlew clean build` — passed
- `verifyProductionJar` — passed
- `git diff --check` — passed
- Additional Luna API calls — none

## 11. Remaining integration risks

- The synchronous validated provider latency is average 942 ms, p95 1306 ms, p99 1951 ms;
  invoking it on a WebSocket/event-loop thread would stall unrelated connections.
- Production-like routing rate 29.80% remains an offline estimate; backend metrics must measure the
  real routing/cost distribution without logging message bodies.
- Average observed Luna request cost is `$0.00014198`; pricing and traffic mix can change.
- No retry, circuit breaker, rate limiter, executor, metrics bridge, or Spring lifecycle management
  is included in the pure Java library.
- Direct file-based JAR consumption must also supply Jackson runtime dependencies until the library
  is published with dependency metadata.

## 12. Exact next YoungManRest_BE integration step

After human review, add `chat-moderation-0.2.0.jar` as a Gradle dependency in `YoungManRest_BE` and
create a Spring-only adapter/configuration that constructs one `OpenAiLunaModerationProvider` and
one `ChatModerationService`, executes `moderate` on a bounded worker with an explicit timeout and
failure policy, and uses the returned `ModerationResult` before both persistence and WebSocket
broadcast. Add adapter tests proving BLOCK prevents both operations and MASK persists/broadcasts only
`outputMessage`. Do not change the Core JAR during that integration unless an adapter test exposes a
library contract defect.
