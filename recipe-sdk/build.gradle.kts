plugins {
    id("java-library")
}

version = "0.0.1"

dependencies {
    // Jackson YAML for parsing - using version catalog
    api(libs.bundles.jackson)

    // Jakarta Bean Validation
    api(libs.jakarta.validation.api)

    // Testing - using version catalog (without Spring Boot)
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(libs.assertj.core)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
