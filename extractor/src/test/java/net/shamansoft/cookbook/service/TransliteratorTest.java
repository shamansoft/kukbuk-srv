package net.shamansoft.cookbook.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TransliteratorTest {

    private final Transliterator transliterator = new SimpleTransliterator();

    @Test
    void returnsEmptyStringForNullInput() {
        SimpleTransliterator transliterator = new SimpleTransliterator();
        String result = transliterator.toAsciiKebab(null);
        assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyStringForBlankInput() {
        SimpleTransliterator transliterator = new SimpleTransliterator();
        String result = transliterator.toAsciiKebab("   ");
        assertThat(result).isEmpty();
    }

    @Test
    void convertsCyrillicToAsciiKebabCase() {
        SimpleTransliterator transliterator = new SimpleTransliterator();
        String result = transliterator.toAsciiKebab("Хачапури по Мегрельски");
        assertThat(result).isEqualTo("khachapuri-po-megrelski");
    }

    @Test
    void handlesSpecialCharactersAndSpaces() {
        SimpleTransliterator transliterator = new SimpleTransliterator();
        String result = transliterator.toAsciiKebab("Hello, World! 123");
        assertThat(result).isEqualTo("hello-world-123");
    }

    @Test
    void removesAccentsFromLatinCharacters() {
        SimpleTransliterator transliterator = new SimpleTransliterator();
        String result = transliterator.toAsciiKebab("Café au lait");
        assertThat(result).isEqualTo("cafe-au-lait");
    }

    @Test
    void handlesEmptyStringInput() {
        SimpleTransliterator transliterator = new SimpleTransliterator();
        String result = transliterator.toAsciiKebab("");
        assertThat(result).isEmpty();
    }

    @Test
    void handlesInputWithOnlyNonAsciiCharacters() {
        SimpleTransliterator transliterator = new SimpleTransliterator();
        String result = transliterator.toAsciiKebab("你好世界");
        assertThat(result).isEmpty();
    }

    @Test
    void handlesInputWithMixedAsciiAndNonAsciiCharacters() {
        SimpleTransliterator transliterator = new SimpleTransliterator();
        String result = transliterator.toAsciiKebab("Hello 世界");
        assertThat(result).isEqualTo("hello");
    }
}