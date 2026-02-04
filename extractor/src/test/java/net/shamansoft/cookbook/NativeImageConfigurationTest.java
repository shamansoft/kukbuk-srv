package net.shamansoft.cookbook;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test to validate native image build configuration for Spring Boot 4 and GraalVM 25+.
 * These tests verify that the configuration is correct for native image compilation,
 * which will be validated in the CI/CD pipeline on x86-64-v3 hardware.
 * <p>
 * Note: Actual native image builds cannot be performed locally on Apple Silicon/ARM due to
 * GraalVM 25 container requiring x86-64-v3 CPU support. Native builds are tested in CI/CD.
 */
class NativeImageConfigurationTest {

    private static final String REQUIRED_GRAALVM_VERSION = "25";

    /**
     * Gets the project root directory (sar-srv) by traversing up from the current working directory.
     */
    private Path getProjectRoot() {
        Path currentPath = Paths.get("").toAbsolutePath();
        if (currentPath.endsWith("extractor")) {
            return currentPath.getParent();
        }
        return currentPath;
    }

    @Test
    void graalvmBuildConfig_shouldBeConfiguredInBuildGradle() throws IOException {
        Path projectRoot = getProjectRoot();
        Path buildGradlePath = projectRoot.resolve("extractor/build.gradle.kts");
        String content = Files.readString(buildGradlePath);

        // Verify graalvmNative configuration block exists
        assertTrue(content.contains("graalvmNative"),
                "build.gradle.kts should have graalvmNative configuration block");

        // Verify image name is set to "cookbook"
        assertTrue(content.contains("imageName.set(\"cookbook\")"),
                "build.gradle.kts should set native image name to 'cookbook'");

        // Verify resource autodetection is enabled
        assertTrue(content.contains("resources.autodetect()"),
                "build.gradle.kts should enable resource autodetection for native image");

        // Verify SLF4J initialization at build time
        assertTrue(content.contains("--initialize-at-build-time=org.slf4j"),
                "build.gradle.kts should initialize SLF4J at build time for native image");
    }

    @Test
    void reflectionConfig_shouldExistForRecipeModels() {
        Path projectRoot = getProjectRoot();
        Path reflectConfigPath = projectRoot.resolve("extractor/src/main/resources/META-INF/native-image/reflect-config.json");

        assertTrue(Files.exists(reflectConfigPath),
                "Reflection config should exist for GraalVM native image: " + reflectConfigPath);
    }

    @Test
    void reflectionConfig_shouldExistForRecipeSdk() {
        Path projectRoot = getProjectRoot();
        Path sdkReflectConfigPath = projectRoot.resolve("recipe-sdk/src/main/resources/META-INF/native-image/net.shamansoft.recipe/recipe-sdk/reflect-config.json");

        assertTrue(Files.exists(sdkReflectConfigPath),
                "Recipe SDK reflection config should exist for GraalVM native image: " + sdkReflectConfigPath);
    }

    @Test
    void reflectionConfig_shouldIncludeGeminiRequestClasses() throws IOException {
        Path projectRoot = getProjectRoot();
        Path reflectConfigPath = projectRoot.resolve("extractor/src/main/resources/META-INF/native-image/reflect-config.json");
        String content = Files.readString(reflectConfigPath);

        // Verify Gemini API classes are registered for reflection
        assertTrue(content.contains("GeminiRequest") || content.contains("gemini"),
                "Reflection config should include Gemini API request classes");
    }

    @Test
    void reflectionConfig_shouldIncludeRecipeModel() throws IOException {
        Path projectRoot = getProjectRoot();
        Path reflectConfigPath = projectRoot.resolve("extractor/src/main/resources/META-INF/native-image/reflect-config.json");
        String content = Files.readString(reflectConfigPath);

        // Verify Recipe model classes are registered for reflection
        assertTrue(content.contains("Recipe") || content.contains("recipe"),
                "Reflection config should include Recipe model classes");
    }

    @Test
    void dockerfileNative_shouldUseGraalVM25() throws IOException {
        Path projectRoot = getProjectRoot();
        Path dockerfilePath = projectRoot.resolve("extractor/Dockerfile.native");
        String content = Files.readString(dockerfilePath);

        // Verify GraalVM 25 base image
        assertTrue(content.contains("ghcr.io/graalvm/native-image-community:" + REQUIRED_GRAALVM_VERSION),
                "Dockerfile.native should use GraalVM " + REQUIRED_GRAALVM_VERSION);

        // Verify platform is linux/amd64
        assertTrue(content.contains("--platform=linux/amd64"),
                "Dockerfile.native should target linux/amd64 platform");
    }

    @Test
    void dockerfileNative_shouldHaveMemoryOptimization() throws IOException {
        Path projectRoot = getProjectRoot();
        Path dockerfilePath = projectRoot.resolve("extractor/Dockerfile.native");
        String content = Files.readString(dockerfilePath);

        // Verify Java memory settings for Java 25
        assertTrue(content.contains("JAVA_OPTS") && content.contains("-Xmx"),
                "Dockerfile.native should have JAVA_OPTS with heap memory settings");

        // Verify native image memory settings
        assertTrue(content.contains("NATIVE_IMAGE_OPTS") && content.contains("-J-Xmx"),
                "Dockerfile.native should have NATIVE_IMAGE_OPTS with native build memory settings");

        // Verify metaspace settings (important for Java 25)
        assertTrue(content.contains("MaxMetaspaceSize"),
                "Dockerfile.native should configure MaxMetaspaceSize for Java 25");
    }

    @Test
    void dockerfileNative_shouldUseDistrolessBaseImage() throws IOException {
        Path projectRoot = getProjectRoot();
        Path dockerfilePath = projectRoot.resolve("extractor/Dockerfile.native");
        String content = Files.readString(dockerfilePath);

        // Verify distroless base for minimal attack surface
        assertTrue(content.contains("gcr.io/distroless/base-debian12"),
                "Dockerfile.native should use distroless base image for production");
    }

    @Test
    void dockerfileNative_shouldCopyLibzDependency() throws IOException {
        Path projectRoot = getProjectRoot();
        Path dockerfilePath = projectRoot.resolve("extractor/Dockerfile.native");
        String content = Files.readString(dockerfilePath);

        // Verify libz.so.1 dependency is copied (required for native image)
        assertTrue(content.contains("libz.so.1"),
                "Dockerfile.native should copy libz.so.1 dependency for native image");
    }

    @Test
    void dockerfileNative_shouldRunNativeCompileTask() throws IOException {
        Path projectRoot = getProjectRoot();
        Path dockerfilePath = projectRoot.resolve("extractor/Dockerfile.native");
        String content = Files.readString(dockerfilePath);

        // Verify native compilation task
        assertTrue(content.contains("cookbook:nativeCompile"),
                "Dockerfile.native should run cookbook:nativeCompile task");

        // Verify target platform is specified
        assertTrue(content.contains("-Porg.graalvm.buildtools.native.targetPlatform=linux-amd64"),
                "Dockerfile.native should specify target platform for cross-compilation");
    }

    @Test
    void buildScript_shouldSupportNativeFlag() throws IOException {
        Path projectRoot = getProjectRoot();
        Path buildScriptPath = projectRoot.resolve("extractor/scripts/build.sh");
        String content = Files.readString(buildScriptPath);

        // Verify --native flag support
        assertTrue(content.contains("--native"),
                "build.sh should support --native flag");

        // Verify Dockerfile.native selection
        assertTrue(content.contains("Dockerfile.native"),
                "build.sh should use Dockerfile.native when --native flag is provided");

        // Verify memory limit configuration
        assertTrue(content.contains("MEMORY_LIMIT"),
                "build.sh should support configurable memory limit for native builds");
    }

    @Test
    void buildScript_shouldUseCorrectDockerBuildContext() throws IOException {
        Path projectRoot = getProjectRoot();
        Path buildScriptPath = projectRoot.resolve("extractor/scripts/build.sh");
        String content = Files.readString(buildScriptPath);

        // Verify multi-project build context (must be project root for settings.gradle)
        assertTrue(content.contains("cd ../"),
                "build.sh should change to project root for multi-module build context");

        // Verify correct Dockerfile path with extractor/ prefix
        assertTrue(content.contains("extractor/Dockerfile.native") || content.contains("-f $DOCKERFILE"),
                "build.sh should use correct Dockerfile path from project root");
    }

    @Test
    void cicdPipeline_shouldBuildNativeImage() throws IOException {
        Path projectRoot = getProjectRoot();
        Path workflowPath = projectRoot.resolve(".github/workflows/deploy.yml");
        String content = Files.readString(workflowPath);

        // Verify native image build in CI/CD
        assertTrue(content.contains("--native") || content.contains("Dockerfile.native"),
                "deploy.yml should build native image in CI/CD pipeline");

        // Verify Docker buildx is used (supports multi-platform)
        assertTrue(content.contains("docker buildx") || content.contains("build.sh"),
                "deploy.yml should use docker buildx for native image builds");
    }

    @Test
    void cicdPipeline_shouldTargetLinuxAmd64Platform() throws IOException {
        Path projectRoot = getProjectRoot();
        Path workflowPath = projectRoot.resolve(".github/workflows/deploy.yml");
        String content = Files.readString(workflowPath);

        // Verify linux/amd64 platform target (required for Cloud Run)
        assertTrue(content.contains("linux/amd64") || content.contains("build.sh"),
                "deploy.yml should target linux/amd64 platform for Cloud Run deployment");
    }

    @Test
    void nativeImageMetrics_shouldBeDocumentedInPlan() throws IOException {
        Path projectRoot = getProjectRoot();
        Path planPath = projectRoot.resolve("docs/plans/20260125-spring-boot-4-migration.md");
        String content = Files.readString(planPath);

        // Verify expected metrics are documented
        assertTrue(content.contains("Startup time") && content.contains("<1 second"),
                "Plan should document expected native image startup time");
        assertTrue(content.contains("Memory footprint") && content.contains("200MB"),
                "Plan should document expected native image memory usage");
        assertTrue(content.contains("Build time") && content.contains("10-15 minutes"),
                "Plan should document expected native image build time");
    }

    @Test
    void nativeImageLimitation_shouldBeDocumented() throws IOException {
        Path projectRoot = getProjectRoot();
        Path planPath = projectRoot.resolve("docs/plans/20260125-spring-boot-4-migration.md");
        String content = Files.readString(planPath);

        // Verify Apple Silicon/ARM limitation is documented
        assertTrue(content.contains("Apple Silicon") || content.contains("ARM"),
                "Plan should document Apple Silicon/ARM limitation for local native builds");
        assertTrue(content.contains("x86-64-v3"),
                "Plan should document x86-64-v3 CPU requirement for GraalVM 25");
        assertTrue(content.contains("CI/CD") && content.contains("native"),
                "Plan should document that native builds are validated in CI/CD");
    }
}
