package com.sapbasu.kafkademo.config;

import com.sapbasu.Employee;
import com.sapbasu.kafkademo.constant.KafkaProducerParamNames;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Map;
import java.util.Properties;

@Configuration
@Slf4j
public class EmployeeProducerConfig {

    private final KafkaConfig kafkaConfig;

    @Autowired
    public EmployeeProducerConfig(KafkaConfig kafkaConfig) {
        this.kafkaConfig = kafkaConfig;
    }

    private Properties initProperties() {

        final Map<String, String> producerParams = kafkaConfig.getProducerParams();

        final Properties properties = new Properties();

        Arrays.asList(KafkaProducerParamNames.values())
                .stream()
                .map(v -> v.getValue())
                .forEach(v -> properties.put(v, producerParams.get(v)));

        return properties;
    }

    @Bean
    public Producer<String, Employee> getEmployeeProducer() {
        return new KafkaProducer<String, Employee>(initProperties());
    }
}
