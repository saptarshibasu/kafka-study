package com.sapbasu.kafkademo.controller;

import com.sapbasu.Employee;
import com.sapbasu.kafkademo.dto.EmployeeDto;
import com.sapbasu.kafkademo.dto.RecordMetadataDto;
import com.sapbasu.kafkademo.endpoint.ProducerEndpoint;
import com.sapbasu.kafkademo.exception.KafkaDemo400Exception;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.concurrent.Executor;

@RestController
@Slf4j
public class ProducerController implements ProducerEndpoint {

    private final Producer<String, Employee> employeeProducer;
    private final Executor commonThreadPool;
    private final String topic;

    @Autowired
    public ProducerController(Producer<String, Employee> employeeProducer,
                              Executor commonThreadPool,
                              @Value("${employee.topic}") String topic) {
        this.employeeProducer = employeeProducer;
        this.commonThreadPool = commonThreadPool;
        this.topic = topic;
    }

    @Override
    public DeferredResult<RecordMetadataDto> produceEmployee(EmployeeDto employeeDto) {

        if (employeeDto.getEmployeeId() == null) {
            throw new KafkaDemo400Exception("EmployeeId cannot be null");
        }

        Employee employee = getEmployee(employeeDto);
        ProducerRecord<String, Employee> producerRecord = new ProducerRecord<>(topic, employee.getEmployeeId(), employee);
        DeferredResult<RecordMetadataDto> deferredResult = new DeferredResult<>();
        commonThreadPool.execute(new EmployeePublisher(employeeProducer, producerRecord, deferredResult, employeeDto.getEmployeeId()));
        log.info("Task submitted for employeeId: {}", employeeDto.getEmployeeId());
        return deferredResult;
    }

    private Employee getEmployee(EmployeeDto employeeDto) {

        Employee.Builder employeeBuilder = Employee.newBuilder();
        employeeBuilder.setEmployeeId(employeeDto.getEmployeeId());
        employeeBuilder.setFirstName(employeeDto.getFirstName());
        employeeBuilder.setLastName(employeeDto.getLastName());
        employeeBuilder.setAge(employeeDto.getAge());
        employeeBuilder.setPhoneNumber(employeeDto.getPhoneNumber());
        employeeBuilder.setEmail(employeeDto.getEmail());

        return employeeBuilder.build();
    }

    private class EmployeePublisher implements Runnable {

        private final Producer<String, Employee> employeeProducer;
        private final ProducerRecord<String, Employee> producerRecord;
        private final DeferredResult<RecordMetadataDto> deferredResult;
        private final String employeeId;

        public EmployeePublisher(Producer<String, Employee> employeeProducer,
                                 ProducerRecord<String, Employee> producerRecord,
                                 DeferredResult<RecordMetadataDto> deferredResult,
                                 String employeeId) {
            this.employeeProducer = employeeProducer;
            this.producerRecord = producerRecord;
            this.deferredResult = deferredResult;
            this.employeeId = employeeId;
        }

        @Override
        public void run() {
            try {
                RecordMetadata recordMetadata = employeeProducer.send(producerRecord).get();
                RecordMetadataDto recordMetadataDto = new RecordMetadataDto(
                        recordMetadata.offset(),
                        recordMetadata.timestamp(),
                        recordMetadata.topic(),
                        recordMetadata.partition());
                log.info("Successfully published employeeId {} in Kafka with offset: {}, topic: {}, partition: {}",
                        employeeId, recordMetadataDto.getOffset(), recordMetadataDto.getTopic(), recordMetadataDto.getPartition());
                deferredResult.setResult(recordMetadataDto);
            } catch (Exception e) {
                deferredResult.setErrorResult(e);
            }
        }
    }
}
