package com.futspring.backend;

import com.futspring.backend.shared.config.TaskSchedulingConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@ImportAutoConfiguration(TaskSchedulingConfig.class)
public class FutSpringApplication {

    public static void main(String[] args) {
        SpringApplication.run(FutSpringApplication.class, args);
    }
}
