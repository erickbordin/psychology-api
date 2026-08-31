package com.psychologyapi.psychologyapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PsychologyApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(PsychologyApiApplication.class, args);
		System.out.println("PROD DEPLOY :)");
	}

}
