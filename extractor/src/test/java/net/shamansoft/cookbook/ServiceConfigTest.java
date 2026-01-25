package net.shamansoft.cookbook;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceConfigTest {

    @Test
    void loggingFilter_passesRequestToExchange() {
        ServiceConfig cfg = new ServiceConfig();
        var filter = cfg.loggingFilter();

        ClientRequest req = ClientRequest.create(HttpMethod.GET, URI.create("http://example.com/path?key=abcd1234&otherParam=value")).build();

        ExchangeFunction exchange = mock(ExchangeFunction.class);
        when(exchange.exchange(any())).thenReturn(Mono.empty());

        filter.filter(req, exchange).block();

        ArgumentCaptor<ClientRequest> captor = ArgumentCaptor.forClass(ClientRequest.class);
        verify(exchange).exchange(captor.capture());

        ClientRequest passed = captor.getValue();
        assertThat(passed).isNotNull();
        assertThat(passed.url()).isEqualTo(req.url());
    }

    @Test
    void webClientBeans_areCreated() {
        ServiceConfig cfg = new ServiceConfig();

        var logging = cfg.loggingFilter();
        assertThat(logging).isNotNull();

        var gemini = cfg.geminiWebClient("https://api.example", logging);
        var auth = cfg.authWebClient("https://auth.example");
        var drive = cfg.driveWebClient("https://drive.example");
        var upload = cfg.uploadWebClient("https://upload.example");
        var web = cfg.webClient();
        var repo = cfg.httpExchangeRepository();

        assertThat(gemini).isNotNull();
        assertThat(auth).isNotNull();
        assertThat(drive).isNotNull();
        assertThat(upload).isNotNull();
        assertThat(web).isNotNull();
        assertThat(repo).isNotNull();
    }
}
