plugins {
    id("java")
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.graalvm.native)
    alias(libs.plugins.jacoco)
    alias(libs.plugins.owasp.dependency.check)
}

version = "0.8.4-SNAPSHOT"

springBoot {
    buildInfo()   // This will generate a build-info.properties file with accurate values
}

sourceSets {
    create("intTest") {
        java.srcDir("src/intTest/java")
        resources.srcDir("src/intTest/resources")
        compileClasspath += sourceSets["main"].output + sourceSets["test"].output
        runtimeClasspath += sourceSets["main"].output + sourceSets["test"].output
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
    create("mockitoAgent")
    getByName("intTestImplementation") {
        extendsFrom(configurations.testImplementation.get())
    }
    getByName("intTestRuntimeOnly") {
        extendsFrom(configurations.testRuntimeOnly.get())
    }
}

dependencies {
    // Recipe SDK - internal library for recipe parsing and validation
    implementation(project(":recipe-sdk"))

    // Spring Boot Starters - exclude default logging
    implementation(libs.bundles.spring.boot.starters) {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-logging")
    }

    // SLF4J Simple Logging for native compatibility
    implementation(libs.slf4j.api)
    implementation(libs.slf4j.simple)

    // Web Scraping
    implementation(libs.jsoup)

    // Google Cloud Services
    implementation(libs.bundles.google.cloud) {
        exclude(group = "commons-logging", module = "commons-logging")
    }

    // Bean Validation Implementation (runtime only - needed for validation to work)
    runtimeOnly(libs.hibernate.validator)

    // Annotation Processing
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // Testing - exclude default logging to avoid conflicts with SLF4J Simple
    testImplementation(libs.bundles.testing) {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-logging")
    }
    testImplementation(libs.mockito.core)
    "mockitoAgent"(libs.mockito.core) {
        isTransitive = false
    }

    // Integration test dependencies
    "intTestImplementation"(libs.bundles.testcontainers) {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-logging")
    }
    "intTestImplementation"(libs.wiremock.standalone)
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("cookbook.jar")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport) // Generate report after tests run
}

// Integration test task
tasks.register<Test>("intTest") {
    description = "Runs integration tests"
    group = "verification"
    testClassesDirs = sourceSets["intTest"].output.classesDirs
    classpath = sourceSets["intTest"].runtimeClasspath
    useJUnitPlatform()
}

// JaCoCo configuration
jacoco {
    toolVersion = libs.versions.jacoco.get()
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.test) // Tests are required to run before generating the report
    reports {
        xml.required.set(true) // XML report for CI tools like SonarQube
        html.required.set(true) // HTML report for human readability
        csv.required.set(false)
    }
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    violationRules {
        rule {
            limit {
                minimum = BigDecimal("0.4") // 40% code coverage achieved
            }
        }
    }
}

// Add a task to check coverage
tasks.register("checkCoverage") {
    dependsOn(tasks.test)
    dependsOn(tasks.jacocoTestReport)
    dependsOn(tasks.jacocoTestCoverageVerification)
}

// Configure dependency check
configure<org.owasp.dependencycheck.gradle.extension.DependencyCheckExtension> {
    outputDirectory = layout.buildDirectory.dir("reports/dependency-check").get().asFile.path
    formats = listOf("ALL")
    suppressionFile = "dependency-check-suppressions.xml"
    failBuildOnCVSS = 7.0f
    analyzers.apply {
        centralEnabled = true
        assemblyEnabled = false
        nuspecEnabled = false
        nugetconfEnabled = false
    }
}

graalvmNative {
    binaries {
        named("main") {
            imageName.set("cookbook")
            resources.autodetect()
            buildArgs.add("--initialize-at-build-time=org.slf4j")
        }
    }
}
