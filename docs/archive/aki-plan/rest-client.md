# WebClient to RestClient Migration with Virtual Threads

## 📋 Table of Contents

- [ ] **Phase 1: Dependencies (30 min)**
  - [ ] Update `gradle/libs.versions.toml` - Add httpclient5 version
  - [ ] Update `gradle/libs.versions.toml` - Remove webflux from spring-boot-starters bundle
  - [ ] Update `extractor/build.gradle.kts` - Add explicit httpclient5 dependency
  - [ ] Verify no webflux: `./gradlew dependencies | grep -i webflux`
  - [ ] Test build: `./gradlew :cookbook:build`

- [ ] **Phase 2: Configuration (1 hour)**
  - [ ] CREATE `config/RestClientConfig.java` with connection pooling
  - [ ] Add all 5 RestClient beans (gemini, auth, drive, upload, generic)
  - [ ] Add logging interceptor to geminiRestClient
  - [ ] UPDATE `application.yaml` - Add virtual threads configuration
  - [ ] DELETE or gut `ServiceConfig.java` (keep HttpExchangeRepository bean)

- [ ] **Phase 3: Service Migration (3 hours)**
  - [ ] Migrate `GeminiRestTransformer.java` (1 .block() call)
  - [ ] Migrate `GoogleDrive.java` (13 .block() calls)
  - [ ] Migrate `GoogleAuthClient.java` (2 .block() calls, form data)
  - [ ] Migrate `TokenRestService.java` (1 .block() call)
  - [ ] Migrate `UserProfileService.java` (1 .block() call, form data)
  - [ ] Verify compilation: `./gradlew :cookbook:build`

- [ ] **Phase 4: Test Migration (4 hours)**
  - [ ] Migrate `GeminiRestTransformerTest.java` mocks
  - [ ] Migrate `GoogleDriveTest.java` mocks (30+ test methods)
  - [ ] Migrate `TokenRestServiceTest.java` mocks
  - [ ] Migrate `UserProfileServiceTest.java` mocks
  - [ ] Migrate `StorageServiceTest.java` mocks
  - [ ] Run tests: `./gradlew :cookbook:test`

- [ ] **Phase 5: Validation & Testing (2 hours)**
  - [ ] Run unit tests: `./gradlew :cookbook:test`
  - [ ] Run integration tests: `./gradlew :cookbook:intTest`
  - [ ] Check coverage: `./gradlew :cookbook:checkCoverage` (≥40%)
  - [ ] Build native image: `cd extractor/scripts && ./build.sh --native --memory=12g`
  - [ ] Local Docker test with Gemini API
  - [ ] Verify no webflux in artifact

- [ ] **Phase 6: Documentation (1 hour)**
  - [ ] Update `CLAUDE.md` - Add RestClient section
  - [ ] Update `application.yaml` comments - Document virtual threads
  - [ ] Create `docs/architecture/rest-client-migration.md`
  - [ ] Remove unused WebFlux imports

---

## Overview

Migrate from Spring WebFlux's `WebClient` (reactive) to Spring Framework's `RestClient` (synchronous) and enable Java 21 virtual threads for efficient non-blocking I/O. This eliminates unnecessary reactive dependencies while maintaining performance through virtual threads.

## Objectives

1. **Remove WebFlux dependency** - Eliminate Netty and Reactor stack
2. **Use RestClient** - Modern synchronous HTTP client (Spring 6.1+)
3. **Enable virtual threads** - Efficient concurrency without reactive complexity
4. **Configure connection pooling** - Production-ready Apache HttpClient 5
5. **Maintain functionality** - Zero regression in API behavior
6. **Improve performance** - Better startup time, lower memory usage

## Current State

- **Spring Boot:** 3.4.2 ✅ (RestClient available since 3.2)
- **Java:** 21 ✅ (Virtual threads available)
- **HTTP Client:** WebClient with Netty (via spring-boot-starter-webflux)
- **WebClient beans:** 5 (gemini, auth, drive, upload, generic)
- **Blocking calls:** 16 total (all using `.block()` - defeats reactive purpose)
- **Connection pooling:** None configured (using defaults)

### Files with .block() Calls

1. `GeminiRestTransformer.java` - 1 call (line 67)
2. `GoogleDrive.java` - 13 calls (lines 48, 84, 110, 129, 141, 178, 198, 248, 289, 316, 344)
3. `GoogleAuthClient.java` - 2 calls (lines 66, 142)
4. `TokenRestService.java` - 1 call (line 27)
5. `UserProfileService.java` - 1 call (line 154)

## Implementation Plan

### Phase 1: Dependencies (30 min)

**Update dependency management:**

1. **`gradle/libs.versions.toml`** - Add Apache HttpClient 5:
```toml
[versions]
httpclient5 = "5.4.1"

[libraries]
apache-httpclient5 = { module = "org.apache.httpcomponents.client5:httpclient5", version.ref = "httpclient5" }

[bundles]
spring-boot-starters = [
    "spring-boot-starter-web",
    # REMOVE: "spring-boot-starter-webflux",
    "spring-boot-starter-actuator",
    "spring-boot-starter-validation"
]
```

2. **`extractor/build.gradle.kts`** - Explicit Apache HttpClient:
```kotlin
dependencies {
    // Spring Boot Starters (webflux removed from bundle)
    implementation(libs.bundles.spring.boot.starters) {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-logging")
    }

    // Apache HttpClient 5 for RestClient connection pooling
    implementation(libs.apache.httpclient5)
}
```

3. **Verify:** `./gradlew dependencies | grep -i webflux` → should return nothing

### Phase 2: Configuration (1 hour)

**Create RestClient configuration with connection pooling:**

1. **CREATE:** `extractor/src/main/java/net/shamansoft/cookbook/config/RestClientConfig.java`

```java
package net.shamansoft.cookbook.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@Slf4j
public class RestClientConfig {

    // Logging interceptor (replaces WebClient ExchangeFilterFunction)
    private static String hideKey(String uri) {
        return uri.replaceAll("key=([^&]{2})[^&]+", "key=$1***");
    }

    @Bean
    public ClientHttpRequestFactory httpRequestFactory() {
        // Connection pooling
        PoolingHttpClientConnectionManager connectionManager =
            new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(200);  // Total connections
        connectionManager.setDefaultMaxPerRoute(20);  // Per-host

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .build();

        // Timeouts
        HttpComponentsClientHttpRequestFactory factory =
            new HttpComponentsClientHttpRequestFactory(httpClient);
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setConnectionRequestTimeout(Duration.ofSeconds(2));

        return factory;
    }

    @Bean
    public RestClient geminiRestClient(
            @Value("${cookbook.gemini.base-url}") String baseUrl,
            ClientHttpRequestFactory requestFactory) {
        return RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(baseUrl)
                .requestInterceptor((request, body, execution) -> {
                    log.info("Request: {} {}", request.getMethod(),
                        hideKey(request.getURI().toString()));
                    return execution.execute(request, body);
                })
                .build();
    }

    @Bean
    public RestClient authRestClient(
            @Value("${cookbook.drive.auth-url}") String authUrl,
            ClientHttpRequestFactory requestFactory) {
        return RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(authUrl)
                .build();
    }

    @Bean
    public RestClient driveRestClient(
            @Value("${cookbook.drive.base-url}") String baseUrl,
            ClientHttpRequestFactory requestFactory) {
        return RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(baseUrl)
                .build();
    }

    @Bean
    public RestClient uploadRestClient(
            @Value("${cookbook.drive.upload-url}") String uploadUrl,
            ClientHttpRequestFactory requestFactory) {
        return RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(uploadUrl)
                .build();
    }

    @Bean
    public RestClient genericRestClient(ClientHttpRequestFactory requestFactory) {
        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }
}
```

2. **UPDATE:** `extractor/src/main/resources/application.yaml` - Virtual threads:

```yaml
# Virtual Threads (Java 21+)
spring:
  threads:
    virtual:
      enabled: true  # Enable for all @Async tasks

# Tomcat configuration for virtual threads
server:
  tomcat:
    threads:
      max: 10  # Low number forces virtual thread usage
      min-spare: 2
    accept-count: 100
```

3. **DELETE:** `ServiceConfig.java` WebClient beans (keep HttpExchangeRepository bean)

### Phase 3: Service Migration (3 hours)

**Migration pattern for all services:**

```java
// BEFORE (WebClient)
import org.springframework.web.reactive.function.client.WebClient;

private final WebClient geminiWebClient;

JsonNode response = geminiWebClient.post()
    .uri(url)
    .header("Content-Type", "application/json")
    .bodyValue(body)
    .retrieve()
    .bodyToMono(JsonNode.class)
    .block();  // ❌ BLOCKING

// AFTER (RestClient)
import org.springframework.web.client.RestClient;

private final RestClient geminiRestClient;

JsonNode response = geminiRestClient.post()
    .uri(url)
    .header("Content-Type", "application/json")
    .body(body)
    .retrieve()
    .body(JsonNode.class);  // ✅ NO .block() NEEDED
```

**Files to migrate:**

1. **`GeminiRestTransformer.java`:**
   - Line 11: Replace `WebClient` import → `RestClient`
   - Line 18: `WebClient geminiWebClient` → `RestClient geminiRestClient`
   - Lines 61-67: Replace `.bodyValue(body).retrieve().bodyToMono(JsonNode.class).block()`
     → `.body(body).retrieve().body(JsonNode.class)`

2. **`GoogleDrive.java`** (13 .block() calls):
   - Line 6: Replace import
   - Lines 15-16: `WebClient` → `RestClient`
   - Lines 18-21: Update constructor injection (`@Qualifier` names)
   - Lines 48, 84, 110, 129, 141, 178, 198, 248, 289, 316, 344: Remove `.block()`, use `.body()`
   - For `Map<String, Object>`: Use `new ParameterizedTypeReference<Map<String, Object>>() {}`

3. **`GoogleAuthClient.java`:**
   - Lines 12-13: Remove `WebClient` and `BodyInserters` imports
   - Add: `import org.springframework.web.client.RestClient;`
   - Line 26: `WebClient webClient` → `RestClient restClient`
   - Lines 32-33: Constructor injection (use `@Qualifier("genericRestClient")`)
   - Lines 60-66, 136-142: Remove `.body(BodyInserters.fromFormData(params))` → `.body(params)`
   - Remove `.block()`, use `.body(new ParameterizedTypeReference<Map<String, Object>>() {})`
   - Line 92: `WebClientResponseException` → `RestClientResponseException`

4. **`TokenRestService.java`:**
   - Lines 7-8: Replace imports
   - Line 18: `WebClient authWebClient` → `RestClient authRestClient`
   - Line 27: Remove `.block()`, use `.body(Map.class)`
   - Line 29: Update exception type

5. **`UserProfileService.java`:**
   - Lines 14-15: Replace imports
   - Line 28: `WebClient webClient` → `RestClient restClient`
   - Lines 148-154: Remove `BodyInserters`, `.block()`, use direct `.body()`

**Form data handling (important):**

```java
// BEFORE (WebClient)
import org.springframework.web.reactive.function.BodyInserters;

.body(BodyInserters.fromFormData(params))

// AFTER (RestClient - cleaner!)
.body(params)  // MultiValueMap accepted directly
```

### Phase 4: Test Migration (4 hours)

**Mock pattern changes:**

```java
// BEFORE (WebClient test mocking)
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Mock
private WebClient geminiWebClient;

@Mock
private WebClient.RequestBodySpec requestBodySpec;

@Mock
private WebClient.ResponseSpec responseSpec;

when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
when(responseSpec.bodyToMono(JsonNode.class)).thenReturn(Mono.just(responseNode));

// AFTER (RestClient test mocking - simpler!)
import org.springframework.web.client.RestClient;
// NO reactor imports needed

@Mock
private RestClient geminiRestClient;

@Mock
private RestClient.RequestBodySpec requestBodySpec;

@Mock
private RestClient.ResponseSpec responseSpec;

when(requestBodySpec.body(any(String.class))).thenReturn(requestHeadersSpec);
when(responseSpec.body(JsonNode.class)).thenReturn(responseNode);  // Direct value
```

**Files to update:**

1. `GeminiRestTransformerTest.java`
2. `GoogleDriveTest.java` (30+ test methods)
3. `TokenRestServiceTest.java`
4. `UserProfileServiceTest.java`
5. `StorageServiceTest.java`

**Key changes:**
- Remove all `Mono.just()` → return values directly
- Remove all `Mono.empty()` → return `null`
- Replace `.bodyValue(any())` → `.body(any(String.class))`
- Replace `.bodyToMono(...)` → `.body(...)`

### Phase 5: Validation & Testing (2 hours)

**Verification steps:**

```bash
# 1. Build verification
./gradlew :cookbook:build

# 2. Unit tests
./gradlew :cookbook:test

# 3. Integration tests
./gradlew :cookbook:intTest

# 4. Code coverage
./gradlew :cookbook:checkCoverage  # Must be ≥ 40%

# 5. Native image build
cd extractor/scripts
./build.sh --native --memory=12g

# 6. Local Docker test
./build.sh v0.9.0-test
docker run -p 8080:8080 \
  -e COOKBOOK_GEMINI_API_KEY=$COOKBOOK_GEMINI_API_KEY \
  gcr.io/kukbuk-tf/cookbook:v0.9.0-test

# 7. Verify no webflux
./gradlew dependencies | grep -i webflux  # Should be empty
```

### Phase 6: Documentation (1 hour)

1. **Update `CLAUDE.md`:**

```markdown
## HTTP Client (v0.9.0+)

**RestClient with Virtual Threads**

The application uses Spring Framework's `RestClient` for all HTTP API calls:
- Synchronous API (no reactive complexity)
- Apache HttpComponents 5 connection pooling
- Virtual threads for efficient concurrency (Java 21+)
- No Netty or Reactor dependencies

**Configuration:**
- Connection pool: MaxTotal=200, MaxPerRoute=20
- Timeouts: connect=2s, connectionRequest=2s
- Virtual threads enabled for Tomcat

**Clients:**
- `geminiRestClient` - Gemini AI API
- `driveRestClient` - Google Drive API
- `uploadRestClient` - Google Drive Upload
- `authRestClient` - Google OAuth
- `genericRestClient` - Generic HTTP calls
```

2. **Update `application.yaml` comments** - Document virtual threads
3. **Create `docs/architecture/rest-client-migration.md`** - Full migration details

## Critical Files

| File | Changes | Risk |
|------|---------|------|
| `gradle/libs.versions.toml` | Add httpclient5, remove webflux from bundle | HIGH |
| `extractor/build.gradle.kts` | Add explicit httpclient5 dependency | HIGH |
| `config/RestClientConfig.java` | **NEW** - All RestClient beans | MEDIUM |
| `ServiceConfig.java` | Delete WebClient beans, keep HttpExchangeRepository | LOW |
| `service/gemini/GeminiRestTransformer.java` | Replace WebClient, remove .block() | HIGH |
| `client/GoogleDrive.java` | 13 .block() removals | MEDIUM |
| `client/GoogleAuthClient.java` | Form data handling change | MEDIUM |
| `service/TokenRestService.java` | Simple migration | LOW |
| `service/UserProfileService.java` | Simple migration | LOW |
| `application.yaml` | Virtual threads config | LOW |
| All test files | Update mocking from WebClient to RestClient | MEDIUM |

## Risk Mitigation

### Risks

1. **Test coverage gaps** - Tests may not catch API contract changes
   - **Mitigation:** Run full test suite before/after, add error scenario tests

2. **Connection pool exhaustion** - Incorrect pooling could cause 502s
   - **Mitigation:** Conservative defaults (200/20), monitor post-deployment

3. **GraalVM compatibility** - Native build could fail
   - **Mitigation:** Test native build early, Apache HttpClient 5 is well-supported

4. **Form data handling** - Different API for form submission
   - **Mitigation:** Test OAuth flows explicitly

### Rollback Plan

If migration fails:

```bash
# Revert Git commits
git revert <migration-commit-sha>

# Redeploy previous version
gh workflow run deploy.yml --ref main^
```

## Success Criteria

✅ **Migration successful if:**
1. All unit tests pass (≥40% coverage)
2. All integration tests pass
3. Native image builds successfully
4. No webflux dependencies in artifact
5. Performance ≥ baseline (ideally better)
6. Zero production errors in first 48 hours

❌ **Rollback triggers:**
1. >5% increase in error rate
2. >20% increase in p99 latency
3. Connection pool exhaustion
4. Native image build failures

## Expected Benefits

- **Startup time:** -20% (no Netty initialization)
- **Memory usage:** -30% (no reactor overhead)
- **Latency:** -10% (direct servlet stack)
- **Throughput:** +50% (virtual threads handle concurrency better)
- **Code simplicity:** No reactive complexity, easier debugging
- **Build time:** -5% (fewer dependencies)

## Implementation Strategy

**Atomic migration (single PR) - RECOMMENDED**

- All changes in one PR
- Clean before/after state
- Full test coverage in single commit
- No feature flags or dual-path complexity

**Estimated total time: 11-12 hours**

## Virtual Threads Best Practices

**Why virtual threads?**
- Lightweight (millions vs thousands of platform threads)
- Perfect for I/O-bound workloads (Gemini, Drive, OAuth APIs)
- Simplifies synchronous code
- GraalVM native image compatible (Java 21+)

**Configuration:**
```yaml
spring.threads.virtual.enabled: true  # Enable globally
server.tomcat.threads.max: 10  # Force virtual thread usage
```

**Monitoring:**
- Track virtual thread count vs platform threads
- Monitor connection pool utilization (<50% normal load)
- Watch for thread pinning (rare with modern I/O)

## Quick Reference: Code Transformations

### Simple GET
```java
// Before
String result = webClient.get().uri("/path").retrieve().bodyToMono(String.class).block();

// After
String result = restClient.get().uri("/path").retrieve().body(String.class);
```

### POST with JSON
```java
// Before
Response r = webClient.post().uri("/path").bodyValue(json).retrieve().bodyToMono(Response.class).block();

// After
Response r = restClient.post().uri("/path").body(json).retrieve().body(Response.class);
```

### Form Data
```java
// Before
Map m = webClient.post().contentType(APPLICATION_FORM_URLENCODED)
    .body(BodyInserters.fromFormData(params)).retrieve().bodyToMono(Map.class).block();

// After
Map m = restClient.post().contentType(APPLICATION_FORM_URLENCODED)
    .body(params).retrieve().body(new ParameterizedTypeReference<Map<String,Object>>(){});
```

### Error Handling
```java
// Before
catch (WebClientResponseException e) { ... }

// After
catch (RestClientResponseException e) { ... }  // Same methods available
```
