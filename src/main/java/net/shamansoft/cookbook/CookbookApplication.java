package net.shamansoft.cookbook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import picocli.CommandLine;

@SpringBootApplication
public class CookbookApplication {

    public static void main(String[] args) {
        int a = 1;
        var ctx = SpringApplication.run(CookbookApplication.class, args);
        int exitCode = new CommandLine(ctx.getBean(CookbookCli.class)).execute(args);
        System.exit(exitCode);
    }
}