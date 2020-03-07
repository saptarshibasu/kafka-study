package com.sapbasu.kafkademo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties("kafka")
@Data
public class KafkaConfig {
    private Map<String, String> producerParams = new HashMap<>(50);
    private Map<String, String> consumerParams = new HashMap<>(50);
}
