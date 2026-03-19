# Integrate markdown.new as Switchable URL Content Fetcher

## Overview

Add [markdown.new](https://markdown.new) as an alternative URL fetcher for `POST /v1/recipes`, selectable via a configuration property. The original JSoup-based fetcher remains as the default.

- **Problem solved**: JSoup fetches raw HTML that requires LLM-side cleaning; markdown.new returns pre-processed markdown (~80% token reduction) that Gemini can consume directly.
- **Design**: Introduce `UrlContentFetcher` as the common abstraction. `HtmlFetcher` (JSoup) and `MarkdownNewClientImpl` both implement it. `HtmlExtractor` injects `UrlContentFetcher`. A `@ConditionalOnProperty` annotation selects the active implementation.
- **Default**: JSoup (`cookbook.fetcher.type=jsoup`). Set `cookbook.fetcher.type=markdown-new` to use markdown.new.
- **Scope**: URL-only fetching path only. Requests with HTML content are unchanged.

## Context

- **`HtmlFetcher`** — existing JSoup fetcher, `@Service`, `fetch(String url)` returns HTML body string
- **`HtmlExtractor`** — injects `HtmlFetcher` directly; returns provided HTML or delegates to fetcher
- **`RecipeService`** — calls `htmlExtractor.extractHtml(url, decompressed)`; no changes needed after this refactor
- **`RestClientConfig`** — defines Spring RestClient beans using Apache HttpClient 5 connection pool; `MarkdownNewClientImpl` needs a new bean here

## markdown.new API

- **Endpoint**: `POST https://markdown.new/`
- **Request**: `Content-Type: application/json`, body `{"url": "https://..."}`
- **Response**: plain text markdown (no auth required)
- **Rate limit**: 500 req/day per IP → HTTP 429

## Development Approach

- **Testing approach**: Regular (code first, then tests)
- All tests must pass before moving to the next task
- Run tests: `./gradlew :cookbook:test`

## Implementation Steps

### Task 1: Introduce `UrlContentFetcher` abstraction and update `HtmlFetcher`

- [x] Create interface `extractor/src/main/java/net/shamansoft/cookbook/html/UrlContentFetcher.java` with single method `String fetch(String url) throws IOException`
- [x] Make `HtmlFetcher` implement `UrlContentFetcher`
- [x] Annotate `HtmlFetcher` with `@ConditionalOnProperty(name = "cookbook.fetcher.type", havingValue = "jsoup", matchIfMissing = true)` (keep existing `@Service`)
- [x] Update `HtmlExtractor` to inject `UrlContentFetcher` instead of `HtmlFetcher`
- [x] Write unit test for `HtmlExtractor` verifying it uses the injected `UrlContentFetcher` (mock the interface, not the concrete class)
- [x] Run tests — must pass before Task 2

### Task 2: Create `MarkdownNewClient` interface and implementation

- [x] Create interface `extractor/src/main/java/net/shamansoft/cookbook/client/MarkdownNewClient.java` extending `UrlContentFetcher` (inherits `fetch(String url)`)
- [x] Create `extractor/src/main/java/net/shamansoft/cookbook/client/MarkdownNewClientImpl.java` implementing `MarkdownNewClient`
  - `@Service`, `@ConditionalOnProperty(name = "cookbook.fetcher.type", havingValue = "markdown-new")`
  - Constructor-inject `RestClient` with `@Qualifier("markdownNewRestClient")`
  - POST body: `{"url": "<url>"}` with `Content-Type: application/json`
  - Return response body as `String`
  - On HTTP 429: throw `UrlFetchException` with message: `"Rate limit exceeded for markdown.new (500 req/day per IP). Please try again later."`
  - On other HTTP errors: throw `UrlFetchException` with status code
- [x] Add `markdownNewRestClient` bean to `RestClientConfig.java`
  - Base URL: `https://markdown.new`
  - Use the same Apache HttpClient 5 connection pool pattern as existing beans
- [x] Write unit tests for `MarkdownNewClientImpl`:
  - Success: returns markdown string
  - 429: throws `UrlFetchException` with rate limit message
  - Other 4xx/5xx: throws `UrlFetchException`
- [x] Run tests — must pass before Task 3

### Task 3: Add property to application config and verify

- [ ] Add `cookbook.fetcher.type=jsoup` (or `markdown-new`) to `application.properties` with a comment explaining the options
- [ ] Verify full test suite: `./gradlew :cookbook:test`
- [ ] Verify test coverage stays at 74%+
- [ ] Verify all acceptance criteria:
  - Default config (`jsoup`): `HtmlFetcher` bean is active, `MarkdownNewClientImpl` is not created
  - With `markdown-new`: `MarkdownNewClientImpl` bean is active, `HtmlFetcher` is not created

## Technical Details

### Interface hierarchy

```
UrlContentFetcher          (html package)
    fetch(String url)
       ├── HtmlFetcher     (html package) — JSoup, @ConditionalOnProperty(matchIfMissing=true)
       └── MarkdownNewClient (client package)
               └── MarkdownNewClientImpl — RestClient, @ConditionalOnProperty("markdown-new")
```

### HtmlExtractor change (minimal)

```java
// Before
private final HtmlFetcher htmlFetcher;

// After
private final UrlContentFetcher htmlFetcher;  // same field name, broader type
```

### RestClient bean pattern

```java
@Bean
public RestClient markdownNewRestClient(HttpClient httpClient) {
    return RestClient.builder()
        .requestFactory(new HttpComponentsClientHttpRequestFactory(httpClient))
        .baseUrl("https://markdown.new")
        .build();
}
```

### Property switch

```properties
# application.properties
# URL fetching strategy: "jsoup" (default) or "markdown-new"
cookbook.fetcher.type=jsoup
```

## Post-Completion

**Manual verification:**
- Switch to `cookbook.fetcher.type=markdown-new` and test a real recipe URL end-to-end
- Verify rate limit message is user-friendly on 429
- Confirm default `jsoup` path is unchanged
