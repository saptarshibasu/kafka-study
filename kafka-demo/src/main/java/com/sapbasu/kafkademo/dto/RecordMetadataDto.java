package com.sapbasu.kafkademo.dto;

import lombok.Data;

@Data
public class RecordMetadataDto {
    private final long offset;
    private final long timestamp;
    private final String topic;
    private final int partition;

    public RecordMetadataDto(long offset, long timestamp, String topic, int partition) {
        this.offset = offset;
        this.timestamp = timestamp;
        this.topic = topic;
        this.partition = partition;
    }

}
