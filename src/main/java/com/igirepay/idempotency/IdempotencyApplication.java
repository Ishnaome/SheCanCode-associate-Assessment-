package com.igirepay.idempotency;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IdempotencyApplication {
    

    public static void main(String[] args) {
        SpringApplication.run(IdempotencyApplication.class, args);
    }
}
// thi my application java file for the idempotency gateway. It is the entry point of the Spring Boot application. The @SpringBootApplication annotation indicates that this is a Spring Boot application, and the @EnableScheduling annotation allows us to use scheduled tasks in our application, which can be useful for cleaning up old idempotency records from the cache. 