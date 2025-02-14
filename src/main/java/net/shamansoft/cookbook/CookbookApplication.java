package net.shamansoft.cookbook;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.annotation.Order;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@SpringBootApplication
public class CookbookApplication implements ApplicationRunner {

	@Option(names = {"-u", "--url"}, description = "URL to process", required = true)
	private String url;

	@Option(names = {"-o", "--out"}, description = "Output file name", required = true)
	private String output;

	public static void main(String[] args) {
		int exitCode = new CommandLine(new CookbookApplication()).execute(args);
		System.exit(exitCode);
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
