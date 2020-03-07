package com.sapbasu.kafkademo.constant;

public enum KafkaProducerParamNames {
    BOOTSTRAP_SERVERS("bootstrap.servers"),
    KEY_SERIALIZER("key.serializer"),
    VALUE_SERIALIZER("value.serializer"),
    ACKS("acks"),
    ENABLE_IDEMPOTENCE("enable.idempotence"),
    MAX_IN_FLIGHT_REQUESTS_PER_CONN("max.in.flight.requests.per.connection"),
    BUFFER_MEMORY("buffer.memory"),
    COMPRESSION_TYPE("compression.type"),
    RETRIES("retries"),
    BATCH_SIZE("batch.size"),
    CLIENT_ID("client.id"),
    CONNECTIONS_MAX_IDLE_MS("connections.max.idle.ms"),
    DELIVERY_TIMEOUT_MS("delivery.timeout.ms"),
    LINGER_MS("linger.ms"),
    MAX_BLOCK_MS("max.block.ms"),
    MAX_REQUEST_SIZE("max.request.size"),
    SCHEMA_REGISTRY_URL("schema.registry.url");

    private String value;

    KafkaProducerParamNames(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
