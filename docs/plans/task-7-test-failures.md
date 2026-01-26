# Task 7 - Test Failures Documentation

## Date
2026-01-25

## Objective
Update test dependencies for Spring Boot 4 and document test failures.

## Actions Taken

### 7.1 Add spring-boot-resttestclient test dependency
- Added `spring-boot-resttestclient` module to `gradle/libs.versions.toml`
- Added to `testing` bundle in version catalog
- Module: `org.springframework.boot:spring-boot-resttestclient`

### 7.2 Add spring-boot-restclient test dependency
- Added `spring-boot-restclient` module to `gradle/libs.versions.toml`
- Added as implementation dependency in `extractor/build.gradle.kts`
- Module: `org.springframework.boot:spring-boot-restclient`

### 7.3 Verify Test Framework Versions
Verified test dependency versions via Gradle dependency report:

**JUnit Jupiter**: 6.0.1
- Managed by Spring Boot 4.0.1
- Upgraded from 5.10.1 in version catalog
- Compatible with Spring Boot 4

**Mockito Core**: 5.18.0
- Version specified in catalog (unchanged)
- Compatible with Spring Boot 4

**AssertJ Core**: 3.26.3
- Version specified in catalog (unchanged)
- Spring Boot 4 attempts to upgrade to 3.27.6 but we override to 3.26.3
- Compatible with Spring Boot 4

**Testcontainers**: 1.19.3
- Version specified in catalog (unchanged)
- Compatible with Spring Boot 4

### 7.4 Test Failures Documented

#### Compilation Errors

**Error 1: TestRestTemplate package relocation**
```
File: extractor/src/test/java/net/shamansoft/cookbook/controller/RecipeControllerSBTest.java:28
Error: package org.springframework.boot.test.web.client does not exist
import org.springframework.boot.test.web.client.TestRestTemplate;
```

**Root Cause**: In Spring Boot 4, TestRestTemplate moved from `org.springframework.boot.test.web.client` to `org.springframework.boot.resttestclient`

**Resolution Required in Task 8**:
1. Update import statement to `org.springframework.boot.resttestclient.TestRestTemplate`
2. Add `@AutoConfigureTestRestTemplate` annotation to RecipeControllerSBTest class

**Error 2: Deprecated API usage**
```
Note: extractor/src/test/java/net/shamansoft/cookbook/service/StorageServiceTest.java uses or overrides a deprecated API.
Note: Recompile with -Xlint:deprecation for details.
```

**Root Cause**: Likely using deprecated test annotations or methods

**Resolution Required in Task 8**:
1. Investigate specific deprecated API usage with `-Xlint:deprecation`
2. Update to Spring Boot 4 equivalents

## Files Modified
1. `gradle/libs.versions.toml` - Added spring-boot-resttestclient and spring-boot-restclient modules
2. `extractor/build.gradle.kts` - Added spring-boot-restclient implementation dependency
3. `extractor/src/test/java/net/shamansoft/cookbook/config/TestDependenciesTest.java` - Created verification test for test dependencies

## Next Steps (Task 8)
1. Fix TestRestTemplate import in RecipeControllerSBTest.java
2. Add @AutoConfigureTestRestTemplate annotation
3. Investigate and fix deprecated API usage in StorageServiceTest.java
4. Run full test suite to identify additional migration issues

## References
- [Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
- [What's New for Testing in Spring Boot 4](https://rieckpil.de/whats-new-for-testing-in-spring-boot-4-0-and-spring-framework-7/)
