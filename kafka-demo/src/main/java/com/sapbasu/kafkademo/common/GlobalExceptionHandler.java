package com.sapbasu.kafkademo.common;

import com.sapbasu.kafkademo.dto.ErrorResult;
import com.sapbasu.kafkademo.exception.KafkaDemo400Exception;
import com.sapbasu.kafkademo.exception.KafkaDemo500Exception;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler({KafkaDemo500Exception.class, AsyncRequestTimeoutException.class})
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResult handleInternalServerError(Throwable cause) {
        log.error("Cause of INTERNAL_SERVER_ERROR: ", cause);
        if(cause instanceof AsyncRequestTimeoutException) {
            return new ErrorResult("Timeout error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return new ErrorResult(cause.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(KafkaDemo400Exception.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResult handleBadRequest(Throwable cause) {
        log.error("Cause of BAD_REQUEST: ", cause);
        return new ErrorResult(cause.getMessage(), HttpStatus.BAD_REQUEST);
    }
}
