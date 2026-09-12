# Architecture

## 1. 목표

이 프로젝트는 특정 웹 서비스에 종속되지 않는 Java 기반 채팅 Moderation Core를 만든다.

현재 예상 실제 서비스 환경:

- Java 21
- Spring Boot 3.4 계열
- WebSocket
- Docker
- Redis/ElastiCache
- PostgreSQL
- Spring AI
- Grafana

하지만 Core는 위 인프라를 몰라야 한다.

## 2. Core Architecture

```text
                     +----------------------+
message ------------>| ChatModerationService|
                     +----------+-----------+
                                |
                                v
                     +----------------------+
                     | MessageNormalizer    |
                     +----------+-----------+
                                |
                                v
                     +----------------------+
                     | KeywordMatcher       |
                     | (Aho-Corasick)       |
                     +----------+-----------+
                                |
                                v
                     +----------------------+
                     | Rule / Regex Filter  |
                     +----------+-----------+
                                |
                                v
                     +----------------------+
                     | ModerationPolicy     |
                     +----------+-----------+
                                |
                                v
                     ALLOW / MASK / BLOCK
```

## 3. 책임

### MessageNormalizer
탐지 전에 문자열을 정규화한다.

초기에는 보수적으로 구현한다.
과도한 정규화로 정상 문장이 차단되는 것을 피한다.

### KeywordMatcher
여러 금칙어 패턴을 한 번에 찾는다.

MVP 구현은 Aho-Corasick을 직접 구현하는 것을 우선 검토한다.

### Rule Filter
정규식 기반 패턴을 담당한다.

예:
- 전화번호
- 이메일
- URL
- 반복 문자

Phase 1에는 넣지 않아도 된다.

### ModerationPolicy
발견된 Match/Reason을 최종 Action으로 변환한다.

예:
- PROFANITY -> BLOCK
- SEXUAL_CONTENT -> BLOCK
- PHONE_NUMBER -> MASK
- URL -> 정책에 따라 BLOCK/ALLOW

Keyword Matcher의 index는 normalized 문자열 기준이므로 reason-only finding으로
정책에 전달한다. Rule Filter는 양끝 공백을 제거한 canonical 원문에서 실행하며,
MASK에는 RuleMatch의 canonical 원문 range만 사용한다. normalized index와 원문
range 사이의 offset mapping은 현재 구현하지 않는다.

PROFANITY와 SEXUAL_CONTENT keyword 및 false-positive exception은 하나의
Aho-Corasick automaton으로 구축한다. Match된 normalized token을 category metadata로
변환하며, exception은 명시적으로 연결된 keyword의 range를 덮는 match만 무효화한다.

Keyword 탐지는 normalized baseline view와 선택적인 separator-collapsed view를 사용한다.
두 번째 view는 사전 keyword의 인접 한글 음절 사이에서만 공백, ASCII 숫자,
`.`, `-`, `_`, `*`, `/`를 제거한다. 실제 변환이 있을 때만 같은 automaton을 한 번 더
검색한다. Detection view와 그 index는 reason-only 판정에만 사용하며 canonical
output이나 개인정보 MASK range에는 사용하지 않는다.

초성 및 임의 삽입 문자 변형은 fuzzy matching 대신 명시적인 alias로 등록한다.

Scheme-less URL은 hostname 경계와 제한된 TLD 목록(`com`, `net`, `org`, `io`,
`dev`, `ai`, `co.kr`)을 사용한다. 이메일의 `@domain`과 `localhost`, 버전 문자열,
목록에 없는 `foo.bar` 형태는 URL match에서 제외한다.

## 4. Spring Integration

최종 Spring 프로젝트에서는 Core 외부에 Adapter를 둔다.

```text
WebSocket Handler
      |
      v
Spring Adapter
      |
      v
ChatModerationService
      |
      +------> Redis Rate Limit
      |
      +------> AI Moderation (선택)
      |
      v
Broadcast
```

Core에 `@Service`, `RedisTemplate`, WebSocket API를 직접 넣지 않는다.

## 5. 운영 원칙

- moderation은 broadcast 전에 수행
- automaton은 재사용
- 외부 AI 장애가 채팅 전체 장애로 번지지 않도록 fallback 정책 필요
- 로그에 원문 개인정보를 과도하게 남기지 않기
- 향후 Micrometer metric 연동 가능

## 6. Semantic Experiment Boundary

Phase 3.10과 3.11의 semantic provider, router, hybrid moderator 및 evaluation runner는
모두 `src/test`에 있다. OpenAI provider를 추가해도 production `src/main` API와
deterministic 동작은 바뀌지 않는다.

실제 provider는 router가 `NEEDS_SEMANTIC_REVIEW`로 분류한 메시지만 hybrid 경로에서
호출한다. deterministic 결과가 MASK이면 provider 입력은 항상 `outputMessage`이며,
원문 개인정보를 전송하지 않는다. API failure는 기존 `ProviderFailurePolicy`에서 처리한다.

Phase 3.11b의 Moderation API provider는 custom prompt를 사용하지 않는다. API가 반환한
native category map을 별도 metadata로 보존하고, 현재 core reason과 억지로 매핑하지
않는다. 실제 category 이름은 고정 목록이 아니라 응답 객체의 key를 동적으로 수집한다.

Phase 3.12의 score threshold와 dataset도 `src/test`에만 존재한다. Threshold 선택기는
provider의 `flagged` 기본 판정을 변경하지 않고 보존된 category score 위에 application
policy 후보를 별도로 계산한다. Calibration과 holdout은 resource와 실행 순서가 분리되며,
선택 과정은 historical semantic dataset이나 holdout 결과를 읽지 않는다.

Phase 3.13 Luna baseline 역시 `src/test`에만 있다. 모든 메시지에서 얻은 동일한 Luna
판정을 Luna-only와 frozen-router counterfactual에 함께 사용해 모델 변동성과 router
손실을 분리한다. Production core와 router에는 변경을 가하지 않는다.
