import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import java.time.Duration
import java.util.*
import java.util.regex.Pattern

/**
 * 설명: 리밸런스 리스너
 *
 * @author 유한욱(Doyle) / hanwook.ryu@dreamus.io
 * @since 2024/12/09
 */

val currentOffsets = HashMap<TopicPartition, OffsetAndMetadata>()
var consumer = makeKafkaConsumer("group2")

fun main() {
    consumer.subscribe(Pattern.compile("kafka.*"), HandleRebalance())

    while (true) {
        val records = consumer.poll(Duration.ofMillis(100))

        records.forEach { record ->
            println("topic = ${record.topic()}, partition = ${record.partition()}, offset = ${record.offset()}, key = ${record.key()}, value = ${record.value()}")
            currentOffsets[TopicPartition(record.topic(), record.partition())] = OffsetAndMetadata(record.offset() + 1, "no metadata")
            consumer.commitAsync()
        }
    }
}

class HandleRebalance : ConsumerRebalanceListener {
    override fun onPartitionsRevoked(partitions: MutableCollection<TopicPartition>?) {
        println("Lost partitions in rebalance. Committing current offsets: $currentOffsets")
        consumer.commitSync(currentOffsets)
    }

    override fun onPartitionsAssigned(partitions: MutableCollection<TopicPartition>?) {}
}

private fun makeKafkaConsumer(groupName: String): KafkaConsumer<String, String> {
    val properties = Properties()

    properties[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = "localhost:9092,localhost:9093,localhost:9094"
    properties[ConsumerConfig.GROUP_ID_CONFIG] = groupName // consumer group
    properties[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = "org.apache.kafka.common.serialization.StringDeserializer"
    properties[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = "org.apache.kafka.common.serialization.StringDeserializer"

    return KafkaConsumer<String, String>(properties)
}