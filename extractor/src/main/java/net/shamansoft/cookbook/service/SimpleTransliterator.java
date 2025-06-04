package net.shamansoft.cookbook.service;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Map;


@Service
public class SimpleTransliterator implements Transliterator {

    private static final Map<String, String> SYMBOL_MAPPING = Map.ofEntries(
            // Russian
            Map.entry("а", "a"), Map.entry("б", "b"), Map.entry("в", "v"),
            Map.entry("г", "g"), Map.entry("д", "d"), Map.entry("е", "e"),
            Map.entry("ё", "yo"), Map.entry("ж", "zh"), Map.entry("з", "z"),
            Map.entry("и", "i"), Map.entry("й", "y"), Map.entry("к", "k"),
            Map.entry("л", "l"), Map.entry("м", "m"), Map.entry("н", "n"),
            Map.entry("о", "o"), Map.entry("п", "p"), Map.entry("р", "r"),
            Map.entry("с", "s"), Map.entry("т", "t"), Map.entry("у", "u"),
            Map.entry("ф", "f"), Map.entry("х", "kh"), Map.entry("ц", "ts"),
            Map.entry("ч", "ch"), Map.entry("ш", "sh"), Map.entry("щ", "shch"),
            Map.entry("ъ", ""), Map.entry("ы", "y"), Map.entry("ь", ""),
            Map.entry("э", "e"), Map.entry("ю", "yu"), Map.entry("я", "ya"),
            // Georgian basics
            Map.entry("ა", "a"), Map.entry("ბ", "b"), Map.entry("გ", "g"),
            Map.entry("დ", "d"), Map.entry("ე", "e"), Map.entry("ვ", "v"),
            Map.entry("ზ", "z"), Map.entry("ი", "i"), Map.entry("კ", "k"),
            Map.entry("ლ", "l"), Map.entry("მ", "m"), Map.entry("ნ", "n"),
            Map.entry("ო", "o"), Map.entry("პ", "p"), Map.entry("რ", "r"),
            Map.entry("ს", "s"), Map.entry("ტ", "t"), Map.entry("უ", "u"),
            Map.entry("ხ", "kh"), Map.entry("ჩ", "ch"), Map.entry("შ", "sh")
    );

    @Override
    public String toAsciiKebab(String input) {

        if(input == null || input.isBlank()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        String lower = input.toLowerCase();

        for (int i = 0; i < lower.length(); i++) {
            String ch = String.valueOf(lower.charAt(i));
            String transliterated = SYMBOL_MAPPING.getOrDefault(ch, ch);
            result.append(transliterated);
        }

        String normalized = Normalizer.normalize(result, Normalizer.Form.NFD);
        String ascii = normalized.replaceAll("[^\\p{ASCII}]", "");
        String kebab = ascii.replaceAll("[^a-z0-9]+", "-");
        if(kebab.endsWith("-")) kebab = kebab.substring(0, kebab.length() - 1);
        return kebab;
    }
}
