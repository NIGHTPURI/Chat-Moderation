# Production Library 0.2.0

## Artifact and supported API

Phase 4A produces a normal Java 21 library at:

```text
build/libs/chat-moderation-0.2.0.jar
```

The named module is `dev.chatmoderation`. Its supported exported packages are:

- `dev.chatmoderation.core`
- `dev.chatmoderation.core.model`
- `dev.chatmoderation.semantic`
- `dev.chatmoderation.semantic.openai`

Matcher, normalization, rule, policy, routing signal, HTTP transport, prompt, and resource-loader
packages/classes are not exported as consumer API.

## Construction and use

Existing deterministic callers keep the synchronous contract:

```java
ChatModerationService moderation = ChatModerationServices.deterministic();
ModerationResult result = moderation.moderate(message);
```

A semantic provider is injected explicitly:

```java
OpenAiLunaProviderConfig config = OpenAiLunaProviderConfig.luna(
        System.getenv("OPENAI_API_KEY"),
        Duration.ofSeconds(3)
);
SemanticModerationProvider provider = new OpenAiLunaModerationProvider(config);
ChatModerationService moderation = ChatModerationServices.withSemanticProvider(
        provider,
        ProviderFailurePolicy.DETERMINISTIC_FALLBACK
);

ModerationResult result = moderation.moderate(message);
```

The semantic-enabled service preserves `ModerationResult moderate(String)`. It is deliberately
synchronous: a routed call blocks its calling thread until the provider responds or its configured
timeout expires. The final validated Luna run observed 942 ms average, 1306 ms p95, and 1951 ms
p99 latency. A backend must therefore call it from a bounded worker executor rather than a
WebSocket/event-loop thread.

## JAR consumption

When the JAR is copied directly into a consumer repository, add it and its JSON dependency:

```groovy
dependencies {
    implementation files('libs/chat-moderation-0.2.0.jar')
    implementation 'com.fasterxml.jackson.core:jackson-databind:2.19.2'
}
```

Jackson Databind brings Jackson Core and Jackson Annotations 2.19.2 transitively. The Java HTTP
client comes from JDK 21. A later Maven repository publication can carry this dependency metadata;
Phase 4A does not publish the artifact.

## Provider configuration

`OpenAiLunaProviderConfig` accepts endpoint, API key, model, and timeout explicitly. The shortcut
`luna(apiKey, timeout)` uses:

- endpoint: `https://api.openai.com/v1/responses`
- model: `gpt-5.6-luna`
- caller-supplied timeout

`fromEnvironment()` is an optional adapter for `OPENAI_API_KEY`, `OPENAI_SEMANTIC_MODEL`, and
`OPENAI_SEMANTIC_TIMEOUT_MS`. No credential is embedded in source, resources, or the JAR. Config
string rendering redacts the key.

The provider reuses the frozen Phase 3.13 policy prompt and strict structured-output contract. It
sets `store=false` and maps provider reasons into the existing core `ModerationReason` values.

## Failure behavior

Timeout, non-2xx HTTP response, malformed response, interruption, and transport failure produce an
explicit non-success `SemanticModerationResult`. A thrown or null provider result is also handled by
the semantic service. The configured policy then decides the final result:

| policy | result when semantic review fails |
|---|---|
| `DETERMINISTIC_FALLBACK` | preserve deterministic result |
| `FAIL_CLOSED` | BLOCK with `OTHER` |
| `FAIL_OPEN` | ALLOW, while preserving any sanitized MASK |

`DETERMINISTIC_FALLBACK` is the recommended initial production policy because it preserves the
existing local moderation contract during provider incidents. The application must choose a policy
explicitly when constructing the service.

## Privacy

Phone numbers and email addresses are sanitized independently of the final deterministic action
before any routed message reaches an external provider. This also covers messages where a BLOCK
reason outranks a simultaneous personal-information MASK.

- The provider receives only the sanitized message.
- Semantic ALLOW cannot restore the original sensitive content.
- `FAIL_OPEN` cannot restore the original sensitive content.
- Messages finalized locally never reach the provider.

## Threading and lifecycle

Create and reuse one service/provider instance. The deterministic automaton is immutable and reused;
the production OpenAI provider uses the JDK thread-safe `HttpClient`. The supplied provider must also
be safe for concurrent calls if the service is shared. The library does not create executors, retry
requests, rate-limit, or schedule work.

At the Phase 3.16 observed average API cost of `$0.00014198/request`, actual monthly cost depends on
the live routing distribution. The production-like offline routing estimate remains 29.80%; cost and
latency monitoring belongs in the future backend adapter.

## Future Spring Boot consumption

`YoungManRest_BE` will later place the JAR under normal Gradle dependency management, create the
configuration/provider/service as Spring beans, execute semantic moderation on a bounded worker,
and call `moderate` before persistence or broadcast. The adapter must set a timeout/failure policy,
record provider/routing metrics without message bodies, and preserve the returned MASK output.
