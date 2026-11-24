package br.com.jotave.wannawatch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class WannawatchApplication {

	public static void main(String[] args) {
		SpringApplication.run(WannawatchApplication.class, args);
	}

}
