I hear you loud and clear. This has been a significant friction point in the ecosystem since `RestTemplate` went into
maintenance mode. For a long time, the official advice was "use WebClient," which forced developers to pull in the
entire Netty/Reactor stack (`spring-boot-starter-webflux`) just to call `.block()` on every request. That is unnecessary
overhead for a synchronous application.

As of **Spring Boot 3.2 (Spring Framework 6.1)**, there is a dedicated solution that fits your exact requirements.

### The Solution: `RestClient`

You should use **`RestClient`**.

It was introduced specifically to solve your problem: it offers the modern, fluent, functional API of `WebClient`, but
it runs on the **synchronous Servlet stack**. It does **not** require Project Reactor or Netty.

### Why this is the correct architectural choice

1. **Zero Reactive Dependencies:** It uses the standard `spring-boot-starter-web`. You do not need to add
   `spring-boot-starter-webflux`.
2. **Fluent API:** You get the readable `body(..)` and `retrieve(..)` chaining syntax you likely envy from WebClient.
3. **Backwards Compatible:** Under the hood, it uses the same `ClientHttpRequestFactory` abstraction as `RestTemplate`.
   This means your existing configuration for timeouts, SSL, and connection pooling (using Apache HttpClient or Jetty)
   still applies.

---

### Production-Ready Implementation

Don't just `new RestClient()` and ship it. In a high-scale environment, you need a robust underlying HTTP client
implementation to handle connection pooling (critical for avoiding port exhaustion in GCP).

Here is how I configure it for production systems using Apache HttpComponents 5:

**1. Dependencies (`pom.xml` / `build.gradle`)**
Ensure you have the standard web starter and the Apache HTTP client for pooling support.

```xml

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
<groupId>org.apache.httpcomponents.client5</groupId>
<artifactId>httpclient5</artifactId>
</dependency>

```

**2. The Configuration (Pro-Level)**
This setup ensures you aren't using the default Java `HttpURLConnection` (which has poor performance characteristics for
high throughput).

```java
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient restClient(RestClient.Builder builder) {
        // 1. Create a Connection Manager with pooling
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(200); // Total connections across all routes
        connectionManager.setDefaultMaxPerRoute(20); // Max connections per specific host

        // 2. Build the Apache Client
        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .build();

        // 3. Configure the Factory with Timeouts (Crucial for Resiliency)
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setConnectionRequestTimeout(Duration.ofSeconds(2));

        // 4. Build the RestClient
        return builder
                .requestFactory(factory)
                .baseUrl("https://api.internal-service.local") // Base URL for microservices
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}

```

**3. Usage Example (Clean & Synchronous)**

```java

@Service
public class InventoryService {

    private final RestClient restClient;

    public InventoryService(RestClient restClient) {
        this.restClient = restClient;
    }

    public InventoryStatus checkStock(String sku) {
        return restClient.get()
                .uri("/inventory/{sku}", sku)
                .retrieve()
                .onStatus(status -> status.value() == 404, (request, response) -> {
                    throw new StockNotFoundException("Item not found: " + sku);
                })
                .body(InventoryStatus.class); // Automatically deserializes JSON
    }
}

```

---

### Principal Engineer's "Caveats & Tips"

**1. The "Under the Hood" Trap**
By default, if you don't configure a request factory (like the Apache one above), `RestClient` falls back to the JDK's
standard `HttpURLConnection`. This implementation does not support advanced features like `PATCH` requests properly in
older JDKs and has very basic connection handling. Always inject a `HttpComponentsClientHttpRequestFactory` or
`JettyClientHttpRequestFactory` for production workloads.

**2. GraalVM & Native Images**
Since you are using GraalVM, `RestClient` is fully supported and generally works better than the old `RestTemplate`
regarding reflection hints, provided you are using the Jackson integration for JSON.

* **Tip:** If you use the **Declarative Client** (interfaces annotated with `@GetExchange`, similar to Feign but
  official), be sure to enable the `-parameters` compiler flag so Spring can resolve parameter names without reflection
  overhead.

**3. Infrastructure Note (GCP Cloud Run)**
If you are deploying this to Cloud Run:

* Ensure your connection pool `MaxTotal` isn't set absurdly high, or you might hit memory limits on smaller container
  instances.
* The `Keep-Alive` settings in the underlying Apache client are vital. Cloud Run creates a barrier between the internet
  and your container; aggressive connection recycling can cause 502s during scale-down events.

**4. Declarative Clients (The "New Feign")**
If you liked OpenFeign but want to stay "Spring Native," you can use `RestClient` to back a declarative interface:

```java
// Define the interface
interface InventoryClient {
    @GetExchange("/inventory/{sku}")
    InventoryStatus checkStock(@PathVariable String sku);
}

// Wire it up in Config
@Bean
InventoryClient inventoryClient(RestClient restClient) {
    return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
            .build()
            .createClient(InventoryClient.class);
}

```

This is cleaner than Feign and uses the exact same `RestClient` bean we configured above.

### Summary

Stop using `RestTemplate` (it's dying). Do not use `WebClient` (it's bloated for your use case). Use **`RestClient`**
with **Apache HttpComponents**.