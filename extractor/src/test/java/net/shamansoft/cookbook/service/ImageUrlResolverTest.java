package net.shamansoft.cookbook.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ImageUrlResolver Tests")
class ImageUrlResolverTest {

    private static final String SOURCE = "https://www.gordonramsay.com/gr/recipes/prsteaksandwich/";

    @ParameterizedTest(name = "[{index}] {0} against {1} -> {2}")
    @CsvSource(value = {
            // already absolute: returned as is, source not needed
            "https://cdn.site.com/a.jpg          | https://site.com/r/x/          | https://cdn.site.com/a.jpg",
            "http://cdn.site.com/a.jpg           | NULL                           | http://cdn.site.com/a.jpg",
            "HTTPS://cdn.site.com/a.jpg          | NULL                           | HTTPS://cdn.site.com/a.jpg",
            "https://cdn.site.com/my photo.jpg   | NULL                           | https://cdn.site.com/my photo.jpg",
            "'  https://cdn.site.com/a.jpg  '    | NULL                           | https://cdn.site.com/a.jpg",
            // protocol-relative: scheme from the source
            "//cdn.example.com/a.jpg             | https://site.com/r/x/          | https://cdn.example.com/a.jpg",
            "//cdn.example.com/a.jpg             | http://site.com/r/x/           | http://cdn.example.com/a.jpg",
            // root-relative: source origin + path
            "/assets/a.png                       | https://site.com/r/x/          | https://site.com/assets/a.png",
            "/assets/a.png                       | https://site.com               | https://site.com/assets/a.png",
            "/assets/a.png                       | https://site.com:8443/r/x      | https://site.com:8443/assets/a.png",
            "/assets/a.png?w=640&h=480           | https://site.com/r/x/          | https://site.com/assets/a.png?w=640&h=480",
            // page-relative: resolved against the source directory
            "images/a.png                        | https://site.com/r/x/          | https://site.com/r/x/images/a.png",
            "images/a.png                        | https://site.com/r/x           | https://site.com/r/images/a.png",
            "images/a.png                        | https://site.com               | https://site.com/images/a.png",
            "./images/a.png                      | https://site.com/r/x/          | https://site.com/r/x/images/a.png",
            "../images/a.png                     | https://site.com/r/x/          | https://site.com/r/images/a.png",
            "../../../images/a.png               | https://site.com/r/x/          | https://site.com/images/a.png",
            // source query/fragment play no part, even with characters java.net.URI rejects
            "images/a.png                        | https://site.com/r/x/?tags=a|b#top | https://site.com/r/x/images/a.png",
            // characters browsers tolerate are percent-encoded rather than rejected
            "/assets/my photo.png                | https://site.com/r/x/          | https://site.com/assets/my%20photo.png",
            "/assets/already%20encoded.png       | https://site.com/r/x/          | https://site.com/assets/already%20encoded.png",
    }, delimiterString = " | ", nullValues = "NULL")
    @DisplayName("Should resolve path to absolute URL")
    void shouldResolvePathToAbsoluteUrl(String path, String source, String expected) {
        assertThat(ImageUrlResolver.resolve(path, source)).contains(expected);
    }

    @ParameterizedTest(name = "[{index}] {0} against {1}")
    @CsvSource(value = {
            // nothing to resolve
            "NULL                                | https://site.com/r/x/",
            "''                                  | https://site.com/r/x/",
            "'   '                               | https://site.com/r/x/",
            // not loadable over http(s)
            "data:image/png;base64,iVBORw0KGgo=  | https://site.com/r/x/",
            "blob:https://site.com/1234          | https://site.com/r/x/",
            "file:///tmp/a.png                   | https://site.com/r/x/",
            // malformed
            "/images/%zz.png                     | https://site.com/r/x/",
            // relative path with no usable source
            "/assets/a.png                       | NULL",
            "/assets/a.png                       | ''",
            "/assets/a.png                       | '   '",
            "/assets/a.png                       | not a url",
            "/assets/a.png                       | www.site.com/r/x/",
            "/assets/a.png                       | ftp://site.com/r/x/",
            "/assets/a.png                       | https:///r/x/",
            "images/a.png                        | NULL",
            "//cdn.example.com/a.jpg             | NULL",
    }, delimiterString = " | ", nullValues = "NULL")
    @DisplayName("Should return empty when path cannot be resolved")
    void shouldReturnEmptyWhenPathCannotBeResolved(String path, String source) {
        assertThat(ImageUrlResolver.resolve(path, source)).isEmpty();
    }

    @Test
    @DisplayName("Should resolve the path reported from sar-kmp")
    void shouldResolveReportedPath() {
        String path = "/assets/Uploads/_resampled/CroppedFocusedImage192072050-50-Screen-Shot-2021-10-01-at-2.54.00-PM.png";

        assertThat(ImageUrlResolver.resolve(path, SOURCE))
                .contains("https://www.gordonramsay.com" + path);
    }
}
