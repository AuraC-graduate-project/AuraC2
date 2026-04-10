package com.server.contestControl;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class JwtAuthServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(JwtAuthServerApplication.class, args);
	}

}
