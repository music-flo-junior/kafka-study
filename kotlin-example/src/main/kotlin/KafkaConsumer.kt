import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import java.time.Duration
import java.util.*
import java.util.concurrent.Executors
import java.util.regex.Pattern

/**
 * 설명: 카프카 컨슈머 생성하기
 *
 * @author 유한욱(Doyle) / hanwook.ryu@dreamus.io
 * @since 2024/12/09
 */
@Volatile
var closing = false

fun main() {
    val executor = Executors.newFixedThreadPool(3)

    (0 until 3).asSequence().forEach { _ ->
        executor.execute {
            subscribeTopic().use { consumer ->
                runPollingLoop(consumer)
            }
        }
    }

    Thread.sleep(10000)
    executor.shutdown()
}

private fun runPollingLoop(consumer: KafkaConsumer<String, String>) {
    while (!closing) {
        val records = consumer.poll(Duration.ofMillis(100))

        records.forEach { record ->
            println("topic = ${record.topic()}, partition = ${record.partition()}, offset = ${record.offset()}, key = ${record.key()}, value = ${record.value()}")

            recordOffsetCommitAsync(record, consumer)

            currentOffsetCommitAsync(consumer)
        }
    }

    currentOffsetCommitSync(consumer)
}

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

private fun currentOffsetCommitSync(consumer: KafkaConsumer<String, String>) {
    consumer.commitSync()
}

private fun currentOffsetCommitAsync(consumer: KafkaConsumer<String, String>) {
    consumer.commitAsync { _, e ->
        if (e != null) {
            throw e
        }
    }
}

private fun subscribeTopic(): KafkaConsumer<String, String> {
    val consumer = makeKafkaConsumer("group1")
    consumer.subscribe(Pattern.compile("kafka.*"))

    return consumer
}

private fun makeKafkaConsumer(groupName: String): KafkaConsumer<String, String> {
    val properties = Properties()

    properties[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = "localhost:9092,localhost:9093,localhost:9094"
    properties[ConsumerConfig.GROUP_ID_CONFIG] = groupName // consumer group
    properties[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = "org.apache.kafka.common.serialization.StringDeserializer"
    properties[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = "org.apache.kafka.common.serialization.StringDeserializer"

    return KafkaConsumer<String, String>(properties)
}