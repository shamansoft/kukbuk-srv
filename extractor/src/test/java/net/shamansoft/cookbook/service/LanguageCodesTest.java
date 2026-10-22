package net.shamansoft.cookbook.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LanguageCodes Tests")
class LanguageCodesTest {

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @CsvSource(value = {
            "en      | en",
            "EN      | en",
            "'  pl ' | pl",
            "en-US   | en",
            "en_GB   | en",
            "en/us   | en",
            "pt-BR   | pt",
            "zh-Hans | zh",
            "no      | no",
    }, delimiter = '|')
    void normalizesToIso6391(String code, String expected) {
        assertThat(LanguageCodes.normalize(code)).contains(expected);
    }

    @ParameterizedTest(name = "[{index}] ''{0}'' -> empty")
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "english", "eng", "e", "xx", "-en", "12"})
    void rejectsBlankAndUnknownCodes(String code) {
        assertThat(LanguageCodes.normalize(code)).isEmpty();
    }
}
