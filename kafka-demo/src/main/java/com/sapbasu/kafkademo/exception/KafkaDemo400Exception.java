package com.sapbasu.kafkademo.exception;

public class KafkaDemo400Exception extends RuntimeException {

    public KafkaDemo400Exception() {
        super();
    }

    public KafkaDemo400Exception(Throwable cause) {
        super(cause);
    }

    public KafkaDemo400Exception(String message) {
        super(message);
    }

    public KafkaDemo400Exception(String message, Throwable cause) {
        super(message, cause);
    }
}
