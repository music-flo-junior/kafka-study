# 챕터 12. 카프카 모니터링

> 카프카 클러스터를 운영하기 위한 유용한 CLI 유틸리티를 제공.
자바 클래스로 구현되어 있으며, 편리하게 호출할 수 있게 스크립트가 함께 제공됨
> 

> 기본적으로는 인증 과정 없이 CLI 툴을 이용 가능하므로 인가되지 않은 변경을 방지하려면 한정된 운영자가 툴에 접근할 수 있도록 유념할 것.
> 

## 12.1 토픽 작업

- [`kafka-topics.sh`](http://kafka-topics.sh) 툴
    - 토픽의 생성, 변경, 삭제, 정보 조회 가능
    - `--bootstrap-server` 옵션에 연결 문자열과 포트 입력 (ex. localhost:9092)

<aside>
💡

버전 확인
클러스터의 브로커와 같은 버전의 명령행 툴을 사용하자

(가장 좋은 것은 카프카 브로커에 설치되어 있는 툴을 사용하는 것이다.)

</aside>

### 12.1.1 새 토픽 생성하기 (`--create`)

**필수 인자 값**

- `--topic`
    - 생성하려는 토픽의 이름
- `--replication-factor`
    - 클러스터 안에 유지되어야 할 레플리카의 개수
- `--partitions`
    - 토픽에서 생성할 파티션의 개수

<aside>
💡

**토픽 이름 짓기**
토픽 이름에는 영문 혹은 숫자, ‘_’, ‘-’, ‘.’을 사용 가능하지만 토픽 이름에 ‘.’을 사용하는건 권장되지 않는다. (카프카 내부적으로 .을 ‘_’로 변환해서 처리하며 토픽 이름이 충돌할 수 있음)
토픽 이름을 ‘_’*로 시작하는 것 역시 카프카 내부에서 토픽을 생성할 때 ‘*__’로 시작하는 것이 관례라 권장하지 않는다.

</aside>

```bash
$ bin/kafka-topic.sh --bootstrap-server <connection-string>:<port> --create --topic <string>
 --replication-factor <integer> --partitions <integer>
```

- 각 파티션에 대해 클러스터는 지정된 수만큼 레플리카 선정. (랙 인식 레플리카 할당 설정이 되어있다면 서로 다른 랙에 레플리카를 위치시킨다. 만약 랙 인식 할당 기능이 필요 없다면 `--disable-rack-aware` 인수를 지정한다.

ex.

```java
$ ./kafka-topics.sh localhost:9092 --create --topic
test-topic --replication-factor 2 --partitions 8
Created topic test-topic
```

<aside>
💡

**‘IF-EXISTS’와 ‘IF-NOT-EXISTS’ 인수 사용하기**

kafka-topics.sh를 자동화된 방식으로 사용할 경우 `--if-not-exists` 인수를 사용하여 같은 이름의 토픽이 이미 있어도 에러를 리턴하지 않도록 하는 것이 좋다.

`—if-exists` 인수를 `--alter` 명령과 사용하면 변경되는 토픽이 존재하지 않을 때 에러를 리턴하지 않으므로 알아차리기 어려워 권장되니 않는다.

</aside>

### 12.1.2 토픽 목록 조회하기 (`—list`)

클러스터 안의 모든 토픽을 보여준다. 이때 출력되는 결과는 한 줄에 하나의 토픽이며 특정한 순서는 없다.

ex.

```bash
$ ./kafka-topics.sh --bootstrap-server localhost:9092 --list
test-topic
```

> 책에서는 내부 토픽인 __consumer_offsets도 보인다고 했으나 docker에서 설치된 kafka의 경우 보이지 않았다. `—exclude-internal`을 함께 실행하면 내부 토픽(`__`)은 제외할 수 있다.
> 

### 12.1.2 토픽 상세 내역 조회하기 (`—discribe`)

```bash
$ ./kafka-topics.sh  kafka-topics --bootstrap-server localhost:9092 --describe --topic test-topic
Topic: test-topic	TopicId: _sr_3p7uTbyHUAnN8aTL9g	PartitionCount: 8	ReplicationFactor: 2	Configs:
	Topic: test-topic	Partition: 0	Leader: 3	Replicas: 3,2	Isr: 3,2
	Topic: test-topic	Partition: 1	Leader: 1	Replicas: 1,3	Isr: 1,3
	Topic: test-topic	Partition: 2	Leader: 2	Replicas: 2,1	Isr: 2,1
	Topic: test-topic	Partition: 3	Leader: 3	Replicas: 3,1	Isr: 3,1
	Topic: test-topic	Partition: 4	Leader: 1	Replicas: 1,2	Isr: 1,2
	Topic: test-topic	Partition: 5	Leader: 2	Replicas: 2,3	Isr: 2,3
	Topic: test-topic	Partition: 6	Leader: 3	Replicas: 3,2	Isr: 3,2
	Topic: test-topic	Partition: 7	Leader: 1	Replicas: 1,3	Isr: 1,3
```

**describe 명령 출력을 필터링 할 수 있는 유용한 옵션 (**클러스터에 발생한 문제를 찾는데 도움이 되는 옵션들)

- `--topics-with-overrides`
    - 설정 중 클러스터 기본 값을 재정의한 것이 있는 토픽을 보여줌
- `--exclude-internal`
    - ‘__’ 내부 토픽 앞에 붙는 토픽들은 결과에서 제외
- `--under-replicated-partitions`
    - 1개 이상의 레플리카가 리더와 동기화 되지 않고 있는 모든 파티션을 보여줌
    - 리밸런싱 과정에서 불완전 복제 파티션이 발생할 수 있으므로 나쁜 것은 아니나 주의할 필요가 있다.
- `--at-min-isr-partitions`
    - 레플리카 수가 인-싱크 레플리카 최소값과 같은 모든 파티션을 보여준다.
    - 이 토픽들은 프로듀서나 컨슈머 클라이언트가 여전히 사용할 수 있지만 중복 저장된 게 없기 때문에 작동 불능에 빠질 위험이 있음
- `--under-min-isr-partitons`
    - ISR 수가 쓰기 작업이 성공하기 위해 필요한 최소 레플리카 수에 미달하는 모든 파티션을 보여준다.
    - 이 파티션들은 읽기 전용 모드라고 볼 수 있고 쓰기 작업은 불가능 하다.
- `--unavailable-partitons`
    - 리더가 없는 모든 파티션 (매우 심각한 상황)
    - 파티션이 오프라인 상태이며 프로듀서나 컨슈머 클라이언트가 사용 불가능 한 상황

### 12.1.4 파티션 추가하기(`—alter`)

- 토픽의 파티션 수를 증가시켜야 하는 경우 (수평 확장)

```bash
$ ./kafka-topics.sh --bootstrap-server localhost:9092 --alter --topic t
est-topic --partitions 16
$ ./kafka-topics.sh --bootstrap-server localhost:9092 --describe --topi
c test-topic
Topic: test-topic	TopicId: _sr_3p7uTbyHUAnN8aTL9g	PartitionCount: 16	ReplicationFactor: 2	Configs:
	Topic: test-topic	Partition: 0	Leader: 3	Replicas: 3,2	Isr: 3,2
	Topic: test-topic	Partition: 1	Leader: 1	Replicas: 1,3	Isr: 1,3
	Topic: test-topic	Partition: 2	Leader: 2	Replicas: 2,1	Isr: 2,1
	Topic: test-topic	Partition: 3	Leader: 3	Replicas: 3,1	Isr: 3,1
	Topic: test-topic	Partition: 4	Leader: 1	Replicas: 1,2	Isr: 1,2
	Topic: test-topic	Partition: 5	Leader: 2	Replicas: 2,3	Isr: 2,3
	Topic: test-topic	Partition: 6	Leader: 3	Replicas: 3,2	Isr: 3,2
	Topic: test-topic	Partition: 7	Leader: 1	Replicas: 1,3	Isr: 1,3
	Topic: test-topic	Partition: 8	Leader: 2	Replicas: 2,3	Isr: 2,3
	Topic: test-topic	Partition: 9	Leader: 3	Replicas: 3,2	Isr: 3,2
	Topic: test-topic	Partition: 10	Leader: 1	Replicas: 1,3	Isr: 1,3
	Topic: test-topic	Partition: 11	Leader: 2	Replicas: 2,1	Isr: 2,1
	Topic: test-topic	Partition: 12	Leader: 3	Replicas: 3,1	Isr: 3,1
	Topic: test-topic	Partition: 13	Leader: 1	Replicas: 1,2	Isr: 1,2
	Topic: test-topic	Partition: 14	Leader: 2	Replicas: 2,3	Isr: 2,3
	Topic: test-topic	Partition: 15	Leader: 3	Replicas: 3,2	Isr: 3,2
```

<aside>
💡

**키가 있는 메시지**

컨슈머 입장에서 키가 있는 메시지를 갖는 토픽에 파티션을 추가하는 것은 매우 어렵다.

파티션의 수가 변하면 키값에 대응하는 파티션도 달라지기 때문이다. 따라서 키가 있는 메시지를 저장하는 토픽을 생성할 때에는 미리 파티션의 개수를 정해놓고 생성한 뒤에 파티션의 수를 바꾸지 않는게 좋다.

</aside>

### 12.1.5 파티션 개수 줄이기

- 토픽의 파티션 개수는 줄일 수 없다.
- 토픽에서 파티션을 삭제한 다는 것은 토픽에 저장된 데이터의 일부를 삭제한다는 것이기 때문에 남은 파티션에 다시 분배하는 것도 어렵고 메시지의 순서도 바뀔 수 있다.
- 만약 파티션의 수를 줄여야 한다면 토픽을 삭제하고 다시 만들거나 새로운 버전의 토픽을 생성해서 모든 쓰기 트래픽을 새 토픽에 몰아주는 것을 권장한다.

### 12.1.6 토픽 삭제하기

- 토픽이 필요하지 않다면 삭제할 수 있다. 다만 `delete.topic.enable` 옵션이 true로 설정되어 있어야 한다.
- 토픽 삭제는 비동기적인 작업이므로 명령을 실행했을 때 토픽이 삭제될 것이라고만 표시될 뿐 삭제가 즉각 일어나진 않는다.
- 컨트롤러가 현재 돌고 있는 작업이 완료되는 대로 브로커에 삭제 작업을 통지하면, 브로커는 메타데이터를 무효화하고 관련 데이터를 디스크에서 지운다.
- 토픽을 지울 때에는 2개 이상의 토픽을 동시에 삭제하지 말고, 삭제 작업 사이에 충분한 시간을 두는 것이 좋다.

`delete.topic.enable`설정 값이 falsedls ruddn 

```bash
$ ./kafka-topics.sh --bootstrap-server localhost:9092 --delete --topic test-topic
$ ./kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic test-topic
Error while executing topic command : Topic 'test-topic' does not exist as expected
[2025-01-13 23:37:10,421] ERROR java.lang.IllegalArgumentException: Topic 'test-topic' does not exist as expected
	at kafka.admin.TopicCommand$.kafka$admin$TopicCommand$$ensureTopicExists(TopicCommand.scala:542)
	at kafka.admin.TopicCommand$AdminClientTopicService.describeTopic(TopicCommand.scala:317)
	at kafka.admin.TopicCommand$.main(TopicCommand.scala:69)
	at kafka.admin.TopicCommand.main(TopicCommand.scala)
 (kafka.admin.TopicCommand$)
```

## 12.2 컨슈머 그룹

- 컨슈머 그룹이란?
    - 서로 협업해서 여러개의 토픽 혹은 하나의 토픽에 속한 여러 파티션에서 데이터를 읽어오는 컨슈머 집단
    - [`kafka-consuer-groups.sh`](http://kafka-consuer-groups.sh) 툴로 컨슈머 그룹을 관리하고 그룹의 상세 내역을 보거나 삭제, 오프셋 정보를 초기화하는데 사용할 수 있다.

### 12.2.1 컨슈머 그룹 목록 및 상세 내역 조회 (`—list`, `--describe`)

```bash
$ ./kafka-consumer-groups.sh --bootstrap-server localhost:9092 --list
my-consumer
```

목록에 포함된 모든 그룹에 대해 —list 매개변수를 —discribe로 바꾸고 —group을 추가하면 상세한 정보를 조회할 수도 있다.

```bash
$ ./kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
--describe --group my-consumer
GROUP TOPIC PARTITION CURRENT-OFFSET LOG-END-OFFSET LAG CONSUMER-ID HOST CLIENT-ID
```

- GROUP : 컨슈머 그룹의 이름
- TOPIC: 읽고 있는 토픽의 이름
- PARTITION 읽고 있는 파티션의 ID
- CURRENT-OFFSET: 컨슈머 그룹이 다음번에 읽어올 메시지의 오프셋 (컨슈머 위치)
- LOG-END-OFFSET: 이 파티션에 쓰여질 다음 번 메시지의 오프셋
- LAG: 컨슈머의 CURRENT-OFFSET과 LOG-END-OFFSET 간의 차이
- CONSUMER-ID: 설정된 client-id 값을 기준으로 생성된 고유한 consumer-id
- HOST: 컨슈머 그룹이 읽고 있는 호스트의 IP 주소
- 컨슈머 그룹에서 속한 클라이언트를 식별하기 위해 클라이언트에 설정된 문자열

### 12.2.2 컨슈머 그룹 삭제하기 (`—delete`)

- 그룹이 읽고 있는 모든 토픽에 대해 저장된 오프셋을 포함한 전체 그룹 삭제
- 컨슈머 그룹 내의 모든 컨슈머들이 내려간 상태이며 컨슈머 그룹에 활동중인 멤버가 하나도 없어야 함.

```bash
$ ./kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
--delete --group my-consumer
```

### 12.2.3 오프셋 관리

- 컨슈머 그룹에 대한 오프셋을 조회하거나 삭제하는 것 이외에도 저장된 오프셋을 가져오거나 새로운 오프셋을 저장하는 것도 가능함.
- 뭔가 문제가 있어서 메시지를 다시 읽어와야 하거나 뭔가 문제가 있는 메시지를 건너뛰기 위해 컨슈머의 오프셋을 리셋하는 경우 유용하다.

1. 오프셋 내보내기
    1. 컨슈머 그룹을 csv 파일로 내려보내기
        1. `--dry-run` 옵션과 `--reset-offsets` 매개변수를 사용
        2. csv 파일 형식: `[토픽 이름],[파티션번호],[오프셋]`
        
        ```bash
        $ ./kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
        --export --group my-consumer --topic my-topic \
        --reset-offsets --to-current --dry-run > offsets.csv
        
        $ cat offsets.csv
        my-topic,0,8905
        my-topic,1,8915
        ...
        ```
        
2. 오프셋 가져오기
    1. 내보내기 작업에서 생성된 파일을 가져와서 컨슈머 그룹의 현재 오프셋을 설정
    2. 대체로 현재 컨슈머 그룹의 오프셋을 내보낸 뒤, 백업을 위해 복사본을 만들어두고 오프셋을 원하는 값으로 바꿔서 사용하는 식으로 운용

<aside>
💡

**오프셋을 가져오기 전에 컨슈머를 먼저 중단시키자**

오프셋 가져오기를 하기 전에 컨슈머 그룹에 속한 모든 컨슈머를 중단시키는 것이 중요

컨슈머 그룹이 돌아가고 있는 상태에서 새 오프셋을 넣어준다고 해서 컨슈머가 새 오프셋 값을 읽어오지 않고 새 오프셋들을 덮어써버린다.

</aside>

```bash
$ ./kafka-consumer-groups.sh --bootstrap-server \
--reset-offsets --group my-consumer \
--from-file offsets.csv --execute
TOPIC PARTITION NEW-OFFSET
my-topic 0 8905
my-topic 1 8915
```

## 12.3 동적 설정 변경

토픽, 클라이언트, 브로커 등 많은 설정이 클러스터를 끄거나 재설치할 필요 없이 돌아가는 와중에 동적으로 바꿀 수있다.

이런 설정들을 수정할 때에는 주로 [`kafka-config.sh`](http://kafka-config.sh) 가 주로 사용된다.

### 12.3.1 토픽 설정 기본값 재정의하기

```jsx
$ bin/kafka-configs.sh --bootstrap-server localhost:9092 \
--alter --entity-type topics --entity-name {topic-name} \
--add-config {key}={value}[,{key}={value}...]
```

ex. test-topic 보존 기한을 1시간으로 설정한다.

```jsx
$ bin/kafka-configs.sh --bootstrap-server localhost:9092 \
--alter --entity-type topics --entity-name test-topic \
--add-config retention.ms=3600000
Completed updating config for topic test-topic.
```

**동적으로 설정 가능한 토픽 키**

→ 책 및 아파치 document 참고

### 12.3.2 클라이언트와 사용자 설정 기본값 재정의하기

- 카프카 클라이언트와 사용자의 경우, 재정의 가능한 설정은 쿼터에 관련된 것 몇 개밖에 없다.

ex. 사용자별, 클라이언트별 컨트롤러의 변경률 설정을 한번에 변경하기

```jsx
$bin/kafka-configs.sh --bootstrap-server localhost:9092 \
--alter --add-config "controller_mutations_rate=10"
--entity-type clients --entity-name {Client ID}
--entity-type users --entity-name {User ID}
```

### 12.3.3 브로커 설정 기본 값 재정의하기

브로커에 대해 재정의 가능한 항목은 80개가 넘는다. 전체 옵션을 보고 싶다면 아래 링크를 참고한다.

https://kafka.apache.org/documentation/#brokerconfigs

그 중 중요한 설정 몇가지만 살펴보자.

- min.insync.replicas
    - 프로듀서의 acks 설정 값이 all 로 잡혀있을 때 쓰기 요청에 응답이 가기 전에 쓰기가 이루어져야 하는 레플리카 수의 최소값을 결정한다.
- unclean.leader.election.enable
    - 리더로 선출되었을 경우 데이터 유실이 발생하는 레플리카를 리더로 선출할 수 있게 한다. 약간의 데이터 유실이 허용되는 경우 혹은 데이터 유실을 피할 수 없어서 카프카 클러스터의 설정을 잠깐 풀어주거나 해야 할 때 유용하다.
- max.connections
    - 브로커에 연결할 수 있는 최대 연결 수. 좀 더 정밀한 스로틀링을 바란다면 max.connections.per.ip, max.connections.per.ip.overrides를 사용할 수 있다.

### 12.3.4 재정의된 설정 상세 조회하기

- kafka-configs.sh를 사용하면 모든 재정의된 설정 목록을 조회할 수 있다.

ex.

```jsx
$ bin/kafka-configs.sh --bootstrap-server localhost:9092 \
--describe --entity-type topics --entity-name test-topic
Dynamic configs for topic test-topic are:
  retention.ms=3600000 sensitive=false synonyms={DYNAMIC_TOPIC_CONFIG:retention.ms=3600000}
```

### 12.3.5 재정의된 설정 삭제하기

동적으로 재정의한 설정은 통째로 삭제할 수 있다. 삭제하려면 `--delete-config` 매개변수와 함께 `--alter` 명령을 사용한다.

ex.

```jsx
$ bin/kafka-configs.sh --bootstrap-server localhost:9092 \
--alter --entity-type topics --entity-name test-topic --delete-config retention.ms
Completed updating config for topic test-topic.
```

## 12.4 쓰기 작업과 읽기 작업

카프카를 사용할 때 애플리케이션이 제대로 돌아가는지 확인하기 위해 수동으로 메시지를 쓰거나 샘플로 메시지를 읽어야 하는 경우 [`kafka-console-consumer.sh`](http://kafka-console-consumer.sh) 와 [`kafka-console-producer.sh`](http://kafka-console-producer.sh) 가 제공된다.

### 12.4.1 콘솔 프로듀서(`kafka-console-producer.sh`)

- 카프카 토픽에 메시지를 써넣을 수 있다.
- 메시지는 줄 단위로, 키와 밸류 값은 탭 문자를 기준으로 구분된다.
- 쓰기 작업이 끝났다면 end-of-file(EOF) 문자를 입력해서 클라이언트를 종료시킨다. 터미널에서는 Ctrl + D로 가능하다.

```jsx
$ bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic test-topic
>Message 1
>Test Message 2
>Test Message 3
>Hello My Name is Nova
```

1. 프로듀서 설정 옵션 사용하기
    1. `--producer.config {설정파일}` 지정하기
    2. 명령줄에서 1개 이상의 `--producer-property {key}={value}` 인수 지정하기
        
        ex. `--batch-size`, `--timeout`, `--compression-codec`, `--sync`
        
2. 읽기 옵션
    1. 콘솔 프로듀서에 `--property` 명령줄 옵션을 사용해서 지정 가능
    
    ex. `ignore.error`, `parse.key`, `key.separator`
    

### 12.4.2 콘솔 컨슈머 (`kafka-console-consumer.sh`)

- 카프카 토픽에서 메시지를 읽을 수 있다.
- 메시지는 표준 출력에 한 줄씩 출력된다.
- 기본적으로 키나 형식 같은 것 없이 메시지 안에 저장된 로 바이트 뭉치가 출력된다.

**어떤 토픽으로부터 메시지를 읽어올 지 결정하는 옵션 (기본적인 명령줄 옵션)**

1. `--topic`
    1. 읽어올 토픽의 이름을 결정
2. `--whitelist`
    1. 읽어오고자 하는 모든 토픽 이름과 매치되는 정규식 지정

ex.

```jsx
$ bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --whitelist 'test.*' --from-beginning
Message 1
Test Message 2
Test Message 3
Hello My Name is Nova
```

1. 컨슈머 기본 옵션 사용하기
    1. `--consumer.config {설정파일}` or 명령줄에서 1개 이상의 `--consumer-property {key}={value}` 형태 인수를 옵션으로 지정하기
    2. 자주 사용되는 옵션
        1. —formatter {클래스 이름} : 메시지를 바이트 뭉치에서 문자열로 변환하기 위해 사용될 메시지 포매터 지정
        2. —from-beginning : 지정된 토픽의 가장 오래된 오프셋부터 메시지 읽기 (없으면 가장 최근 오프셋부터 읽어옴)
        3. —max-messages {정수값}: 종료되기 전 읽어올 최대 메시지 수
        4. —partition {정수값}: 지정된 ID의 파티션에서만 읽어온다.
        5. —offset : 읽어오기 시작할 오프셋. earliest로 지정하면 맨 처음부터, latest로 지정하면 가장 최신값부터 읽어옴
        6. —skip-message-on-error: 메시지에 에러가 있을 경우 실행을 중단하는게 아니라 넘어감
2. 메시지 포매터 옵션
    1. kafka.tools.LoggingMessageFormatter: 표준 출력이 아니라 로거를 사용해서 메시지를 출력함. 각 메시지는 INFO 레벨로 출력하며 타임스탬프, 키, 밸류를 포함한다.
    2. kafka.tools.ChecksumMessageFormatter: 메시지의 체크섬만 출력한다.
    3. kafka.tools.NoOpMessageFormatter: 메시지를 읽어오되 아무것도 출력하지 않는다.

1. 오프셋 토픽 읽어오기

클러스터의 컨슈머 그룹 별로 커밋된 오프셋을 확인해야 하는 경우 사용한다.

특정 그룹이 오프셋을 커밋하고 있는지 여부나 얼마나 자주 커밋했는지 등을 확인할 수 있다.

콘솔 컨슈머를 사용해서 __consumer_offset 내부 토픽을 읽어오면 된다. 

ex)

```jsx
$bin/kafka-console-consumer.sh --bootstrap-server localhost:9092\
--topic __consumer_offsets --froom-beginning --max-message 1\
--formatter "kafka.coordinator.group.GroupMetadataManager\$OffsetsMessageFormatter"\
--consumer-property exclude.internal.topics=false
```

## 12.5 파티션 관리

- 카프카는 파티션 관리에 사용할 수 있는 스크립트 역시 기본으로 탑재하고 있다.
    1. 리더 레플리카를 다시 선출하기 위한 툴
    2. 파티션을 브로커에 할당해주는 저수준 유틸리티
    
    위 두 툴은 카프카 클러스터 안의 브로커 간 메시지 트래픽 균형을 직접 맞춰줘야할 때 요긴하게 쓸 수 있다.
    

### 12.5.1 선호 레플리카 선출

카프카 클러스터에 균형이 맞지 않는 경우 아래와 같이 선호 레플리카 선출을 실행할 수 있다.

이 작업은 컨트롤러로 하여금 파티션에 대해 가장 적절한 리더를 고르도록 한다.

```jsx
$bin/kafka-leader-election.sh --bootstrap-server localhost:9092 \
--election-type PREFERRED \
--all-topic-partitions
```

만약 특정 파티션이나 토픽에 대해서만 선출하고 싶다면 아래와 같이 지정해준다.

**partitions.json 파일**

```jsx
{
	"partitions" : [
		{
			"partition":1,
			"topic": "test-topic"
		},
		{
			"partiton":2,
			"topic":"foo"
		}
	]
}
```

```jsx
$bin/kafka-leader-elections.sh --bootstrap-server localhost:9092 \
--election-type PREFERRED --path-to-json-file partitions.json
```

### 12.5.2 파티션 레플리카 변경하기

때로는 파티션 레플리카 할당을 수동으로 변경해주어야 할 수 있다.

이러한 작업이 필요한 경우는

- 자동으로 리더 레플리카를 분산시켜 주었는데도 브로커간 부하가 불균등할 때
- 브로커가 내려가서 파티션이 불완전 복제되고 있을 때
- 새로 추가된 브로커에 파티션을 빠르게 분산시켜주고 싶을 때
- 토픽의 복제 팩터를 변경해주고 싶은 경우

위와 같은 경우 `kafka-reassign-partitions.sh`를 사용한다.

자세한 예시는 책을 참고하자.

### 12.5.3 로그 세그먼트 덤프 뜨기 (`kafka-dump-log.sh`)

- 토픽 내 특정 메시지가 오염되어 컨슈머가 처리할 수 없는 경우, 특정 메시지의 내용물을 열어봐야 할 수 있다. 이 경우 [kafka-dump-log.sh](http://kafka-dump-log.sh) 툴을 활용한다.
- 이 툴을 사용하면 컨슈머로 읽어올 필요 없이 각각의 메시지를 바로 열어볼 수 있다.

자세한 예시는 책을 참고하자.

### 12.5.4 레플리카 검증 (`kafka-replica-verification.sh`)

- 클러스터 전체에 걸쳐 토픽 파티션의 레플리카들이 서로 동일하다는 점을 확인하고자 한다면 [kafka-replica-verification.sh](http://kafka-replica-verification.sh) 툴을 사용한다.
- 이 툴은 주어진 토픽 파티션의 모든 레플리카로부터 메시지를 읽어온 뒤, 모든 레플리카가 해당 메시지를 가지고 있다는 점을 확인하고, 주어진 파티션의 최대 랙 값을 출력한다.
- 다만 이 툴은 클러스터에 영향을 줄 수 있으므로 주의해서 사용한다.

## 12.6 기타 툴

더 많은 툴들에 대한 설명은 카프카 공식 홈페이지를 참고하자.

- 클라이언트 ACL
    - [kafka-acls.sh](http://kafka-acls.sh) 명령줄 툴은 카프카 클라이언트에 대한 접근 제어를 관리하기 위해 사용된다.
- 경량 미러메이커
    - [kafka-mirror-maker.sh](http://kafka-mirror-maker.sh) 스크립트를 데이터 미러링 용으로 사용할 수 있다.
- 테스트 툴
    - 카르카를 테스트하거나 기능 업그레이드에 사용할 수 있는 스크립트도 있다.
    - 카프카 버전 업그레이드 과정의 호환성 문제를 확인하기 위해 API 요소들의 서로 다른 버전을 확인하고 싶다면 [kafka-broker-api-versions.sh](http://kafka-broker-api-versions.sh) 툴을 사용한다.
    - 벤치마크와 스트레스 테스트 수행을 위한 테스트 프레임워크인 [trogdor.sh](http://trogdor.sh) 도 있다.

## 12.7 안전하지 않은 작업

기술적으로는 가능하지만, 극단적인 상황이 아닌 한 시도되지 말아야할 운영 작업이 있다.

여기서는 비상 상황에서 복구에 활용할 수 있도록, 이러한 작업들 중에서도 상대적으로 일반적인 것들에 대해 다룬다. 일반적인 클러스터 운영 작업 중에 사용하는 것은 권장하지 않는다.

- 클러스터 컨트롤러 이전하기
- 삭제될 토픽 제거하기
- 수동으로 토픽 삭제하기

위와 같은 작업이 있다. 위 작업을 진행할 때에는 주의를 요해야 한다.