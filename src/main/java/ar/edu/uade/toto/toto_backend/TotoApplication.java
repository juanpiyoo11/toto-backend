package ar.edu.uade.toto.toto_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class TotoApplication {

	public static void main(String[] args) {
		// Set default timezone for the entire application
		TimeZone.setDefault(TimeZone.getTimeZone("America/Argentina/Buenos_Aires"));
		SpringApplication.run(TotoApplication.class, args);
	}

}

