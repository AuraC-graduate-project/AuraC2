package com.server.contestControl.submissionServer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class Judge0HttpConfig {

    @Bean
    public RestTemplate judge0RestTemplate() {
        return new RestTemplate();
    }
}
