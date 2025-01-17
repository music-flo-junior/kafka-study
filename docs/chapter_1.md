# 챕터 1. 카프카 훑어보기

## 메시지 발행과 구독하기

- 메시지 발행/구독 시스템
    - 발행자: 어떤 형태로든 메시지를 구분해서 발행/구독 시스템에 전송
    - 구독자: 특정 부류의 메시지를 구독하게 한다.
    - 브로커: 발행된 메시지를 저장하고 중계

## 초기의 발행/구독 시스템

- 대부분의 발행/구독 시스템은 간단한 메시지 큐나 프로세스 간 통신 채널을 갖는 형태로 시작

![image.png](https://github.com/user-attachments/assets/3df6ca23-a9cf-4d99-b1c0-c695304f6a0c)

예) 메트릭 모니터링:  대시보드 화면에 매트릭을 직접 전송하는 애플리케이션 서비스

→ 만약 메트릭을 받아 분석하는 서버를 추가한다면? UI서버 및 분석서버 모두에게 메트릭을 전달해야함.

![image.png](https://github.com/user-attachments/assets/81cf88c2-8c84-4a79-b061-e44ab29c6e51)

나중에는 이렇게 복잡한 구조가 되곤 한다.

![image.png](https://github.com/user-attachments/assets/5c1a46db-5590-4df3-851b-7774009b27ba)

메트릭 발행/구독 시스템이 있으면 이렇게 단순한 구조로 만들 수 있음

## 개별적인 메시지 큐 시스템

로그메시지로 웹 사이트에 접속하는 사용자의 활동을 추가하고 싶다면?
이런 활동들을 통해 머신러닝 개발자에게 제공하거나 관리자용 보고서로 생성한다면?

![image.png](https://github.com/user-attachments/assets/3a7cd02b-8273-4555-92cb-5fca28294b53)

오른쪽과 같은 서버가 추가됨. 많은 기능이 중복되고 다수의 메시지 처리 시스템을 관리해야 함.

또 다른 종류의 메시지가 추가되면 새로운 시스템을 추가해야한다.

→ 이런 문제를 해결하기 위한 것이 ‘카프카’이다.

## 카프카 살펴보기

- 카프카
    - ‘분산 커밋로그(distributed commit log)’
      ‘분산 스트리밍 플랫폼(distributed streaming platform)’
    - 카프카의 데이터도 지속해서 저장하고 읽을 수 있으며, 시스템 장애에 대비하고 확장에 따른 성능 저하를 방지하기 위해 분산처리한다.

## 메시지와 배치

- 카프카의 메시지 데이터는 토픽(topic)으로 분류된 파티션(partition)에 수록된다.
- 데이터를 수록할 파티션을 결정하기 위해 일관된 해시 값으로 키를 생성한다.
- 같은 키를 갖는 메시지는 항상 같은 파티션에 수록된다.
- 카프카는 효율성을 위해 여러 메시지를 모아 배치(batch) 형태로 파티션에 수록한다.
  → 이 경우 대기시간(latency)와 처리량(throughput)간의 트레이드오프

## 스키마

- 스키마(schema): 메시지의 구조
    - 여러 표준 형식 사용 가능 (JSON, XML 등)
    - 아파치 Avro 선호 (하둡을 위해 개발된 직렬화 프레임워크)
- 일관된 데이터 형식 중요
    - 메시지 쓰기 / 읽기 작업 분리
    - 만약 읽기/쓰기가 합쳐져 있다면? 구/신버전 데이터 형식 병행처리를 위해 구독(읽기) 어플리케이션 먼저 업데이트 → 쓰기 어플리케이션 업데이트
      ⇒ 카프카에서는 잘 정의된 스키마를 공유 레포지토리(repository)에 저장하여 사용할 수 있으므로 애플리케이션 변경 없이 메시지를 처리할 수 있다. ⇒ how???

## 토픽과 파티션

- 토픽: 여러개의 파티션으로 구성
- 파티션
    - 커밋로그의 관점에서 하나의 ‘로그’에 해당
    - 메시지는 파티션에 추가되는 형태로만 수록, 맨 앞부터 순서대로 읽힌다.
    - 메시지 처리 순서는 토픽이 아닌 파티션으로 유지 관리
    - 추가되는 메시지는 각 파티션의 끝에 수록
    - 각 파티션은 서로 다른 서버에 분산될 수 있다. (하나의 토픽이 여러 서버에 수평적으로 확장될 수 있으므로 성능 UP)

![image.png](https://github.com/user-attachments/assets/bec3d380-88cb-4be8-8c96-ce50a1f5505a)

- 스트림(stream)
    - 스트림은 파티션 개수와 상관 없이 하나의 토픽 데이터로 간주
    - 프로듀서로부터 읽는 컨슈머로 이동되는 연속적인 데이터
    - 카프카 스트림즈(Kafka Streams), 아파치 Samza, Storm과 같은 프레임워크에서 실시간으로 메시지를 처리할 때 주로 사용하는 방법

## 프로듀서와 컨슈머

- 프로듀서
    - 메시지를 생성한다.
    - 메시지는 특정 토픽으로 생성
    - 프로듀서는 메시지가 어떤 파티션에 수록되었는지 관여하지 않음
        - 가끔 프로듀서가 특정 파티션에 메시지를 직접 쓰는 경우가 있음 → 메시지 키와 파티셔너(partitioner) 사용 → 지정된 키를 갖는 메시지가 항상 같은 파티션에 수록
- 컨슈머
    - 메시지를 읽는다.
    - 메시지의 오프셋(offset)을 유지하여 읽는 메시지의 위치를 알 수 있다.
    - 오프셋은 메시지가 생성될 때 카프카가 추가해준다.
    - 파티션에 수록된 각 메시지는 고유한 오프셋을 갖는다.
    - 주키퍼(Zookeeper)나 카프카에서는 각 파티션에서 마지막에 읽은 메시지의 오프셋을 저장하고 있으므로 컨슈머가 메시지 읽기를 중단했다가 다시 시작해도 언제든 그 다음 메시지를 읽을 수 있다.
    - 컨슈머는 컨슈머 그룹의 멤버로 동작한다. 컨슈머 그룹은 한 토픽을 소비하기 위해 같은 그룹의 여러 컨슈머와 함께 동작한다.
    - 한 토픽의 각 파티션은 하나의 컨슈머만 소비할 수 있다.  각 컨슈머가 특정 파티션에 대응되는 것을 파티션 소유권(ownership)이라고 한다.

![image.png](https://github.com/user-attachments/assets/d7147c15-de13-45ee-b32c-a4b7e41fcff8)

- 기타
    - 카프카 커넥트(Kafka Connect) API
    - 카프카 스트림즈 클라이언트 API


## 브로커와 클러스터

- 브로커
    - 하나의 카프카 서버
    - 프로듀서로부터 메시지를 수신하고 오프셋을 지정한 후 메시지를 디스크에 저장한다.
    - 컨슈머의 파티션 읽기 요청에 응답하고 디스크에 수록된 메시지를 전송한다.
    - 하나의 브로커는 초당 수천개의 토픽과 수백만개의 메시지를 처리할 수 있다.
- 클러스터
    - 여러개의 브로커는 하나의 클러스터에 포함된다.
    - 그 중 하나는 자동으로 선정되는 클러스터 컨트롤러 기능을 수행한다.
    - 컨트롤러는 같은 클러스터의 각 브로커에게 담당 파티션을 할당하고 브로커들이 정상적으로 동작하는지 모니터링하는 관리 기능을 맡는다.
- 카프카 클러스터의 파티션 복제
    - 각 파티션은 클러스터의 한 브로커가 소유하며, 그 브로커를 파티션 ‘리더’라고 한다.
    - 같은 파티션이 여러 브로커에 지정될 수도 있는데, 이때는 해당 파티션이 ‘복제’된다.
    - 이 경우, 파티션의 메시지는 중복으로 저장되지만 관련 브로커에 장애가 나면, 다른 브로커가 소유권을 인계받아 그 파티션을 처리할 수 있다. 각 파티션을 사용하는 모든 컨슈머와 프로듀서는 파티션 리더에 연결해야 한다.

![image.png](https://github.com/user-attachments/assets/526de86c-43df-4fab-a441-8e7d853c7d9d)

- 보존(retention)
    - 일정 기간 메시지를 보존 (일정 기간 / 일정 크기만큼 메시지를 보존)

## 다중 클러스터

- 다중 클러스터의 장점
    - 데이터 타입에 따라 구분해서 처리할 수 있다.
    - 보안 요구사항을 분리해서 처리할 수 있다.
    - 재해 복구를 대비한 다중 데이터센터를 유지할 수 있다.
- 미러메이커
    - 하나의 카프카 클러스터에서 소비된 메시지를 다른 클러스터에서도 사용할 수 있도록 생성해준다.
    - 아래 예시에서는 두 개의 로컬 클러스터에서 집중(aggregate) 클러스터로 메시지를 모은 후 다른 데이터센터로 복사한다.

![image.png](https://github.com/user-attachments/assets/c45fcd85-a7f4-40f8-b987-8703a4b17dcc)

## 카프카를 사용하는 이유

### 1. 다중 프로듀서

- 여러 클라이언트가 많은 토픽을 사용하거나 같은 토픽을 사용해도 무리없이 처리할 수 있음.

### 2. 다중 컨슈머

- 많은 컨슈머가 상호 간섭 없이 어떤 메시지 스트림도 읽을 수 있게 지원함.

### 3. 디스크 기반의 보존

- 카프카는 지속해서 메시지를 보존할 수 있다.
- 따라서 컨슈머는 항상 실시간으로 실행되지 않아도 된다. 컨슈머가 메시지를 읽는데 실패해도 데이터가 유실될 위험이 없다. 컨슈머가 다시 실행되면 중단 시점의 메시지부터 처리한다.

### 4. 확장성

- 카프카는 확장성이 좋아서 어떤 크기의 데이터도 쉽게 처리할 수 있다.
- 확장 작업은 시스템 전체의 사용에 영향을 주지 않고 클러스터가 온라인 상태에서도 수행될 수 있다.

### 5. 고성능

- 지금까지의 모든 기능이 합쳐져서 카프카의 고성능 메시지 발행/구독 시스템을 만듦

## 데이터 생태계

![image.png](https://github.com/user-attachments/assets/3f849d8e-e128-4976-9ff3-da9357d0ce82)

- 카프카는 모든 클라이언트에 대해 일관된 인터페이스 제공
- 데이터기반 구조의 다양한 멤버 간에 메시지를 전달
- 비즈니스 용도가 생기거나 없어질 때 관련 컴포넌트만 추가하거나 제거

## 이용 사례

1. 활동 추적
2. 메시지 전송
3. 메트릭과 로깅
4. 커밋로그
5. 스트림 프로세싱

## 카프카의 기원

- 링크드인의 문제점
    - 모니터링 시스템 결함 (ex. 폴링 기반 메트릭 시간 처리간격, 일관성 x 등)
    - 사용자 활동 유형 변경 시 많은 조정작업 필요, 스키마 변경 시 시스템 중단
    - 각 서비스간 호환성 X
- 기존의 ActiveMQ → 브로커를 중단시키는 결함, 시스템 확장 지원 X

## 카프카의 탄생

- 모니터링 추적 시스템 요구사항 충족, 확장 가능한 시스템이 목표
    - 푸시 풀 모델로 메시지 프로듀서와 컨슈머 분리
    - 다수의 컨슈머가 사용할 수 있게 데이터 보존
    - 많은 메시지 처리량에 최적화
    - 데이터 스트림 양이 증가될 때 시스템을 수평 확장

## 오픈소스

- 2010년 카프카가 깃헙에 공개
- 2011년 7월 아파치에서 인큐베이터 프로젝트 지정
- 2012년 10월 인큐베이터 프로젝트 끝