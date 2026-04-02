package com.server.contestControl;

import com.server.contestControl.authServer.startup.AdminBootstrapProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AdminBootstrapProperties.class)
public class AuraServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(AuraServerApplication.class, args);
	}

}
