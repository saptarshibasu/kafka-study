package com.sapbasu.kafkademo.dto;

import lombok.Data;

@Data
public class EmployeeDto {
    private final String employeeId;
    private final String firstName;
    private final String lastName;
    private final Integer age;
    private final String phoneNumber;
    private final String email;

    public EmployeeDto(String employeeId, String firstName, String lastName, Integer age, String phoneNumber, String email) {
        this.employeeId = employeeId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.age = age;
        this.phoneNumber = phoneNumber;
        this.email = email;
    }
}
