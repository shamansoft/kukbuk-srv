package net.shamansoft.cookbook;

import lombok.RequiredArgsConstructor;
import net.shamansoft.cookbook.service.DownloadService;
import net.shamansoft.cookbook.service.StoreService;
import net.shamansoft.cookbook.service.Transformer;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import java.util.concurrent.Callable;

@Command(name = "cookbook", mixinStandardHelpOptions = true, version = "1.0")
@Component
@RequiredArgsConstructor
public class CookbookCli implements Callable<Integer> {

    private final DownloadService downloadService;
    private final Transformer transformer;
    private final StoreService storeService;

    @Option(names = {"-u", "--url"}, description = "URL to process", required = true)
    private String url;

    @Option(names = {"-o", "--out"}, description = "Output file name", required = true)
    private String outFile;

    @Override
    public Integer call() throws Exception {
        System.out.println("Processing " + url);
        String raw = downloadService.fetch(url);
        String content = transformer.transform(raw);
        storeService.store(content, outFile);
        return 0;
    }
}