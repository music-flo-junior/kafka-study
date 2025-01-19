import org.apache.kafka.common.serialization.Serdes
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.StreamsBuilder
import org.apache.kafka.streams.StreamsConfig
import org.apache.kafka.streams.kstream.KStream
import java.util.Arrays
import java.util.Properties
import java.util.regex.Pattern

fun main() {
    val wordCountConfig = makeStreamsConfig("word-count")
    val builder = wordCountStreams()

    val kafkaStreams = KafkaStreams(builder.build(), wordCountConfig)

    kafkaStreams.start()

    Thread.sleep(5000L)

    kafkaStreams.close()
}

fun makeStreamsConfig(configName: String): Properties {
    val properties = Properties()

    properties[StreamsConfig.APPLICATION_ID_CONFIG] = configName
    properties[StreamsConfig.BOOTSTRAP_SERVERS_CONFIG] = "localhost:9092"
    properties[StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG] = Serdes.String().javaClass.name
    properties[StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG] = Serdes.String().javaClass.name

    return properties
}

fun wordCountStreams(): StreamsBuilder {
    val streamsBuilder = StreamsBuilder()

    // source
    val source = streamsBuilder.stream<String, String>("wordcount-input")

    val pattern = Pattern.compile("\\W+")

    val counts = source.flatMapValues { event -> listOf(*pattern.split(event.lowercase())) }
        .map { _, value -> KeyValue(value, value) }
        .filter { _, value -> value != "the" }
        .groupByKey()
        .count()
        .mapValues { value -> value.toString() }
        .toStream()

    counts.to("wordcount-output")

    return streamsBuilder
}