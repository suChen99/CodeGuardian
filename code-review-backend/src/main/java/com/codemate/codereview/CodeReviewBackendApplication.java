package com.codemate.codereview;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CodeReviewBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeReviewBackendApplication.class, args);
    }

}
