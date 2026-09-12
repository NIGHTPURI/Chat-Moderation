# Codex First Prompt

아래 내용을 Codex에 그대로 입력한다.

---

이 저장소의 `AGENTS.md`, `README.md`, `docs/ARCHITECTURE.md`, `docs/ROADMAP.md`, `docs/SESSION_STATE.md`를 먼저 모두 읽어라.

현재 프로젝트는 Java 21 + Gradle 기반의 재사용 가능한 실시간 채팅 Moderation Core다.

중요한 목표는 특정 Spring 프로젝트에 종속되지 않는 순수 Java Core를 먼저 만드는 것이다.

이번 작업에서는 **Phase 1만 구현**해라.

구현 범위:

1. `MessageNormalizer`의 기본 구현체
2. Aho-Corasick 기반 `KeywordMatcher`
3. Trie Node 구조
4. Failure Link 구축
5. 문자열 검색
6. 발견된 모든 Match 반환
7. 중복 패턴 / 중첩 패턴 처리
8. 한글 금칙어 지원
9. 충분한 JUnit 5 테스트

제약:

- Spring Boot 추가 금지
- FastAPI/Python 추가 금지
- Redis 추가 금지
- OpenAI/Spring AI 추가 금지
- WebSocket 추가 금지
- 메시지마다 Trie를 다시 만드는 구현 금지
- 금칙어 목록을 매 메시지마다 순회하며 `contains()`를 호출하는 구현 금지
- 불필요한 디자인 패턴/과도한 추상화 금지

Normalizer는 지나치게 공격적으로 구현하지 마라.

초기 지원 후보:
- Unicode normalization 검토
- 영문 대소문자 정규화
- 양끝 공백
- 정책상 안전한 범위의 공백 처리

`씨 발 -> 씨발`, `시1발 -> 시발` 같은 공격적인 우회욕설 정규화는 이번 Phase에서 무조건 넣지 말고,
false positive 위험과 정책 문제를 먼저 설명해라.

Aho-Corasick 구현 전 먼저 현재 interface/model이 적절한지 검토하고,
필요한 최소 변경만 해라.

테스트는 최소 다음을 포함해라.

- 패턴 1개
- 여러 패턴
- 한글 패턴
- 한 메시지에 여러 match
- 서로 겹치는 패턴
- 접두사 관계 패턴
- match 없음
- 빈 메시지
- 빈 keyword 목록
- 같은 keyword 중복 입력
- 긴 문자열
- 동일 matcher 재사용

작업 완료 후 다음 형식으로 보고해라.

1. 수정/생성 파일
2. 구현 구조
3. Aho-Corasick 동작 원리
4. build 단계 복잡도
5. search 단계 복잡도
6. 테스트 결과
7. 아직 해결하지 않은 문제
8. Phase 2에서 해야 할 일

작업 범위를 Phase 1 밖으로 확장하지 마라.

---
