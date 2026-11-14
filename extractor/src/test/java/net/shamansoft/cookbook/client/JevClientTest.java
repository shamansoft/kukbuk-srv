package net.shamansoft.cookbook.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class JevClientTest {

    private static final String RESPONSE = """
            {
              "model": "jev-1.14.0",
              "answers": {
                "complete": {"type": "noul", "noul": 0.93},
                "ingredients": {"type": "choice", "choice": "list", "confidence": 0.9,
                                "probabilities": {"list": 0.95, "prose": 0.04, "none": 0.01}}
              },
              "usage": {"input_tokens": 812, "output_tokens": 20}
            }
            """;

    private MockRestServiceServer server;
    private JevClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.typesafe.ai");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new JevClient(builder.build(), "jev-1.14.0");
    }

    @Test
    void ask_postsModelStateAndQuestions_andReadsAnswers() {
        server.expect(requestTo("https://api.typesafe.ai/v1/systemone"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"model": "jev-1.14.0",
                         "state": {"text": "Pancakes"},
                         "questions": {"complete": {"type": "noul", "instructions": "Complete?"}}}
                        """, org.springframework.test.json.JsonCompareMode.STRICT))
                .andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));

        JevClient.Answers answers = client.ask(Map.of("text", "Pancakes"),
                Map.of("complete", Map.of("type", "noul", "instructions", "Complete?")));

        server.verify();
        assertThat(answers.model()).isEqualTo("jev-1.14.0");
        assertThat(answers.inputTokens()).isEqualTo(812);
        assertThat(answers.noul("complete")).isCloseTo(0.93, within(1e-9));
        assertThat(answers.choice("ingredients")).isEqualTo("list");
        assertThat(answers.probabilities("ingredients"))
                .containsEntry("list", 0.95)
                .containsEntry("none", 0.01);
    }

    @Test
    void ask_httpError_throwsJevException() {
        server.expect(requestTo("https://api.typesafe.ai/v1/systemone"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.ask("text", Map.of("q", Map.of("type", "noul"))))
                .isInstanceOf(JevClient.JevException.class)
                .hasMessageContaining("TypeSafe request failed");
    }

    @Test
    void ask_responseWithoutAnswers_throwsJevException() {
        server.expect(requestTo("https://api.typesafe.ai/v1/systemone"))
                .andRespond(withSuccess("{\"model\": \"jev-1.14.0\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.ask("text", Map.of("q", Map.of("type", "noul"))))
                .isInstanceOf(JevClient.JevException.class)
                .hasMessageContaining("no answers");
    }

    @Test
    void answers_missingQuestion_throwsJevException() {
        server.expect(requestTo("https://api.typesafe.ai/v1/systemone"))
                .andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));

        JevClient.Answers answers = client.ask("text", Map.of("q", Map.of("type", "noul")));

        assertThatThrownBy(() -> answers.noul("steps"))
                .isInstanceOf(JevClient.JevException.class)
                .hasMessageContaining("steps");
        assertThatThrownBy(() -> answers.probabilities("complete"))
                .isInstanceOf(JevClient.JevException.class);
    }
}
