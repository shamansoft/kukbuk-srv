package net.shamansoft.cookbook;

import net.shamansoft.cookbook.dto.RecipeResponse;
import net.shamansoft.cookbook.dto.Request;
import net.shamansoft.cookbook.service.Compressor;
import net.shamansoft.cookbook.service.DriveService;
import net.shamansoft.cookbook.service.RawContentService;
import net.shamansoft.cookbook.service.TokenService;
import net.shamansoft.cookbook.service.Transformer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import net.shamansoft.cookbook.service.GoogleDriveRestService;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CookbookControllerSpringTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @MockitoBean
    private RawContentService rawContentService;

    @MockitoBean
    private Transformer transformer;

    @MockitoBean
    private Compressor compressor;
    @MockitoBean
    private DriveService googleDriveService;
    @MockitoBean
    private TokenService tokenService;

    @Test
    void healthEndpointReturnsOk() {
        ResponseEntity<String> response = restTemplate.getForEntity("/", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("OK");
    }

    @Test
    void helloEndpointReturnsGreetingWithName() {
        ResponseEntity<String> response = restTemplate.getForEntity("/hello/John", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("Hello, Cookbook user John!");
    }

    @Test
    void createRecipeFromCompressedHtml() throws IOException {
        Request request = new Request("compressed html", "Title", "http://example.com");
        when(compressor.decompress("compressed html")).thenReturn("raw html");
        when(transformer.transform("raw html")).thenReturn("transformed content");

        ResponseEntity<RecipeResponse> response = restTemplate.postForEntity(
                "/recipe",
                request,
                RecipeResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .extracting(RecipeResponse::title, RecipeResponse::url, RecipeResponse::content)
                .containsExactly("Title", "http://example.com", "transformed content");
    }

    @Test
    void createRecipeFromUrl() throws IOException {
        Request request = new Request(null, "Title", "http://example.com");
        when(rawContentService.fetch("http://example.com")).thenReturn("raw html");
        when(transformer.transform("raw html")).thenReturn("transformed content");

        ResponseEntity<RecipeResponse> response = restTemplate.postForEntity(
                "/recipe",
                request,
                RecipeResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .extracting(RecipeResponse::title, RecipeResponse::url, RecipeResponse::content)
                .containsExactly("Title", "http://example.com", "transformed content");
    }

    @Test
    void invalidRequestReturnsBadRequest() {
        Request request = new Request(null, null, null);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/recipe",
                request,
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).startsWith("Validation error:");
    }
}