package net.shamansoft.cookbook.html;

import java.io.IOException;

public interface UrlContentFetcher {
    String fetch(String url) throws IOException;
}
