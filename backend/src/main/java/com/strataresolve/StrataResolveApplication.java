package com.strataresolve;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StrataResolveApplication {

    public static void main(String[] args) {
        SpringApplication.run(StrataResolveApplication.class, args);
    }
}
