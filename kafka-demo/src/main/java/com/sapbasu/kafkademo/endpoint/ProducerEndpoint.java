package com.sapbasu.kafkademo.endpoint;

import com.sapbasu.kafkademo.dto.EmployeeDto;
import com.sapbasu.kafkademo.dto.RecordMetadataDto;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.context.request.async.DeferredResult;

@RequestMapping("/produce")
public interface ProducerEndpoint {

    @PostMapping("/employee")
    DeferredResult<RecordMetadataDto> produceEmployee(@RequestBody EmployeeDto employeeDto);
}
