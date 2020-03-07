package com.sapbasu.kafkademo.exception;

public class KafkaDemo500Exception extends RuntimeException {

    public KafkaDemo500Exception() {
        super();
    }

    public KafkaDemo500Exception(Throwable cause) {
        super(cause);
    }

    public KafkaDemo500Exception(String message) {
        super(message);
    }

    public KafkaDemo500Exception(String message, Throwable cause) {
        super(message, cause);
    }
}
