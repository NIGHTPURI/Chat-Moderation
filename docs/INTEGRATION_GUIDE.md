# Integration Guide

아직 구현 전 참고 문서다.

## 목표 사용 형태

최종적으로 외부 코드는 아래 정도만 알아야 한다.

```java
ModerationResult result = moderationService.moderate(message);

if (!result.allowed()) {
    return;
}

broadcast(result.outputMessage());
```

## Spring WebSocket 예상 적용 지점

```text
Client
  ↓
WebSocket message receive
  ↓
Moderation
  ↓
ALLOW / MASK / BLOCK
  ↓
broadcast
```

중요:
broadcast 이후 moderation을 수행하지 않는다.

## 향후 친구 프로젝트에 전달할 때

가능한 선택지:

1. moderation 패키지 소스 복사
2. 별도 Gradle module로 포함
3. JAR로 publish하여 dependency 추가

MVP에서는 1 또는 2가 가장 단순하다.

## 현재 단계에서 하지 않을 것

- AWS 인프라 변경
- 새 EC2 생성
- 별도 FastAPI 서버 배포
- Spring WebSocket 직접 수정
