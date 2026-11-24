package br.com.jotave.wannawatch;

import org.springframework.boot.SpringApplication;

public class TestWannawatchApplication {

	public static void main(String[] args) {
		SpringApplication.from(WannawatchApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
