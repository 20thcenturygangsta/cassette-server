package com.playlist.cassette;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;


@SpringBootApplication
@EnableFeignClients
public class CassetteApplication {

	public static void main(String[] args) {
		SpringApplication.run(CassetteApplication.class, args);
	}

}
