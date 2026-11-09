package com.example.pairup;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class PairUpApplication {

	public static void main(String[] args) {
		SpringApplication.run(PairUpApplication.class, args);
	}

}
