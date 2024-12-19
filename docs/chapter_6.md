# Chapter6 카프카 내부 메커니즘

< 이번 장에서 공부할 내용 >

- 카프카 컨트롤러
- 카프카 복제(replication) 동작 방식
- 카프카가 프로듀서와 컨슈머 요청을 처리하는 방법
- 카프카가 저장을 처리하는 방식 (파일 형식, 인덱스 등)

## 클러스터 멤버십

### Zookeeper

- **Zookeeper**
    - 계층적인 트리 구조로 데이터를 저장하고 사용
    - 데이터 저장 노드: znode
    - znode 앞에 /를 붙이고 디렉터리처럼 경로 path를 사용해서 노드 위치를 식별
    - ex. `/music/event/renew`

```java
./zookeeper-shell.sh {Zookeeper URL}
Connecting to {Zookeeper URL}
Welcome to ZooKeeper!
JLine support is disabled

WATCHER::

WatchedEvent state:SyncConnected type:None path:null

ls /music
[event, lock]
ls /music/event
[renew, update]
```

- **노드 관리**
    - 주키퍼를 사용하는 클라이언트에서 한다
    - 노드 생성/삭제, 노드 존재 여부 확인, 데이터 읽기/쓰기, 특정 노드의 모든 자식 노드 내역 가져오기 등
    - 각 노드에는 상태와 구성 정보 및 위치 정보 등의 데이터만 저장되어 크기가 작다. (1KB 미만)
    - 모든 노드가 메모리에 저장되어 처리되므로 속도가 빠르다

- **임시 노드 (ephemeral)**
    - 생성한 클라이언트가 연결되어 있을 때만 존재
    - 연결이 끊어지면 자동으로 삭제
- **영구 노드 (persistent)**
    - 클라이언트가 삭제하지 않는 한 계속 보존
- **노드의 상태 모니터링 (watch)**
    - 노드 변경(자식 노드 추가, 자신과 자식 노드의 삭제 또는 데이터 변경)시 콜백 호출을 통해 클라이언트에게 알려줌
    - ex. 우리가 사용 중인 AbstractZookeeperEventWatcher 클래스 기능
- 브로커 프로세스는 시작될 때마다 주키퍼의 /brokers/id에 임시노드로 자신의 ID를 등록

## 컨트롤러

### Zookeeper
- 클러스터를 시작하는 첫 번째 브로커가 컨트롤러
- controller 브로커는 어떻게 생성되고 변경되는걸 알까?
    - 모든 브로커는 /controller 노드에 주키퍼의 watch를 생성
    → 이 노드에 변경이 생기는 것을 알 수 있음
    → 컨트롤러 브로커가 중단되거나 주키퍼 연결이 끊어지면 임시 노드인 /controller는 삭제
    → 이 때 다른 브로커들이 watch를 통해 알고 /controller 노드 생성을 시도
- 컨트롤러는 선출될때마다 컨트롤러의 세대번호를 알게 됨. → 변경 전의 컨트롤러와 혼동되는 것을 막을 수 있음
- 컨트롤러는 리더 브로커를 선출할 책임을 가짐

```java
# ./zookeeper-shell.sh localhost:2181
Connecting to localhost:2181
Welcome to ZooKeeper!
JLine support is disabled

WATCHER::

WatchedEvent state:SyncConnected type:None path:null
ls /
[admin, brokers, cluster, config, consumers, controller, controller_epoch, feature, isr_change_notification, latest_producer_id_block, log_dir_event_notification, zookeeper]
get /controller
{"version":2,"brokerid":3,"timestamp":"1733881780507","kraftControllerEpoch":-1}
```

### (참고) Zookeeper vs Kraft

< Zookeeper → Kraft >

주키퍼는 기존 Kafka의 메타데이터 관리 도구로 사용되었으나 여러 문제점을 가지고 있었음.

- 확장성: Kafka 클러스터의 크기와 트래픽이 증가함에 따라 Zookeeper가 병목현상이 되어 카프카 성능에 영향을 미치는 경우가 있음
- 복잡성: Zookeeper는 Kafka 아키텍처에 또 다른 계층을 추가하여 복잡성과 종속성을 추가했음. (Kafka와 별도로 Zookeeper를 설치, 구성, 모니터링 및 문제 해결을 해야 했음 ex. 이번 Amazon 이미지 업그레이드 관련 건 등)
- 일관성: Zookeeper가 모든 요청을 처리하기 위해 사용 가능한 노드의 쿼럼을 필요로 하기 때문에 장애 상황에서 Kafka 가용성에 영향을 미칠 수 있음

![image 4](https://github.com/user-attachments/assets/09e83908-e14a-4823-abb8-d4c2bf4dc5e5)


이런 Zookeeper의 여러 문제를 해결한게 ‘Kraft’임. Kraft의 장점은 아래와 같음

- 단순성: Kafka의 아키텍처 단순화
- 확장성: 메타데이터 저장소 부하를 줄여 확장성 개선
- 가용성: 부분적 오류를 허용하여 가용성 향상, 모든 요청을 처리하는데 컨트롤러 쿼럼만 필요
- 간소화된 배포 및 관리: Zookeeper 클러스터를 실행하고 유지할 필요가 없음
- 보안 강화: SSL/TLS로 클라이언트-서버 통신 암호화 및 인증 지원

트레이드 오프: 호환성 (Zookeeper와 호환되지 않음), 일관성 (특정 경우_컨트롤러가 바뀔 때 오래되거나 일관되지 않은 메타 데이터를 볼 수 있음)

![image 5](https://github.com/user-attachments/assets/88306bb0-2c04-49c0-a49f-1b3b97d9dc66)

Kafka 2.8부터는 Kraft 모드를 활성화 해서 사용할 수 있음. 또한 Kafka 3.5 부터는 Zookeeper 모드는 사용되지 않으며 Kafka 4.0부터는 Zookeeper가 완전히 제거됨.

참고자료
https://romanglushach.medium.com/the-evolution-of-kafka-architecture-from-zookeeper-to-kraft-f42d511ba242

https://devocean.sk.com/blog/techBoardDetail.do?ID=165711&boardType=techBlog

https://kafka.apache.org/documentation/#kraft


### Kraft: 카프카의 새로운 래프드 기반 컨트롤러
- preview 버전: 아파치 카프카 2.8
> dev 기준 현재 우리의 버전은? 
> docker image cp-kafka:7.0.1 -> kafka 3.0.x
> https://docs.confluent.io/platform/7.0/installation/versions-interoperability.html
- 왜 카프카 커뮤니티는 컨트롤러를 교체하기로 결정했을까?
  1. 데이터 불일치
  컨트롤러가 주키퍼에 메타데이터를 쓰는 작업: 동기 / 브로커 메시지를 보내는 작업: 비동기 / 주키퍼 업데이트 받는 과정: 비동기
  -> 브로커, 컨트롤러, 주키퍼 간에 메타데이터 불일치가 있을 수 있으며 잡아내기도 어려움.
  2. 재시작 성능
  컨트롤러가 재시작될 때마다 주키퍼로부터 모든 브로커와 파티션에 대한 메타데이터 갱신처리 후 모든 브로커로 전송 -> 병목
  3. 아키텍처가 복잡함
  메타데이터 소유권 관련 내부 아키텍처가 복잡하다. 어떤 작업은 컨트롤러가 하고 다른 건 브로커가 하고, 나머지는 주키퍼가 직접함.
  4. 운영 어려움
  주키퍼는 그 자체로 분산시스템이며 어느정도 기반지식이 있어야 한다. 이런 점에 러닝커브와 운영의 어려움이 있을 수 있다.
  
- Kraft 핵심 아이디어: 사용자가 상태를 이벤트 스트림으로 나타낼 수 있도록 하는 로그 기반 아키텍처를 도입
- Kraft에서는 컨트롤러 노드들은 메타데이터 이벤트 로그를 관리하는 래프트 쿼럼이 된다. 이 로그는 클러스터 메타데이터의 변경 내역을 저장
- 래프트 알고리즘을 사용하여 컨트롤러 노드들은 자체적으로 리더를 선출할 수 있게 됨
- 메타데이터 로그의 리더 역할을 맡고 있는 컨트롤러는 `액티브 컨트롤러` 라고 부름
- 액티브 컨트롤러가 브로커가 보내온 모든 RPC 호출을 처리하고 팔로워 컨트롤러들은 액티브 컨트롤러에 쓰여진 데이터를 복제
- 브로커 프로세스는 시작 시 주키퍼가 아닌 '컨트롤러 쿼럼'에 등록
- Zookeeper -> Kraft 마이그레이션
  - 브리지 릴리스(Bridge Release) 제공


## 복제

- 복제는 카프카 아키텍처의 핵심
- 각 서버 노드 장애 시 카프카의 가용성과 내구성을 보장하는 방법

**리더 리플리카**

- 각 파티션은 리더로 지정된 하나의 리플리카를 갖음
- 일관성을 위해 모든 프로듀서/컨슈머 요청은 리더를 통해 처리

**팔로어 리플리카**

- 각 파티션의 리더를 제외한 리플리카
- 리더의 메시지를 복제하여 리더의 것과 동일하게 유지
- 특정 파티션의 리더 리플리카가 중단되면 팔로어 중 하나가 새로운 리더로 선출

- 팔로어가 리더의 최신 메시지를 복제하지 못하는 케이스
    - 네트워크 혼잡 등으로 복제가 늦어지거나
    - 브로커가 중단되어 다시 복제를 시작하는 경우 등
- 동기화
    - 레플리카 → 리더 fetch 요청 전송
    - fetch 요청에 원하는 메시지의 오프셋이 포함, 항상 수신된 순서로 처리
    - 팔로워 리플리카가 요청한 마지막 오프셋을 보면 복제가 얼마나 지연되고 있는지 알 수 있음
    - 10초 이상 복제하지 못했다면 동기화 하지 않는(out-sync)로 간주하여 리더가 장애가 났을 때 새로운 리더가 될 수 없음.
    - 최신 메시지를 계속 요청하는 팔로어 리플리카를 동기화 리플리카(in-sync replica)라고 하며 해당 리플리카만이 리더로 선출될 수 있음
    - 지연시간은 `replica.lag.time.max.ms`로 제어 가능
- 선호 리더(preferred leader)
    - 토픽이 생성될 때 각 파티션의 리더였던 리플리카
    - 파티션을 처음 생성할 때 고르게 파티션을 할당받아 리더가 되므로 이것이 선호되는 리더임
    - 출력 내역의 첫 번째 리플리카가 항상 선호 리더임

## 요청 처리

- 브로커가 하는 일은 리더에게 전송되는 요청을 처리하는 것
- 특정 클라이언트로부터 브로커에 전송된 모든 요청은 항상 수신된 순서로 처리
- 모든 요청은 `표준 헤더`를 갖는다
    - 요청 타입ID: produce는 0, consumer의 fetch는 1
    - 요청 버전: 프로토콜 API 버전
    - cID(correlation ID): 요청의 고유 식별번호
    - 클라이언트 ID: 문자열 형식의 값
- 요청 타입마다 서로 다른 구조의 데이터를 같이 전송
- 브로커는 자신이 리스닝하는 각 포트에 대해 acceptor 스레드를 실행
- processor 스레드는 클라이언트 연결로부터 요청을 받고, 그것을 요청 큐에 넣으며, 응답 큐로부터 응답을 가져와서 클라이언트에게 전송
- 입출력 스레드는 각 요청을 가져와서 처리하는 책임

**쓰기 요청 (Produce request)**

- 프로듀서가 전송하며 브로커에세 쓰려는 메시지를 포함

**읽기 요청 (Fetch request)**

- 쓰기 요청과 읽기 요청은 모두 파티션의 리더 레플리카에게 전송
- 리더가 아닌 리플리카에게 전달하면 ‘파티션 리더가 아님’ 에러 응답을 받음

![image](https://github.com/user-attachments/assets/119cae5a-32b6-4d20-9827-2fa111581ed8)


- 메타데이터 요청
    - 메타 데이터 요청에 대한 서버 응답에는 토픽에 존재하는 파티션들, 각 파티션의 리플리카, 어떤 리클리카가 리더인지 등의 정보가 포함
    - 자신의 요청 중 하나에서 ‘파티션 리더가 아님’ 에러를 수신했을 때 해당 요청을 다시 전송하기 전에 메타 데이터를 새로 교체함

![image 1](https://github.com/user-attachments/assets/bcdf69b3-dd1a-4734-af74-58426dd05549)


## 쓰기 요청

- acks 구성 매개변수에는 메시지를 수신해야 하는 브로커의 수를 설정
    - 리더만 메시지를 받으면 됨(acks=1)
    - 모든 동기화 리플리카가 메시지를 받아야 함(acks=all)
    - 브로커의 수신 응답을 기다리지 않음(acks=0)
- 브로커가 파티션의 쓰기요청을 받으면 다음 사항의 검사를 시작
    - 해당 토픽의 쓰기 권한을 가지고 있는가?
    - acks 값이 적합한가? (0, 1, all)
    - acks가 all이라면 메시지를 안전하게 쓰는데 충분한 동기화 리플리카가 있는가?
- acks가 all이라면 팔로워 리클리카가 메시지를 복제했는지 리더가 확인할 때까지 퍼거토리(purgatory)라고 하는 버퍼에 해당 요청을 저장한다.

## 읽기 요청

- 클라이언트는 읽기를 원하는 토픽과 파티션 및 오프셋에 있는 메시지들의 읽기 요청을 브로커에게 전송
    - ex. mx토픽의 0파티션의 32오프셋부터 시작하는 메시지와 master3 토픽의 3파티션의 35오프셋부터 시작하는 메시지 전송해주세요
- 클라이언트는 각 파티션마다 브로커가 반환할 수 있는 데이터의 크기를 제한할 수 있음
    - 크기를 제한하지 않으면 클라이언트의 메모리 부족을 초래할 만큼 큰 응답을 전송할 수 있음
- 너무 오래 되어서 파티션에서 이미 삭제된 메시지나 아직 존재하지 않는 오프셋을 클라이언트가 요청하면 브로커는 에러를 응답
- 오프셋이 존재하면 지정한 제한 크기까지의 메시지들을 해당 파티션에서 읽은 뒤 클라이언트에 전송
- 카프카는 제로카피 기법 (메시지를 중간 버퍼 메모리에 쓰지 않고 곧바로 네트워크 채널로 전송)으로 메시지를 전송
- 클라이언트는 반환 하한 크기를 설정할 수 있음 (버퍼만큼 채워서 전송, 네트워크 비용과 CPU 사용을 줄일 수 있음) (timeout을 지정해서 해당 시간이 넘으면 바로 보내도록 설정 가능)

![image 2](https://github.com/user-attachments/assets/469df1b6-7538-4297-890d-701c507508f3)


- 클라이언트는 모든 동기화 리플리카에 쓴 메시지들만 읽을 수 있다.
    - 리플리카들에게 아직 복제가 되지 않은 메시지들은 ‘불안전’한 것으로 간주
    - 리더에만 존재하는 메시지를 클라이언트가 읽으면 일관성이 결여될 수 있다.
    - 리플리카가 살아있는 것으로 간주되는 동안(replica.lag.time.max.ms) 새로운 메시지 복제에 소요될 수 있는 제한 시간 설정
    
![image 3](https://github.com/user-attachments/assets/8f94dd13-9b53-49a5-abbb-1cb40bc4a408)

## 기타 요청들

- 클라이언트 API : 자바의 경우 아파치 카프카 프로젝트에서 클라이언트 API를 구현하고 유지 관리
    - C/C++, Python, Go 등
- 특정 파티션들이 새로운 리더를 갖는다는 것을 컨트롤러가 알릴 때는 새 리더와 팔로워들에게 LeaderAndIsr 요청을 전송
- 컨슈머가 시작될 때는 메시지를 읽을 위치를 알기 위해 주키퍼의 오프셋들을 확인
    - 새로운 카프카 버전에서는 주키퍼를 사용하는 대신 카프카의 특별한 토픽에 오프셋을 저장
    - OffsetCommit, OffsetFetch, ListOffsets 요청이 프로토콜에 추가됨. (주키퍼를 쓰지 않고 클라이언트 API에서 해당 요청을 써도 됨)
- 클라이언트 버전을 업그레이드 하기 전에 브로커 버전을 먼저 업그레이드할 것

## 스토리지

- 파티션 리플리카: 카프카의 기본적인 스토리지 단위
- 하나의 파티션은 여러 브로커 간에 분할될 수 없음
- 하나의 파티션 크기는 단일 마운트 포인트에 사용 가능한 공간으로 제한
- 파티션이 저장될 디렉터리 내역을 log.dirs 매개변수에 지정
- 카프카가 디렉터리를 사용해서 데이터를 저장하는 방법
    - 브로커가 파일들을 관리하는 방법(보존이 처리되는 방법)
    - 로그 압축(Log Compaction) 수행 방법

### 파티션 할당

- 토픽을 생성할 때 카프카는 제일 먼저 여러 브로커 간에 파티션을 할당하는 방법 결정
    - ex. 6개의 브로커 (0~5), 토픽에 10개의 파티션(0~9), 복제 팩터 3

![image 6](https://github.com/user-attachments/assets/fe5f0d9f-c3a1-4bbd-9b8e-925edc71205b)


- 파티션 리플리카는 브로커 간에 고르게 분산 (브로커당 5개의 리플리카를 할당)
- 각 파티션의 리플리카는 서로 다른 브로커에 할당
- 브로커가 랙(rack) 정보를 가지고 있다면 각 파티션의 리플리카는 서로 다른 랙에 있는 것으로 지정

![image 7](https://github.com/user-attachments/assets/052ca25a-dad0-4c29-8f1a-e5f7b4f2744a)


- 라운드로빈 방식
    - 임의의 브로커 4부터 시작한다고 했을 때 라운드로빈 방식으로 파티션 할당
        - 브로커 4: P0의 리더 파티션, 브로커 5: P1의 리더 파티션, 브로커0: P2의 리더 파티션…
        - 브로커 5: P0의 팔로워 파티션, 브로커 6: P0의 팔로워 파티션…
- 랙 인식 방법
    - 서로 다른 랙의 브로커가 번갈아 선택되도록 순서를 정함
    - ex. 브로커 0, 1, 2는 같은 렉 / 브로커 3, 4, 5는 다른 랙
        - 0~5까지 브로커를 선택하는게 아니라 0, 3, 1, 4, 2, 5 순서로 사용
    - 만에 하나 랙에 장애가 생겨도 가용성 보장
    
![image 8](https://github.com/user-attachments/assets/b4d7a5c2-e5e7-410a-960d-3323870bf1f1)

 
- 디렉터리 결정
    - 각 디렉터리의 파티션 개수를 계산하고 가장 적은 수의 파티션을 갖는 디렉터리에 새 파티션을 추가
    
    디스크 공간에 유의하자
    
    - 브로커에 파티션을 할당할 때는 사용 가능한 디스크 공간이나 기존의 사용량이 고려되지 않음
    - 파티션을 디스크에 할당할 때 파티션 크기가 아닌 개수가 고려 대상이 됨

## 파일 관리

- 보존(retention)
    - 메시지 삭제 전에 모존하는 시간
    - 오래된 메시지 전에 보존할 데이터의 크기
- 파일을 삭제하는 방식
    - 각 파티션을 세그먼트로 나눈다. (로그 세그먼트)
    - 기본적으로 각 세그먼트는 최대 1G 데이터 또는 1주일 간 데이터 보존한 양이다.
    - 브로커가 파티션에 데이터를 쓸 때 세그먼트의 제한 크기나 보존 기간에 도달하면 해당 파일을 닫고 새로운 세그먼트 파일에 계속 쓴다
- 액티브 세그먼트
    - 메시지를 쓰기 위해 사용 중인 세그먼트
    - 액티브 세그먼트는 삭제하지 않는다.
        - ex. 로그보존 기간 1일 / 세그먼트 5일 보존 → 데이터는 5일간 보존
        - ex. 1주 데이터 보존 / 매일 1개의 세그먼트 생성 → 파티션은 7개의 세그먼트

## 파일 형식

- 각 세그먼트는 하나의 데이터 파일로 생성
- 카프카 메시지와 오프셋이 저장
- 디스크에 수록되는 데이터 형식은 메시지의 형식과 동일
    
    → 제로카피 기법으로 컨슈머에게 메시지를 전송할 때 별도 버퍼 없이 디스크에서 바로 네트워크 전송
    
    → 프로듀서가 이미 압축해서 전송한 메시지의 압축 해지와 재압축하지 않음
    
- 메시지에 포함된 내용
    - 체크섬(checksum) 코드, 메시지 형식의 버전, 압축 코덱, 타임 스탬프 등

![image 9](https://github.com/user-attachments/assets/ab1bdc0c-002a-4df1-b917-683611888031)


```java
# bin/kafka-run-class.sh kafka.tools.DumpLogSegments
```

→ 파일 시스템에서 파티션 세그먼트와 내용을 살펴볼 수 있음. 각 메시지의 오프셋, 체크섬, 매직 바이트, 크기, 압축 코덱도 볼 수 있다.

~~→ 그러나 테스트 해봤을 때 안됨 (버전 문젠가)~~

## 인덱스

- 카프카는 각 파티션의 인덱스를 유지 관리하며 인덱스는 각 세그먼트 파일과 이 파일의 내부 위치로 오프셋을 연관시킨다
- 인덱스도 세그먼트로 분할된다. 따라서 메시지가 삭제되면 그것과 연관된 인덱스 항목도 삭제할 수 있다.
- 인덱스가 손상되면 연관된 로그 세그먼트로부터 메시지들을 다시 읽고 오프셋과 위치를 수록하여 다시 생성한다.

```java
[appuser@kafka-1 /]$ cd /var/lib/kafka
[appuser@kafka-1 kafka]$ ls
data
[appuser@kafka-1 kafka]$ cd data
[appuser@kafka-1 data]$ ls
__consumer_offsets-0   __consumer_offsets-2   __consumer_offsets-30  __consumer_offsets-41  __consumer_offsets-8
__consumer_offsets-1   __consumer_offsets-20  __consumer_offsets-31  __consumer_offsets-42  __consumer_offsets-9
__consumer_offsets-10  __consumer_offsets-21  __consumer_offsets-32  __consumer_offsets-43  artist.detail.album-0
__consumer_offsets-11  __consumer_offsets-22  __consumer_offsets-33  __consumer_offsets-44  artist.detail.album-1
__consumer_offsets-12  __consumer_offsets-23  __consumer_offsets-34  __consumer_offsets-45  cleaner-offset-checkpoint
__consumer_offsets-13  __consumer_offsets-24  __consumer_offsets-35  __consumer_offsets-46  ha.mcp.meta-0
__consumer_offsets-14  __consumer_offsets-25  __consumer_offsets-36  __consumer_offsets-47  ha.mcp.meta-1
__consumer_offsets-15  __consumer_offsets-26  __consumer_offsets-37  __consumer_offsets-48  ha.mcp.meta-2
__consumer_offsets-16  __consumer_offsets-27  __consumer_offsets-38  __consumer_offsets-49  log-start-offset-checkpoint
__consumer_offsets-17  __consumer_offsets-28  __consumer_offsets-39  __consumer_offsets-5   meta.properties
__consumer_offsets-18  __consumer_offsets-29  __consumer_offsets-4   __consumer_offsets-6   recovery-point-offset-checkpoint
__consumer_offsets-19  __consumer_offsets-3   __consumer_offsets-40  __consumer_offsets-7   replication-offset-checkpoint
[appuser@kafka-1 data]$ vi ha.mcp.meta-0/
00000000000000000001.log        00000000000000000001.timeindex  partition.metadata
00000000000000000001.snapshot   leader-epoch-checkpoint
```

## 압축

- 대개 카프카는 설정된 시간동안 메시지를 저장하며 보존 기간이 지난 메시지는 삭제
- 삭제 보존 정책
    - 보존 기간 이전의 메시지들을 삭제
- 압축 보존 정책
    - 각 키의 가장 최근 값만 토픽에 저장
    - 토픽에 null 키가 포함되어 있으면 압축이 안됨

## 압축 처리 방법

**클린**

- 이전에 압축되었던 메시지들
- 각 키에 대해 하나의 값만 포함, 이전에 압축할 당시 가장 최근 값

**더티**

- 직전 압축 이후에 추가로 쓴 메시지들이 저장된 부분

![image 10](https://github.com/user-attachments/assets/341234cc-377d-4214-959e-4ac12283ce66)


- 압축하는 과정
    1. log.cleaner.enabled 설정 시 압축 활성화
    2. 하나의 압축 매니저 스레드와 여러개의 압축 스레드 시작
    3. 각 스레드는 전체 파티션 크기보다 더티 메시지 비율이 가장 큰 파티션을 선택하고 압축
    4. 파티션을 압축하기 위해, 압축 스레드는 메모리에 압축용 오프셋 Map을 생성

![image 11](https://github.com/user-attachments/assets/5150899e-6a4f-4b5e-9010-1937729d0f36)


## 삭제된 메시지

- 특정 키를 갖는 모든 메시지를 삭제하고자 한다면?
    - ex. 특정 사용자가 더 이상 서비스를 받지 않아 법적으로 그 사용자의 모든 기록을 시스템에서 삭제해야할 경우
    
    → 해당 키와 null 값을 포함하는 메시지를 카프카에 쓴다.
    
    → 압축 스레드에서는 그런 메시지를 발견할 때 압축을 수행한 후 해당 키에 대해 null 값을 갖는 메시지만 남긴다.
    
    → 이런 메시지를 ‘**톰스톤 메시지**’라고 하는데, 만약 RDB에 저장한 사용자의 데이터가 있다면 컨슈머는 null인 값을 보고 삭제 처리하면 된다.
    

## 토픽은 언제 압축될까?

- 현재 사용 중인 액티브 세그먼트를 삭제하지 않는 삭제 보존정책과 마찬가지로 압축도 하지 않음.
- 카프카 0.10.0 이하 버전에서는 토픽의 50%가 더티 레코드를 포함할 때 압축 시작
