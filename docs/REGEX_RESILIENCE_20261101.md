# 긴 입력 회귀 수정과 배포 JAR 검증

## 문제와 변경

짧은 주소에서 통과하던 이메일·URL 정규식이 `a.`를 10,000회 반복한 입력에서는 Java 정규식 엔진의 반복 그룹 역추적으로 `StackOverflowError`를 발생시켰다. 새 회귀 테스트를 기존 구현에 먼저 적용했을 때 5개 중 4개가 실패했다.

`EmailRule`은 로컬 부분과 도메인 반복에 possessive quantifier를 사용한다. `UrlRule`은 마지막으로 성립하는 suffix를 lookahead로 확인하면서 호스트 라벨을 소비한다. 무조건 possessive로 바꾸면 `example.co.kr`의 탐지 범위가 달라지므로, 지원 TLD 및 원문 매치 위치를 함께 비교했다. 이 판단은 Kiro CLI의 실제 `claude-opus-5.5`와 토론 후 구현했다.

## 수정 파일

- `src/main/java/dev/chatmoderation/core/rule/{EmailRule,UrlRule}.java`: 반복 그룹 수정.
- `src/test/java/dev/chatmoderation/core/rule/LongInputRuleTest.java`: 긴 이메일·도메인·경로·near miss, Unicode 앞부분과 마스킹 위치, 기존 정규식과의 차등 비교.
- `verification/PackagedConsumer.java`, `build.gradle`: production JAR만으로 외부 소비자를 컴파일·실행. 기본 리소스, 공개 API, 가짜 semantic provider 및 클래스 로딩 출처를 검증한다.
- `.github/workflows/ci.yml`: Java 21 `clean check`, 보고서 artifact. 키 없는 테스트이며 live 평가 task는 실행하지 않는다.

## 검증

2026-11-01, Java 21 / Gradle 8.10.2:

```bash
./gradlew clean check --no-daemon --console=plain
```

- JUnit **458 tests, 0 failures, 0 errors, 0 skipped**.
- 짧은 정상·비정상 입력의 기존 탐지 범위와 일치: 고정 사례 25개 및 고정 seed 5,000개 × 변형 3개.
- 10,000개 라벨의 주소와 긴 near miss에서 StackOverflow 회귀 재현이 사라졌다.
- 별도 소비자 실행 성공. `src/main`이나 test runtime classpath 없이 배포 JAR와 소비자 클래스만 사용한다. 실제 OpenAI provider의 전이 의존성/네트워크 연동까지 검증한 것은 아니다.
- 전체 검증에는 기존 `verifyProductionJar`의 테스트 자산 유출 검사도 포함한다.

## 복잡도·한계·다음 단계

이번 수정은 반복 그룹의 재귀적 역추적에 의한 스택 고갈을 방지하는 범위다. Java 정규식의 lookahead와 검색 시작점 재시도가 남으므로 **모든 입력에 대한 O(n) 시간, 상수 메모리, 처리량 개선을 증명하지 않았다**. 결과 목록·원문·마스킹 문자열은 입력과 매치 수에 비례하는 공간을 사용한다. 호스트/TLD 지원 범위는 기존 정책을 유지하며 RFC 전체 주소 검증기가 아니다. 고정·무작위 사례는 전체 입력 언어의 동치 증명이 아니다.

다음 단계는 호출 애플리케이션의 메시지 길이 한도와 동시성 정책을 별도로 결정하고 실제 운영 분포에서 지연·오탐을 측정하는 것이다. 공개 API 변경이나 웹 프레임워크 결합은 이번 변경에 포함하지 않는다.
