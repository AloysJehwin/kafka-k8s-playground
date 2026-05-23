package com.eventflow.webbff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WebBffApplication {
    public static void main(String[] args) {
        SpringApplication.run(WebBffApplication.class, args);
    }
}
