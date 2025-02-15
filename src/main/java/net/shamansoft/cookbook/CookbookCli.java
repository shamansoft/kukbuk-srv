package net.shamansoft.cookbook;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import java.util.concurrent.Callable;

@Command(name = "cookbook", mixinStandardHelpOptions = true, version = "1.0")
@Component
@RequiredArgsConstructor
public class CookbookCli implements Callable<Integer> {

    private final DownloadService downloadService;

    @Option(names = {"-u", "--url"}, description = "URL to process", required = true)
    private String url;

    @Option(names = {"-o", "--out"}, description = "Output file name", required = true)
    private String output;

    @Override
    public Integer call() throws Exception {
        System.out.println("Processing " + url);
        downloadService.fetch(url);
        System.out.println("Saving to " + output);
        return 0;
    }
}