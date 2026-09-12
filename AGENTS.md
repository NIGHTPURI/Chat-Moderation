# AGENTS.md

이 저장소는 실시간 익명 채팅용 Moderation Core를 구현하기 위한 학습/실전 프로젝트다.

## 절대 원칙

1. Java 21을 사용한다.
2. 초기 Core 구현에는 Spring Boot를 넣지 않는다.
3. FastAPI/Python 서비스를 추가하지 않는다.
4. WebSocket 코드와 Moderation Core를 직접 결합하지 않는다.
5. 핵심 알고리즘은 단위 테스트 가능해야 한다.
6. 메시지를 broadcast한 뒤 검사하는 구조를 만들지 않는다.
7. 금칙어마다 `String.contains()`를 반복하는 구현을 최종안으로 사용하지 않는다.
8. 모든 메시지를 생성형 LLM에 보내는 구조를 기본값으로 만들지 않는다.
9. 성능 최적화보다 먼저 false positive / false negative 테스트를 작성한다.
10. 한 번에 전체 기능을 구현하지 말고 ROADMAP의 Phase 단위로 진행한다.

## 설계 방향

외부에서 보이는 진입점은 최대한 단순해야 한다.

```java
ModerationResult moderate(String message);
```

내부 구현은 책임별로 분리한다.

- normalization
- keyword matching
- regex/rule
- policy
- result model
- later: spam/rate limiting
- later: AI moderation
- later: Spring adapter

## Aho-Corasick

Aho-Corasick은 금칙어 다중 패턴 검색 엔진 역할만 담당한다.

Aho-Corasick 자체가 "이 표현이 실제 욕설인지"를 판단한다고 가정하지 않는다.

- Trie
- Failure Link
- Output/Match
- BFS 기반 failure link 구축
- 이미 구축된 automaton 재사용

메시지마다 Trie/Automaton을 새로 생성하면 안 된다.

## 테스트

정상 문장 테스트와 차단 문장 테스트를 반드시 같이 만든다.

특히 아래를 고려한다.

- 정확한 금칙어
- 중복 패턴
- 접두사/접미사 중첩
- 한 메시지에 여러 금칙어
- 빈 문자열
- 매우 긴 문자열
- 한글
- 영문
- 특수문자
- 정규화 전/후 차이
- false positive

## 코딩 스타일

- 과도한 추상화 금지
- 의미 없는 디자인 패턴 금지
- public API는 작게 유지
- 불변 객체/record를 적극 고려
- 메서드 이름으로 의도를 드러낼 것
- 테스트 없는 알고리즘 최적화 금지

## 작업 후 보고 형식

Codex는 각 Phase 종료 시 아래를 보고한다.

1. 생성/수정 파일
2. 구현 내용
3. 핵심 설계 판단
4. 테스트 결과
5. 시간복잡도/공간복잡도
6. 알려진 한계
7. 다음 Phase에서 할 일
