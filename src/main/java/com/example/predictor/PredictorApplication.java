package com.example.predictor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PredictorApplication {

    public static void main(String[] args) {
        SpringApplication.run(PredictorApplication.class, args);
    }
}
