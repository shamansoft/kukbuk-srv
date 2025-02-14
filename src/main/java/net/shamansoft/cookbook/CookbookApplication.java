package net.shamansoft.cookbook;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.annotation.Order;

@SpringBootApplication
public class CookbookApplication implements ApplicationRunner {

	@Value("${url}")
	private String url;

	@Value("${out}")
	private String output;

	public static void main(String[] args) {
		SpringApplication.run(CookbookApplication.class, args);
	}

	@Override
	@Order(1)
	public void run(ApplicationArguments args) throws Exception {
		// Print application startup message
		System.out.println("Cookbook Application Started!");
		System.out.println("URL: " + url);
		System.out.println("Output: " + output);
		
		// Example logic: Process the URL and write to output
		// You would add your business logic here
	}
}
