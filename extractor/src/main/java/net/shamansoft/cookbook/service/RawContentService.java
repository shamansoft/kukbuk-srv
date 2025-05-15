package net.shamansoft.cookbook.service;

import org.springframework.stereotype.Service;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;

@Service
public class RawContentService {

    public String fetch(String url) throws IOException {
        Document doc = Jsoup.connect(url).get();
        Elements scripts = doc.getElementsByTag("script");
        for (Element script : scripts) {
            script.remove();
        }
        return doc.body().html();
    }
}
