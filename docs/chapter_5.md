### 챕터 5. 프로그램 내에서 코드로 카프카 관리하기

카프카를 관리하는 데 있어서는 많은 CLI, GUI 툴 들이 있지만 클라이언트 애플리케이션에서 직접 관리 명령을 내려야 할 때도 있다.

아파치 카프카 버전 0.11 이전까지는 명령줄 프로그램으로만 가능했던 관리 기능이 가능했지만, 0.11 부터 프로그램적인 관리 기능 API를 제공하기 위한 목적으로 AdminClient 가 추가되었다.
토픽 목록 조회, 생성, 삭제, 클러스터 상세 정보 확인, ACL 관리, 설정 변경 등의 기능이 이것으로 가능하다.

이 장에서는 AdminClient 대해서 간략하게 살펴보고, 이것을 애플리케이션에서 어떻게 사용할 수 있는지를 알아볼 것이다.

### 5.1 AdminClient 개요

카프카 AdminClient를 사용하기 전에 핵심이 되는 설계 원리에 대해 짚고 넘어가는 게 좋다.

#### 5.1.1 비동기적이고 최종적 일관성을 가지는 API

- AdminClient 를 이해할 때 가장 중요한 것은 비동기적으로 작동한다는 사실이다.
- 각 메서드는 요청을 클러스터 컨트롤러로 전송한 뒤 바로 1개 이상의 Future 객체를 리턴한다.
- AdminClient는 Future 객체를 Result 객체 안에 감싸는데, Result 객체는 작업이 끝날 때까지 대기하거나 작업 결과에 대해 일반적으로 뒤이어 쓰이는 작업을 수행하는 핼퍼 메서드를 가지고 있다. 
- 예를 들어서, 카프카의 AdminClient.createTopics 메서드는 CreateTopicsResult 객체를 리턴하는데, 이 객체는 모든 토피기 생성될 때까지 기다리거나, 각각의 토픽 상태를 하나씩 확인하거나, 아니면 특정한 토픽이 생성된 뒤 해당 토픽의 설정을 가져올 수 있도록 해준다.
- 카프카 컨트롤러로 부터 브로커의 메타데이터 전파가 비동기적으로 이루어지기 때문에 AdminClient API가 리턴하는 Future 객체들은 컨트롤러의 상태가 완전히 업데이트된 시점에 완료된 것으로 간주된다. 
- 이 시점에서 모든 브로커가 전부 다 새로운 상태에 대해 알고 있지는 못할 수 있기 때문에, listTopics 요청은 최신 상태를 전달받지 않은 브로커에 의해 처리될 수 있는 것이다.
- 이러한 속성을 최종적 일관성이라고 한다. 최종적으로 모든 브로커는 모든 토픽에 대해 알게 될 것이지만, 정확히 그게 언제가 될지에 대해서는 아무런 보장도 할 수 없다.

#### 5.1.2 옵션

- AdminClient의 각 메서드는 메서드별로 특정한 Options 객체를 인수로 받는다.
- 모든 AdminClient 메서드가 가지고 있는 매개변수는 timeoutMs다.

#### 5.1.3 수평 구조

- 모든 어드민 작업은 KafkaAdminClient에 구현되어 있는 아파치 카프카 프로토콜을 사용해서 이루어진다.
- 여기에는 객체 간의 의존 관계나 네임스페이스 같은 게 없다.
- 따러서, JavaDoc 문서에서 필요한 메서드를 하나 찾아서 쓰기만 해도 되는데다가 IDE에서 간편하게 자동 완성을 해주기까지 하는 것이다.

#### 5.1.4 추가 참고 사항

- 클러스터의 상태를 변경하는 모든 작업(create, delete, alter)은 컨트롤러에 의해 수행된다.
- 클러스터 상태를 읽기만 하는 작업(list, describe)는 아무 브로커에서나 수행될 수 있으며 클라이언트 입장에서 보이는 가장 부하가 적은 브로커로 전달된다.
- 카프카 2.5 출시 직전 기준으로, (지금은 3.9 출시된 상태..) 대부분의 어드민 작업은 AdminClient를 통해서 수행되거나 아니면 주키퍼에 저장되어 있는 메타데이터를 직접 수정하는 방식으로 이루어진다.
- 주키퍼 의존성을 완전히 제거되기 때문에 주키퍼를 직접 수정하는 것은 절대 쓰지 말 것을 권장한다.

### 5.2 AdminClient 사용법: 생성, 설정, 닫기

- AdminClient를 사용하기 위해 가장 먼저 해야 할 일은 AdminClient 객체를 생성하는 것이다.
```
Properties props = new Properties();
props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
AdminClient admin = AdminClient.create(props);
// TODO: AdminClient를 사용해서 필요한 작업을 수행한다.
admin.close(Duration.ofSeconds(30));
```

- 정적 메서드인 create 메서드는 설정값을 담고 있는 Properties 객체를 인수로 받는다.
- 반드시 있어야 하는 설정은 클러스터에 대한 URI 하나 뿐이다.
- 프로덕션 환경에서는 브로커 중 하나에 장애가 발생할 경우를 대비해서 최소한 3개 이상의 브로커를 지정하는 것이 보통이다.
- AdminClient를 시작했으면 결국엔 닫아야 한다. 
- close 를 호출할 때는 아직 진행중인 작업이 있을 수 있다는 걸 감안해야 한다.
- close 메서드는 타임아웃 매개변수를 받는데, 타임아웃이 만료될 때까지 응답을 기다리고 발생하면 모든 진행중인 작동을 멈추고 모든 자원을 해제한다.
- 타임아웃 없이 close 를 호출한다는 것은 얼마가 되었든 모든 진행중인 작업이 완료될 때까지 대기하게 된다는 의미다.

#### 5.2.1 client.dns.lookup

- 이 설정은 아파치 카프카 2.1.0 에서 추가되었다.
- 카프카는 부트스트랩 서버 설정에 포함된 호스트명을 기준으로 연결을 검증하고, 해석하고, 생성한다.
- 이 단순한 모델은 두 가지 맹점이 있는데, DNS 별칭를 사용할 경우와 2개 이상의 IP 주소로 연결되는 하나의 DNS 항목을 사용할 경우다.


이  두 가지 시나리오를 좀 더 자세히 살펴보자.

1. DNS 별칭을 사용하는 경우
- 모든 브로커들을 부트스트랩 서버 설정에 일일이 지정하는 것보다 이 모든 브로커 전체를 가리킬 하나의 DNS 별칭을 만들 수 있다.
- 어떤 브로커가 클라이언트와 처음으로 연결될지는 그리 중요하지 않기 때문에 부트스트래핑을 위해 all-brokers.hostname.com를 사용할 수 있는 것이다.
- 이것은 매우 편리하지만 SASL을 사용해서 인증을 하려고 할 때에는 문제가 생신다.
- SASL을 사용할 경우 클라이언트는 all-brokers.hostname.com에 대해서 인증을 하려고 하는데, 서버의 보안 주체는 broker2.hostname.com 이라서 호스트 명이 일치하지 않아 SASL 은 인증을 거부하고 연결도 실패한다.
- 이러한 경우 client.dns.lookup=resolve_canonical_bootstrap_servers_only 설정을 잡아주면 된다.
- 이 설정이 되어 있는 경우 클라이언트는 DNS 별칭을 '펼치게' 되기 때문에 DNS 별칭에 포함된 모든 브로커 이름을 일일이 부트스트랩 서버 목록에 넣어 준 것과 동일하게 작동하게 된다.

2. 다수의 IP 주소로 연결되는 DNS 이름을 사용하는 경우
- 하나의 DNS 를 여러 개의 IP 주소로 연결하는 것은 매우 흔하다.
- 해석된 IP 주소가 사용 불능일 경우 브로커가 멀정하게 작동하고 있는데도 클라이언트는 연결에 실패할 수 있다는 애기다.
- 바로 이러한 이유 때문에 클라이언트가 로드 밸런싱 계층의 고가용성을 충분히 활용할 수 있도록 client.dns.lookup=use_all_dns_ips를 사용하는 것이 강력히 권장된다.

#### 5.2.2 request.timeout.ms

- 이 설정은 애플리케이션이 AdminClient의 응답을 기다릴 수 있는 시간의 최대값을 정의한다.
- 이 시간에는 클라이언트가 재시도가 가능한 에러를 받고 재시도하는 시간이 포함된다.
- 기본값은 120초

### 5.3 필수적인 토픽 관리 기능

- AdminClient 의 가장 흔한 활용 사례는 토픽 관리다.
- 여기에는 토픽 목록 조회, 상세 내역 조회, 생성 및 삭제가 들어간다.

> 토픽 목록 조회 
```
ListTopicResult topics = admin.listTopics();
topics.names().get().forEach(System.out::println);
```

- admin.listTopics() 가 Future 객체들을 감싸고 있는 ListTopicsResult 객체를 리턴한다는 점을 유념하자.
- 이 Future 객체에서 get 메서드를 호출하면, 실행 스레드는 서버가 토픽 이름 집합을 리턴할 때까지 기다리거나 아니면 타임아웃 예외를 발생시킨다.
- 토픽 이름 집합을 받으면 우리는 반복문을 사용해서 모든 토픽 이름을 출력할 수 있다.

> 정확한 설정을 갖는 토픽이 존재하는지 확인 및 존재하지 않는 경우 새로운 토픽 생성
```
DescribeTopicsResult demoTopic = admin.describeTopics(TOPIC_LIST);

try {
    topicDescription = demoTopic.values().get(TOPIC_NAME).get();
    System.out.println("Description of demo topic:" + topicDescription);
    
    if(topicDescription.partitions().size() != NUM_PARTITIONS) {
       System.out.println("Topic has wrong number of partitions. Exiting.");
       System.exit(-1);
    }
} catch (ExecutionException e){
    // 종류를 막론하고 예외가 발생하면 바로 종료한다.
    if (! (e.getCause() instanceof UnknownTopicOrPartitionException)) {
        e.printStackTrace();
        throw e;
    }
    
    // 여기까지 진행됐다면, 토픽은 존재하지 않는다.
    System.out.println("Topic " + TOPIC_NAME + " does not exist. Going to create it now");
    
    // 파티션 수와 레플리카 수는 선택사항임에 유의. 만약 이 값들을 지정하지 않으면 카프카 브로커에 설정된 기본값이 사용된다.
    CreateTopicResult newTopic = admin.createTopics(Collections.singletonList(new NewTopic(TOPIC_NAME, NUM_PARTITIONS, PER_FACTOR)));
    
    // 토픽이 제대로 생성됐는지 확인한다.
    if (newTopic.numPartitions(TOPIC_NAME).get() != NUM_PARTITIONS) {
       System.out.println("Topic has wrong number of partitions. Exiting.");
       System.exit(-1);
    }
}   

```

> 토픽 삭제

( 책 내용이 이상한듯..?)

- 삭제할 토픽 목록과 함께 deleteTopics 메서드를 호출한 뒤, get() 을 호출해서 작업이 끝날 때 까지 기다린다.

> 카프카 응답을 기다리며 서버 스레드가 블록되지 않도록 처리하지 않는 방법

- 사용자로부터 계속해서 요청을 받고, 카프카로 요청을 보내고, 카프카가 응답하면 그제서야 클라이언트로 응답을 보내는 게 더 합리적이다.

```
vertx.createHttpServer().requestHandler(request -> {
    string topic = request.getParam("topic");
    string timeout = request.getParam("timeout");
    int timeoutMs = NumberUtils.toInt(timeout, 1000);
    
    DescribeTopicResult demoTopic = admin.describeTopics(
        Collections.singletonList(topic),
        new DescribeTopicsOptions().timeoutMs(timeoutMs));

demoTopic.values().get(topic).whenComplete(
    (final TopicDescription topicDescription, final Throwable throwable) -> {
        if (throwable != null) {
            request.response().end("Error trying to describe topic "+ topic + " due to " + throwable.getMessage());
        } else {
            request.response().end(topicDescription.toString());
        }
    }
```
- vert.x는 비동기 네트워크 서버 프레임워크 
- 호출시 블록되는 get()를 호출하는 대신 Future 객체의 작업이 완료되면 호출될 함수를 생성한다. -> whenComplete
- 여기서 중요한 것은 우리가 카프카로부터의 응답을 기다리지 않는다는 점이다. 카프카로부터 응답이 도착하면 DescriptionTopicResult 가 HTTP 클라이언트에게 응답을 보낼 것이다.
- 그 사이, HTTP 서버는 다른 요청을 처리할 수 있다.

### 5.4 설정 관리
- 설정 관리는 ConfigResource 객체를 사용해서 할 수 있다.
- 설정 가능한 자원에는 브로커, 브로커로그, 토픽이 있다.
- 브로커와 브로커 로깅 설정을 확인하고 변경하는 작업은 kafka-configs.sh 혹은 다른 카프카 관리 툴을 사용해서 하는 게 보통이지만, 애플리케이션에서 사용하는 토픽의 설정을 확인하거나 수정하는 것은 상당히 흔하다.

> 압착 설정이 된 토픽인지 확인하고, 설정이 안 되어 있을 경우 설정을 교정

```
ConfigResource configResource = new ConfigResource(ConfigResource.Type.TOPIC, TOPIC_NAME);
DescribeConfigResult configsResult = admin.describeConfigs(Collections.singleton(configResource));
Config configs = configResult.all().get().get(configResource);

// 기본값이 아닌 설정을 출력한다.
configs.entries().stream().filter(entry -> !entry.isDefault()).forEach(System.out::println);

// 토픽에 압착 설정이 되어 있는지 확인한다.
ConfigEntry compaction = new ConfigEntry(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_COMPACT);

if(!configs.entries().contains(compaction)) {
    // 토픽에 압착 설정이 되어있지 않을 경우 해준다.
    Collection<AlterConfigOp> configOp = new ArrayList<AlterConfigOp>();
    configOp.add(new AlterConfigOp(compaction, AlterConfigOp.OpType.SET));
    Map<ConfigResource, Collection<AlterConfigOp>> alterConf = new HashMap<>();
    alterConf.put(configResource, configOp);
    admin.incrementalAlterConfigs(alterConf).all().get();
} else {
    System.out.println("Topic "+ TOPIC_NAME + " is compacted topic");
}
```

- describeConfigs의 결과물은 ConfigResource을 키로, 설정값의 모음을 밸류로 갖는 맵이다.
- 각 설정 항목은 해당 값이 기본값에서 변경되었는지를 확인할 수 있게 해주는 isDefault() 메서드를 갖는다.
- 토픽 설정이 기본값이 아닌것으로 취급되는 경우는 다음과 같다.
1. 사용자가 토픽 설정이 기본값이 아닌 것으로 잡아준 경우
2. 브로커 단위 설정이 수정된 상태에서 토픽이 생성되어 기본값이 아닌 값을 브로커 설정으로 부터 상속받았을 경우
- 설정을 변경하고 싶다면, 변경하고자 하는 ConfigResource을 키로, 바꾸고자 하는 설정값 모음을 밸류로 하는 맵을 지정한다.
- 각각의 설정 변경 작업은 설정 항목과 작업 유형으로 이루어진다.
- 카프카에서는 4가지 형태의 설정 변경이 가능하다. 
- 즉 설정값을 잡아주는 SET, 현재 설정값을 삭제하고 기본값으로 되돌리는 DELETE, 그리고 APPEND와 SUBSTRACT가 바로 그것이다.
- 마지막 두개는 목록 형태의 설정에만 사용이 가능한데, 이걸 사용하면 전체 목록을 주고받을 필요없이 필요한 설정만 추가하거나 삭제할 수 있다.
- 업그레이드 실수를 하다가 브로커 설정 파일이 깨진 경우, AdminClient 를 사용해서 아직 남아있는 브로커 중 하나의 설정값을 통쨰로 덤프를 뜬 덕분에 위기를 넘기는 사례가 있다.

### 5.5 컨슈머 그룹 관리

- AdminClient를 사용해서 프로그램적으로 컨슈머 그룹과 이 그룹들이 커밋한 오프셋을 조회하고 수정하는 방법에 대해서 살펴볼 것이다.

#### 5.5.1 컨슈머 그룹 살펴보기

> 컨슈머 그룹의 목록을 조회하기
```
admin.listConsumerGroups().valid().get().forEach(System.out::println);
```
- 주의할 점은 valid() 메서드, get() 메서드를 호출함으로써 리턴되는 모음은 큵러스터가 에러 없이 리턴한 컨슈머 그룹만을 포함한다는 점이다.
- 이 과정에서 발생한 에러가 예외의 형태로 발생하지는 않는데, errors() 메서드를 사용해서 모든 예외를 가져올 수 있다.

> 특정 그룹에 대해 더 상세한 정보를 조회하기
 ```
ConsumerGroupDescription groupDescription = admin.describeConsumerGroups(CONSUMER_GRP_LIST).describedGroups().get(CONSUMER_GROUP).get();
System.out.println("Description of group " + CONSUMER_GROUP + ":" + groupDescription);
```
- description은 해당 그룹에 대한 상세한 정보를 담는다.
- 여기에는 그룹 멤버와 멤버별 식별자와 호스트명, 멤버별로 할당된 파티션, 할당 알고리즘, 그룹 코디네이터의 호스트명이 포함된다.
- 우리는 컨슈머 그룹이 읽고 있는 각 파티션에 대해 마지막으로 커밋된 오프셋 값이 무엇인지, 최신 메시지에서 얼마나 뒤떨어졌는지(랙lag)를 알고 싶을 것이다.
- 이러한 정보를 얻어올 수 있는 유일한 방법이 컨슈머 그룹이 카프카 내부 토픽에 쓴 커밋 메시지를 가져와서 파싱하는 것뿐이었다. -> 제대로 호환성 보장을 하지 않아 권장하지 않음

> 커밋 정보 얻어오기
```
Map<TopicPartition, OffsetAndMetadata> offsets = admin.listConsumerGroupOffsets(CONSUMER_GROUP).partitionsToOffsetAndMetadata().get();
Map<TopicPartition, OffsetSpec> requestLatestOffsets = new HashMap<>();

for(TopicPartition tp: offsets.keySet()) {
    requestLastestOffsets.put(tp, OffsetSpec.latest());
}

Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> latestOffsets = admin.listOffsets(requestLatestOffsets).all().get();

for (Map.Entry<TopicPartition, OffsetAndMetadata> e: offsets.entrySet()) {
    String topic = e.getKey().topic();
    int partition = e.getKey().partition();
    long committedOffset = e.getValue().offset();
    long latestOffset = latestOffsets.get(e.getKey()).offset();
    System.out.println("lag : " + (latestOffset - committedOffset));
}
```
- 컨슈머 그룹이 사용중인 모든 토픽 파티션을 키로, 각각으 토픽 파티션에 대해 마지막으로 커밋된 오프셋을 벨류로 하는 맵을 가져온다.
- describeConsumerGroups 와는 달리 listConsumerGroupOffsets 는 컨슈머 그룹의 모음이 아닌 하나의 컨슈머 그룹을 받는다는 점을 유념해라.
- 모든 파티션을 반복해서 각각의 파티션에 대해 마지막으로 커밋된 오프셋, 파티션의 마지막 오프셋, 둘 사이의 랙을 구할 수 있다.

#### 5.5.2 컨슈머 그룹 수정하기

- AdminClient는 컨슈머 그룹을 수정하기 위한 메서드들 역시 가지고 있다.
- 그룹 삭제, 멤버 제외, 커밋된 오프셋 삭제 혹은 변경 등..
  ( 자세한 예제는 관심있다면 더 보는게 나을듯.. )

### 5.6 클러스터 메타데이터

- 클러스터에 대한 정보를 명시적으로 읽어와야 하는 경우는 드물다.
- 얼마나 많은 브로커가 있는지, 어느 브로커가 컨트롤러인지 알 필요 없이 메시지를 읽거나 쓸 수 있기 때문이다.
  (관심있으면 더 보시길..)

### 5.7 고급 어드민 작업

- 토픽에 파티션 추가하기
- 토픽에 레코드 삭제하기
- 리더 선출
- 레플리카 재할당

AdminClient 를 활용하여 다양한 작업을 할 수 있으며, 더 자세한 내용은 필요에 따라 각자 보면 될 것 같다.

