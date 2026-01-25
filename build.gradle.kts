plugins {
    java
}

// Common configuration for all subprojects
subprojects {
    apply(plugin = "java")

    group = "net.shamansoft"

    repositories {
        mavenCentral()
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    tasks.withType<JavaCompile> {
        options.compilerArgs.add("-parameters")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}

// Root level tasks
tasks.register("cleanAll") {
    description = "Clean all subprojects"
    group = "build"
    dependsOn(subprojects.map { it.tasks.named("clean") })
}

tasks.register("buildAll") {
    description = "Build all subprojects"
    group = "build"
    dependsOn(subprojects.map { it.tasks.named("build") })
}

tasks.register("testAll") {
    description = "Test all subprojects"
    group = "verification"
    dependsOn(subprojects.map { it.tasks.named("test") })
}
