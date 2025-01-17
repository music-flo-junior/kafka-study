# 챕터 4. 카프카 컨슈머: 카프카에서 데이터 읽기

## 카프카 컨슈머의 개념

### 컨슈머
- **컨슈머**는 데이터를 읽고 처리하는 역할을 담당한다.
- 만약 **컨슈머가 하나**뿐이라면, 새로 추가되는 메시지 속도를 따라잡지 못할 수 있다.
- 처리 지연이 발생하여 작업이 계속해서 뒤로 밀릴 가능성이 존재한다.
- 이를 해결하기 위해 **데이터 읽기 작업을 확장**할 필요가 있다.
    - 여러 프로듀서가 동일한 토픽에 메시지를 쓰는 것처럼,
    - 여러 컨슈머가 동일한 토픽에서 데이터를 분할하여 읽도록 구성이 가능하다.

### 컨슈머 그룹
- **카프카 컨슈머**는 일반적으로 **컨슈머 그룹**의 일부로 작동한다.
- 동일한 그룹에 속한 여러 컨슈머가 동일한 토픽을 구독할 경우에는 각 컨슈머는 해당 토픽의 **서로 다른 파티션**에서 메시지를 수신한다.

> #### 예시
> 1. **T1 토픽**: 4개의 파티션 보유.
> 2. **G1 컨슈머 그룹**:
>   - **C1만 존재**: C1이 모든 파티션의 메시지를 수신.
>   - **C2 추가**: C1과 C2가 각각 2개의 파티션에서 메시지를 수신.
>   - **C3, C4 추가**: 각각 하나의 파티션에서 메시지를 수신.
>   - **컨슈머가 파티션 수를 초과**: 일부 컨슈머는 메시지를 수신하지 않음.

컨슈머 그룹에 컨슈머를 추가하는 것은 카프카 토픽에서 읽어오는 **데이터를 확장하는 주된 방법**이다. 
또한, 여러 애플리케이션에서 동일한 토픽에서 데이터를 읽어와야 하는 경우도 매우 흔하다.
(카프카는 하나의 토픽에 쓰여진 데이터를 전체 조직 안에서 여러 용도로 사용할 수 있도록 만드는 것이 목표였다.)

위의 예시에서 새로운 컨슈머 그룹 **G2**를 추가하게 된다면, 이 컨슈머는 **G1**에서 무슨 행동을 하던지 상관 없이 **T1 토픽의 모든 메시지**를 받는다.

### 파티션 리밸런스

컨슈머 그룹에 속한 컨슈머들은 자신들이 구독하는 토픽의 파티션들에 대한 소유권을 공유한다. 새로운 컨슈머를 추가하면 이전에 다른 컨슈머가 읽고 있던 파티션으로 부터 메시지를 읽기 시작한다.
또한, 특정 컨슈머가 종료되도 마찬가지다. 잔류하고 있는 컨슈머 하나가 대신 받아서 읽기 시작한다. 이러한 컨슈머에 할당 된 파티션을 다른 컨슈머에게 할당하는 작업을 **리밸런스**라고 한다.

> #### 조급한 리밸런스 (eager rebalance)
> 조급한 리밸런스가 실행되면 **모든 컨슈머는 읽기를 멈추고 자신에게 할당 된 모든 소유권을 포기**한다. 그 이후 **다시 컨슈머 그룹에 참여**하여 새로운 파티션을 할당받는다.

> #### 협력적 리밸런스 (cooperative, incremental rebalance)
> 협력적 리밸런스는 **한 컨슈머에 할당되어 있던 파티션만을 다른 컨슈머에게 재할당**한다. 재할당 되지 않은 파티션에서 레코드를 읽고 있던 컨슈머들은 영향이 없다.
> 먼저, 컨슈머 그룹 리더가 다른 컨슈머들에게 각자 할당 된 파티션 중 일부가 재할당 된다고 통보하면, 컨슈머는 해당 파티션에서 데이터 읽기를 멈추고 소유권을 포기한다. 
> 그 후 **컨슈머 그룹 리더가 포기 된 파티션을 재할당**한다.

컨슈머는 해당 컨슈머 그룹의 **그룹 코디네이터** 역할을 지정받은 브로커에 **하트비트**를 전송하므로써 멤버십과 할당 된 파티션에 대한 소유권을 유지한다.

컨슈머가 일정 시간 이상 하트비트를 보내지 않으면, **세션 타임아웃이 발생하면서 해당 컨슈머가 죽었다고 간주하고 리밸런스**를 실행한다.

> #### Q. 파티션은 어떻게 컨슈머에게 할당될까?
> 컨슈머가 그룹에 참여하고 싶을 때 그룹 코디네이터에게 JoinGroup 요청을 보낸다. 가장 먼저 그룹에 참여한 컨슈머가 그룹 리더가 된다. 
> 리더는 그룹 코디네이터로부터 해당 그룹 안에 있는 모든 컨슈머의 목록을 받아 각 컨슈머에 파티션의 일부를 할당한다.
> 파티션 할당이 결정되면 **할당 내역을 리더가 그룹 코디네이터에게 전달하고, 이 할당 내역을 모든 컨슈머에게 전파**한다.

### 정적 그룹 멤버십

컨슈머가 갖는 컨슈머 그룹의 멤버로서의 자격은 일시적이다. 컨슈머가 컨슈머 그룹을 떠나는 순간 해당 컨슈머에 할당되어 있던 파티션은 해제된다.
다시 참여하면 새로운 멤버 아이디가 발급되면서 리밸런스 프로토콜에 의해 새로운 파티션이 할당된다.

위 설명은 컨슈머에 고유한 `group.instance.id` 값을 잡지 않는 한 유효하다. (group.instance.id을 설정하면 컨슈머가 정적인 멤버가 된다.)

컨슈머가 정적 멤버로 처음 참여하면 평소와 같이 파티션이 할당되는데, 이 컨슈머가 **꺼질 경우 자동으로 그룹을 떠나지 않는다.**
그리고 컨슈머가 다시 그룹에 조인하면 멤버십이 그대로 유지되지 때문에 리밸런스가 발생할 필요 없이 예전에 할당 받았던 파티션들을 그대로 재할당 받는다.
그룹 코디네이터는 그룹 내 파티션 할당 정보를 캐시하고 있다. (같은 `group.instance.id`를 가진 컨슈머가 조인한다면, 에러가 발생한다.)

컨슈머 그룹의 정적 멤버는 종료할때 **미리 컨슈머 그룹을 떠나지 않는다**. 정적 멤버가 종료되었음을 인지하는 방법은 `session.timeout.ms` 설정에 달려 있다.

## [카프카 컨슈머 생성하기](../kotlin-example/src/main/kotlin/KafkaConsumer.kt)

카프카 레코드를 읽어오는 단계는 KafkaConsumer 인스턴스를 생성하는 것이다. 
중요한 포인트는 해당 인스턴스가 속하는 컨슈머 그룹을 지정하는 **group.id** 속성이다.

``` kotlin
val properties = Properties()

properties[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = "localhost:9092,localhost:9093,localhost:9094"
properties[ConsumerConfig.GROUP_ID_CONFIG] = "CountryCounter" // consumer group
properties[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = "org.apache.kafka.common.serialization.StringDeserializer"
properties[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = "org.apache.kafka.common.serialization.StringDeserializer"

val consumer = KafkaConsumer<String, String>(properties)
```

## [토픽 구독하기](../kotlin-example/src/main/kotlin/KafkaConsumer.kt)

컨슈머를 생성하고 할 일은 1개 이상의 토픽을 구독하는 것 이다.

``` kotlin
consumer.subscribe(listOf("kafka.example.topic"))
```

정규식을 매개변수로 subscribe를 호출할 수 있다. 다수의 토픽 이름에 매칭 될 수 있다. 누군가 정규식과 매치되는 새로운 토픽을 생성하면 거의 즉시 리밸런스가 발생하면서 새로운 토픽으로부터 읽기 작업을 수행한다.
(다수의 토픽에서 레코드를 읽어와서 다른 유형의 데이터를 처리하는 경우 유용하다.)

``` kotlin
consumer2.subscribe(Pattern.compile("kafka.*"))
```

> #### Q. 여러 토픽을 동시에 구독했을때 컨슈머는 어떻게 파티션을 할당받을까?
> 예를 들어, 컨슈머 그룹에 컨슈머가 3개 있고, 토픽을 T1, T2를 구독하고, 각각의 토픽이 5개씩의 파티션이 있다고 가정하자.
> 이 경우에는 T1(5개) + T2(5개) = 10개의 파티션을 3개의 컨슈머가 나눠서 할당받는다. 이때 하나의 토픽의 파티션만 할당받을 수도 있다. (카프카의 파티션 할당 알고리즘에 따라 다르기는 하다.) 

## [폴링 루프](../kotlin-example/src/main/kotlin/KafkaConsumer.kt)

컨슈머 API의 핵심은 서버에 추가 데이터가 들어왔는지 폴링하는 단순한 루프다. 컨슈머 애플리케이션은 대부분 계속 데이터를 폴링하는 오래 돌아가는 애플리케이션이다.
계속 카프카를 폴링하지 않으면 죽은 것으로 간주되어 이 컨슈머가 가지고 있던 파티션은 다른 컨슈머에게 넘겨진다.

``` kotlin
val records = consumer.poll(Duration.ofMillis(100))
```
poll은 레코드들이 저장 된 리스트를 반환한다. 각각의 레코드는 토픽, 파티션, 오프셋, 키, 밸류를 포함한다. 
poll()을 호출하면, 컨슈머는 그룹 코디네이터를 찾아서 컨슈머 그룹에 참여하고, 파티션을 할당받는다. 
poll()이 `max.poll.interval.ms`에 지정 된 시간 이상으로 호출되지 않을 경우 컨슈머를 죽은 것으로 판단한다.

### 스레드 안정성

하나의 스레드에서 동일한 그룹 내 여러개의 컨슈머를 생성할 수 없다. 같은 컨슈머를 다수의 스레드가 안전하게 사용할 수도 없다.
**하나의 스레드 당 하나의 컨슈머가 원칙**이다. 동일한 그룹에 속하는 여러개의 컨슈머를 운용하고 싶다면 스레드를 여러 개 띄워서 돌려야 한다.

``` kotlin
val executor = Executors.newFixedThreadPool(5)

(0 until 10).asSequence().forEach { _ ->
    executor.execute {
        subscribeTopic().use { consumer ->
            runPollingLoop(consumer)
        }
    }
}

executor.shutdown()
```
(대략 이런식..?)

## 컨슈머 설정하기

모든 컨슈머 설정은 [아파치 카프카 문서](https://kafka.apache.org/documentation/#consumerconfigs)에 정리되어 있다.
대부분의 매개 변수는 합리적인 기본 값을 가지고 있기 때문에 딱히 변경할 필요는 없다. 다만 몇몇 매개 변수는 컨슈머의 성능과 가용성에 영향을 준다.

### `fetch.min.bytes`

이 속성은 브로커로 부터 레코드를 얻어올 때 받는 데이터의 최소량을 지정할 수 있다. 브로커가 컨슈머로부터 레코드 요청을 받았는데,
새로 보낼 레코드 양이 설정 값 보다 작을 경우 메시지를 기다렸다가 보내준다. 이 값이 증가하면 처리량이 적은 상황에서 지연이 증가함을 명심하라.

### `fetch.max.wait.ms`

이 속성은 카프카가 컨슈머에게 응답하기 전 얼마나 오래 기다릴 것인지를 결정한다. 기본적으로 카프카는 500ms를 기다리도록 되어 있다.
카프카는 토픽에 컨슈머에게 리턴 할 데이터가 부족할 경우 리턴할 데이터 최소량 조건을 맞추기 위해 500ms까지 기다리는 것 이다.
만약, `fetch.min.bytes`가 1MB이고, `fetch.max.wait.ms`가 100ms일 경우 두 조건 중 하나가 만족하는 대로 리턴한다.

### `fetch.max.bytes`

이 속성은 컨슈머가 브로커를 폴링할 때 카프카가 리턴하는 최대 바이트 수를 지정한다. 이것은 컨슈머가 서버로 부터 받은 데이터를 저장하기 위해 사용하는 메모리 양을 제한한다.
브로커가 컨슈머에 레코드를 보낼 때는 배치 단위로 보내며, **브로커가 보내야 하는 첫번째 배치의 크기가 이 설정값을 넘겨도 그대로 전송**한다.

### `max.poll.records`

이 속성은 poll() 1회 호출 시 리턴되는 **최대 레코드 수를 지정**한다.

### `max.partition.fetch.bytes`

이 속성은 서버가 파티션별로 리턴하는 최대 바이트 수를 결정한다. 레코드 목록을 리턴할 때 메모리 상에 저장된 레코드 객체의 크기는 컨슈머에 할당 된 파티션 별로 최대 이 속성 값만큼 차지할 수 있다.
이 설정을 사용해서 메모리 사용량을 조절하기는 매우 어렵다. 특별한 이유가 아닌 한 `fetch.max.bytes`를 사용하라

### `session.timeout.ms` 와 `heartbeat.interval.ms`

컨슈머가 브로커와 신호를 주고 받지 않아도 살아있는 것으로 판정되는 최대 시간의 기본값은 10초다. (3.0 이상에선 45초)
만약 컨슈머가 그룹 코디네이터에게 하트비트를 보내지 않은 채 `session.timeout.ms`가 지나면 그룹 코디네이터는 해당 컨슈머를 죽은것으로 간주하고 리밸런스를 진행한다.
이 속성은 `session.timeout.ms`가 컨슈머가 하트비트를 보내지 않을 수 있는 최대 시간을 결정한다는 것을 의미한다. `heartbeat.interval.ms`를 `session.timeout.ms`의 3분의 1정도로 사용하라.

### `max.poll.interval.ms`

이 속성은 컨슈머가 **폴링을 하지 않고** 죽은 것으로 판정되지 않을 수 있는 최대 시간을 지정한다.
앞에서 언급했듯 하트비트와 세션 타임아웃은 카프카가 죽은 컨슈머를 찾아내고 파티션을 해제하는 주된 메커니즘이다.
하지만, 하트비트는 **백그라운드 스레드**에 의해 전송된다. 메인 스레드는 데드락 상황인데 하트비트는 전송하고 있을 수 있다.
해당 타임아웃이 발생하면 백그라운드 스레드는 브로커로 하여금 컨슈머가 죽어 리밸런스를 수행해야 한다는 **leave group** 요청을 보낸 뒤 하트비트를 중단한다.

### `request.timeout.ms`

컨슈머가 브로커로부터 응답을 기다릴 수 있는 최대 시간이다. 브로커가 이 시간 내에 응답하지 않으면 클라이언트는 재연결을 시도한다.

### `auto.offset.reset`

컨슈머가 예전에 오프셋을 커밋한 적이 없거나, 커밋 된 오프셋이 유효하지 않을 때, 파티션을 읽기 시작할 때의 작동을 정의한다.
기본은 `latest`인데, 만약 유효한 오프셋이 없을 경우 컨슈머는 가장 최신 레코드 부터 읽기 시작한다.
다른 값으로는 `earliest`가 있는데. 유효한 오프셋이 없는 경우 맨 처음부터 모든 데이터를 읽는 방식이다.

### `enable.auto.commit`

컨슈머가 자동으로 오프셋을 커밋할지 여부를 결정한다. 언제 커밋할지를 정하고 싶다면 이 값을 false로 놓으면 된다.
`auto.commit.interval.ms`를 통해 오프셋 커밋 주기를 제어할 수 있다.

### `partition.assignment.strategy`

컨슈머 그룹에 속한 컨슈머에게는 파티션이 할당된다. PartitionAssignor 클래스는 컨슈머와 토픽이 주어졌을 때 어느 컨슈머에 어느 파티션이 할당될지 결정한다.

#### Range

컨슈머가 구독하는 각 토픽의 파티션들을 연속된 그룹으로 나눠 할당한다. 컨슈머 C1, C2가 각각 3개의 파티션을 갖는 T1, T2를 구독하면 아래와 같이 할당받는다.

- **컨슈머 C1**: T1: [P0 P1], T2: [P0 P1]
- **컨슈머 C2**: T1: [P3], T2: [P3]

#### RoundRobin

모든 구독된 토픽의 파티션을 가져다가 순차적으로 하나씩 할당한다.

#### Sticky

파티션을 균등하게 할당하면서도, 리밸런스가 발생했을 때 가능하면 많은 파티션들이 같은 컨슈머에 할당되어 오버헤드를 최소화 하는 목적으로 만들어졌다.
라운드 로빈과 비슷하게 동작하지만 파티션 이동이 더 적다.

#### Cooperative Sticky

Sticky 할당자와 동일하지만, 컨슈머가 재할당되지 않은 파티션으로부터 레코드를 계속해서 읽어올 수 있도록 협력적 리밸런스를 지원한다.

### `client.id`

어떠한 문자열도 될 수 있으며 클라이언트 식별 용도로 사용한다.

### `client.rank`

기본적으로 컨슈머는 각 파티션 리더 레플리카에서 메시지를 읽는다. 하지만 클러스터가 다수의 데이터 센터에 설치되어 있다면, 같은 영역에 있는 레플리카에서 읽는게 유리하다.
이를 통해 클라이언트가 위치한 영역을 식별하게 해줄 수 있다. 그 후 브로커의 설정을 변경하여 가까운 레플리카에서 읽어오게 할 수 있다.

### `group.instance.id`

정적 그룹 멤버십을 적용하기 위해 사용되는 설정이다.

### `receive.buffer.bytes`, `send.buffer.bytes`

TCP 수신 및 수신 버퍼의 크기를 가리킨다. 운영체제 기본 값이 사용된다.

### `offset.retention.minutes`

이것은 브로커 설정이지만 컨슈머 작동에 큰 영향을 미친다. 컨슈머 그룹에 현재 돌아가고 있는 컨슈머들이 있는 한, 컨슈머 그룹이 각 파티션에 대해 커밋한 마지막 오프셋 값은 카프카에 의해 보존된다.
하지만 그룹이 비게 되면 카프카는 커밋 된 오프셋을 이 설정값에 지정 된 기간동안만 보관한다.

## [오프셋과 커밋](../kotlin-example/src/main/kotlin/KafkaConsumer.kt)

우리가 poll()을 호출할 때 마다 카프카에 쓰여진 메시지 중에서 컨슈머 그룹에 속한 컨슈머들이 아직 읽지 않은 레코드가 리턴된다.
뒤집어 말하면, 이를 이용해서 그룹내의 컨슈머가 어떤 레코드를 읽었는지 판단할 수 있다.

카프카에서 **파티션의 현재 위치를 업데이트 하는 작업을 오프셋 커밋**이라고 한다.
전통적인 메시지 큐와는 다르게 카프카는 레코드를 개별적으로 커밋하지 않는다. 컨슈머는 파티션에서 성공적으로 처리한 **마지막 메시지를** 커밋함으로써 그 앞에 모든 메시지들 역시 성공적으로 처리되었음을 암묵적으로 나타낸다.

카프카에는 특수 토픽인 `__consumer_offsets` 토픽에 각 파티션별로 커밋 된 오프셋을 업데이트하도록 메시지를 보낸다.
리밸런스가 발생하면 각각의 컨슈머는 리밸런스 이전에 처리하고 있던 것과 다른 파티션을 할당받을 수 있다. 어디서 부터 작업을 재개해야 하는지 컨슈머는 각 파티션의 마지막 커밋 메시지를 읽어 재개한다.

만약 커밋 된 오프셋이 클라이언트가 처리한 마지막 오프셋보다 작을 경우, 마지막으로 처리 된 오프셋과 커밋 된 오프셋 사이의 메시지는 두번 처리된다. 반대의 경우에는 누락된다.

> #### Q. 어느 오프셋이 커밋될까?
> 오프셋을 커밋할 때 자동으로 하건 지정하지 않고 하던 poll()이 리턴한 **마지막 오프셋 바로 다음 오프셋을 커밋**하는 것이 기본 동작이다.


### 자동 커밋

오프셋을 가장 커밋하기 쉬운 방법은 컨슈머가 대신 하도록 하는 것 이다. `enable.auto.commit`설정이 true인 경우 5초마다 한번씩 마지막 오프셋을 커밋한다.
폴링 루프의 마지막에서 오프셋을 커밋해야하는지 확인 한 뒤 마지막에 커밋처리한다.

커밋한 뒤 3초뒤에 크래시 될 경우 3초까지 읽혔던 이벤트는 두번씩 처리된다. 중복 메시지를 방지하기엔 충분하지 않다.

### 현재 오프셋 커밋하기

개발자는 오프셋이 커밋되는 시각을 제어하고자 한다. 메시지 유실의 가능성을 제거하고 리밸런스 발생 시 중복되는 메시지의 수를 줄이기 위해서다.
컨슈머 API는 원하는 시간에 오프셋을 커밋하는 옵션을 제공한다.

commitSync()는 poll()에 의해 리턴된 마지막 오프셋을 커밋한다. 커밋이 완료되면 리턴, 실패하면 예외가 발생한다. 리턴 된 모든 레코드 처리 전 commitSync를 먼저 호출하면 누락될 위험이 있다.

``` kotlin
private fun currentOffsetCommitSync(consumer: KafkaConsumer<String, String>) {
    consumer.commitSync()
}
```

### 비동기적 커밋

수동 커밋의 단점 중 하나는 브로커가 커밋 요청에 응답할 때 까지 애플리케이션이 블록된다. 이것은 처리량을 제한한다.
비동기적 커밋을 통해 처리를 계속하게 만들 수 있다.

``` kotlin
private fun currentOffsetCommitAsync(consumer: KafkaConsumer<String, String>) {
    consumer.commitAsync { _, e ->
        if (e != null) {
            throw e
        }
    }
}
```

비동기적 커밋은 재시도를 하지 않는다. 이미 다른 커밋 시도가 성공했을 수 있기 때문이다.

### 동기적 커밋과 비동기적 커밋을 함께 사용하기

대체로, 재시도 없는 커밋이 실패한다고 해서 문제가 되지 않는다. 이것이 컨슈머를 닫기 전이나 리밸런스 전 마지막 커밋이라면 성공 여부를 확인 할 필요가 있다.
일반적인 패턴은 종료 직전에 commitAsync(), commitSync()를 함께 사용한다.

``` kotlin
@Volatile
var closing = false

private fun runPollingLoop(consumer: KafkaConsumer<String, String>) {
    while (!closing) {
        val records = consumer.poll(Duration.ofMillis(100))

        records.forEach { record ->
            println("topic = ${record.topic()}, partition = ${record.partition()}, offset = ${record.offset()}, key = ${record.key()}, value = ${record.value()}")

            currentOffsetCommitAsync(consumer)
        }
    }

    currentOffsetCommitSync(consumer)
}
```

정상적인 상황에서는 commitAsync(), 컨슈머가 종료되는 시점에는 commitSync()를 사용한다.

### 특정 오프셋 커밋하기

가장 최근 오프셋 커밋은 배치 작업이 전부 종료되어야만 가능하다. 하지만 배치 사이즈가 큰 경우 전체 배치를 재처리 해야하는 일이 발생할 수 있다.
이를 위해 특정 오프셋을 커밋할 수 있는 방법을 지원한다.

``` kotlin
private fun recordOffsetCommitAsync(
    record: ConsumerRecord<String, String>,
    consumer: KafkaConsumer<String, String>
) {
    val map = HashMap<TopicPartition, OffsetAndMetadata>()
    map[TopicPartition(record.topic(), record.partition())] = OffsetAndMetadata(record.offset() + 1, "no metadata")

    consumer.commitAsync(map) { _, e ->
        if (e != null) {
            throw e
        }
    }
}
```
## [리밸런스 리스너](../kotlin-example/src/main/kotlin/KafkaConsumer.kt)

컨슈머는 종료하기 전이나 리밸런싱이 시작되기 전에 정리 작업을 해줘야 할 것이다.

컨슈머에 할당 된 파티션이 해제될 것이라는 걸 알게 된다면 해당 파티션에서 마지막으로 처리한 이벤트의 오프셋을 커밋해야할 것이다.

컨슈머 API는 컨슈머에 파티션이 할당되거나 해제될 때 사용자의 코드가 실행되도록 하는 메커니즘을 제공한다.
리밸런스 리스너는 아래와 같은 3개의 메서드를 제공한다.

``` java
public interface ConsumerRebalanceListener {
    void onPartitionsRevoked(Collection<TopicPartition> partitions);
    void onPartitionsAssigned(Collection<TopicPartition> partitions);
    default void onPartitionsLost(Collection<TopicPartition> partitions) {
        onPartitionsRevoked(partitions);
    }
}
```

### onPartitionsAssigned

파티션이 컨슈머에게 재할당 된 후에 컨슈머가 메시지를 읽기 시작하기 전에 호출된다. 상태를 적재하거나, 필요한 오프셋을 탐색하거나 등 준비 작업을 수행한다.
컨슈머가 문제 없이 그룹에 조인하려면 `max.poll.timeout.ms` 안에 완료되어야 한다.

### onPartitionsRevoked

컨슈머가 할당받았던 파티션이 할당 해제될 때 호출된다. 일반적인 상황으로 리밸런스가 되었을 때 호출되며 컨슈머가 메시지 읽기를 멈춘 뒤 리밸런스가 실행되기 전에 호출된다.
협력적 리밸런스 알고리즘이 사용되었을 경우에는 리밸런스가 완료되고 컨슈머에서 할당 해제되어야 할 파티션들에 대해서만 호출된다.

### onPartitionsLost

협력적 리밸런스 알고리즘이 사용되었을 경우, **할당 된 파티션이 리밸런스 알고리즘에 의해 해제되기 전 다른 컨슈머에 할당된 예외적인 상황**에서만 호출된다.
여기서는 상태나 자원들을 정리한다.

## 특정 오프셋의 레코드 읽어오기

각 파티션의 마지먹으로부터 커밋 된 오프셋부터 읽기를 시작해서 모든 메시지를 순차적으로 처리하기 위해 poll()을 사용했다. 하지만 다른 오프셋에서 부터 읽기를 시작하고 싶은 경우가 있다.
카프카는 다음 번 poll() 호출이 다른 오프셋에서 부터 읽기를 시작하도록 하는 다양한 메서드를 제공한다.

파티션의 맨 앞에서 부터 모든 메시지를 읽고자 하면 `seekToBeginning`을 사용하고, 파티션에 새로 들어온 메시지 부터 읽기를 시작하고자 한다면 `seekToEnd`를 사용하면 된다.

## 폴링 루프를 벗어나는 방법

폴링 루프를 무한루프에서 수행한다는 사실을 걱정하지 말자, 루프를 깔끔하게 탈출할 수 있다.

컨슈머를 종료하고자 할 때 컨슈머가 오래 poll()을 기다리고 있더라도 즉시 루프를 탈출하고 싶다면 다른 스레드에서 `consumer.wakeup()`을 호출해야 한다.

## 디시리얼라이저

카프카 프로듀서는 카프카에 데이터를 쓰기 전 커스텀 객체를 바이트 배열로 변환하기 위해 시리얼라이저가 필요하다.
마찬가지로 카프카 컨슈머도 바이트로 받은 배열을 자바 객체로 변환하기 위해 디시리얼라지저가 필요하다. 

## 독립 실행 컨슈머 

경우에 따라서는 더 단순한게 필요할 수 있다. 하나의 컨슈머가 토픽의 모든 파티션으로 부터 모든 데이터를 읽어와야 하거나, 특정 파티션으로부터 데이터를 읽어와야 할 때가 있다.
이러한 경우 컨슈머 그룹이나 리밸런스 기능이 필요하지 않다.
