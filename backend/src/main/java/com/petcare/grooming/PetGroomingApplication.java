package com.petcare.grooming;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PetGroomingApplication {
    public static void main(String[] args) {
        SpringApplication.run(PetGroomingApplication.class, args);
    }
}
