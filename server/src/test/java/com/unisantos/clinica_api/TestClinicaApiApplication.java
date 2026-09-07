package com.unisantos.clinica_api;

import org.springframework.boot.SpringApplication;

public class TestClinicaApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(ClinicaApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
