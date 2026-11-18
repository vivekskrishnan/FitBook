package com.vivekkrishnan.fitbook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FitbookApplication {

	public static void main(String[] args) {
		SpringApplication.run(FitbookApplication.class, args);
	}

}