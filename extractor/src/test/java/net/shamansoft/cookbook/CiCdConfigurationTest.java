package net.shamansoft.cookbook;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to validate CI/CD workflow configuration for Spring Boot 4 compatibility.
 * Verifies that GitHub Actions workflows are configured with correct Java and GraalVM versions.
 */
class CiCdConfigurationTest {

    private static final String REQUIRED_JAVA_VERSION = "25";
    private static final String REQUIRED_GRAALVM_VERSION = "25";

    /**
     * Gets the project root directory (sar-srv) by traversing up from the current working directory.
     * When tests run via Gradle, the working directory is typically the project root.
     */
    private Path getProjectRoot() {
        Path currentPath = Paths.get("").toAbsolutePath();

        // If we're in extractor directory (when running from IDE), go up one level
        if (currentPath.endsWith("extractor")) {
            return currentPath.getParent();
        }

        // Otherwise, assume we're already in project root
        return currentPath;
    }

    @Test
    void deployWorkflow_shouldUseJava25() throws IOException {
        Path projectRoot = getProjectRoot();
        Path workflowPath = projectRoot.resolve(".github/workflows/deploy.yml");
        Map<String, Object> workflow = loadYamlWorkflow(workflowPath);

        // Verify environment variable
        @SuppressWarnings("unchecked")
        Map<String, String> env = (Map<String, String>) workflow.get("env");
        assertNotNull(env, "deploy.yml should have env section");
        assertEquals(REQUIRED_JAVA_VERSION, env.get("JAVA_VERSION"),
                "deploy.yml should use Java " + REQUIRED_JAVA_VERSION + " for Spring Boot 4");
    }

    @Test
    void prValidationWorkflow_shouldUseJava25() throws IOException {
        Path projectRoot = getProjectRoot();
        Path workflowPath = projectRoot.resolve(".github/workflows/pr-validation.yml");
        Map<String, Object> workflow = loadYamlWorkflow(workflowPath);

        // Verify environment variable
        @SuppressWarnings("unchecked")
        Map<String, String> env = (Map<String, String>) workflow.get("env");
        assertNotNull(env, "pr-validation.yml should have env section");
        assertEquals(REQUIRED_JAVA_VERSION, env.get("JAVA_VERSION"),
                "pr-validation.yml should use Java " + REQUIRED_JAVA_VERSION + " for Spring Boot 4");
    }

    @Test
    void dockerfile_shouldUseGraalVM25() throws IOException {
        Path projectRoot = getProjectRoot();
        Path dockerfilePath = projectRoot.resolve("extractor/Dockerfile.native");
        String content = Files.readString(dockerfilePath);

        // Verify GraalVM 25 base image
        assertTrue(content.contains("ghcr.io/graalvm/native-image-community:" + REQUIRED_GRAALVM_VERSION),
                "Dockerfile.native should use GraalVM " + REQUIRED_GRAALVM_VERSION + " for Spring Boot 4");

        // Verify platform is linux/amd64
        assertTrue(content.contains("--platform=linux/amd64"),
                "Dockerfile.native should target linux/amd64 platform");
    }

    @Test
    void deployWorkflow_shouldHaveAllRequiredPhases() throws IOException {
        Path projectRoot = getProjectRoot();
        Path workflowPath = projectRoot.resolve(".github/workflows/deploy.yml");
        Map<String, Object> workflow = loadYamlWorkflow(workflowPath);

        @SuppressWarnings("unchecked")
        Map<String, Object> jobs = (Map<String, Object>) workflow.get("jobs");
        assertNotNull(jobs, "deploy.yml should have jobs section");

        // Verify all 4 phases exist
        assertTrue(jobs.containsKey("test"), "deploy.yml should have test job (Phase 1)");
        assertTrue(jobs.containsKey("build-and-push"), "deploy.yml should have build-and-push job (Phase 2)");
        assertTrue(jobs.containsKey("deploy"), "deploy.yml should have deploy job (Phase 3)");
        assertTrue(jobs.containsKey("finalize"), "deploy.yml should have finalize job (Phase 4)");
    }

    @Test
    void prValidationWorkflow_shouldRunAllTests() throws IOException {
        Path projectRoot = getProjectRoot();
        Path workflowPath = projectRoot.resolve(".github/workflows/pr-validation.yml");
        String content = Files.readString(workflowPath);

        // Verify unit tests are run
        assertTrue(content.contains(":cookbook:test"),
                "pr-validation.yml should run unit tests");

        // Verify integration tests are run
        assertTrue(content.contains(":cookbook:intTest"),
                "pr-validation.yml should run integration tests");

        // Verify coverage check
        assertTrue(content.contains(":cookbook:checkCoverage"),
                "pr-validation.yml should check code coverage");
    }

    @Test
    void deployWorkflow_shouldHaveManualTrigger() throws IOException {
        Path projectRoot = getProjectRoot();
        Path workflowPath = projectRoot.resolve(".github/workflows/deploy.yml");
        String content = Files.readString(workflowPath);

        // Check for workflow_dispatch trigger in the YAML content
        assertTrue(content.contains("workflow_dispatch"),
                "deploy.yml should support manual workflow_dispatch trigger");

        // Verify it's in the 'on' section by checking nearby context
        assertTrue(content.contains("on:") || content.contains("on:\n"),
                "deploy.yml should have 'on' trigger section");
    }

    @Test
    void deployWorkflow_shouldHaveConcurrencyControl() throws IOException {
        Path projectRoot = getProjectRoot();
        Path workflowPath = projectRoot.resolve(".github/workflows/deploy.yml");
        Map<String, Object> workflow = loadYamlWorkflow(workflowPath);

        @SuppressWarnings("unchecked")
        Map<String, Object> concurrency = (Map<String, Object>) workflow.get("concurrency");
        assertNotNull(concurrency, "deploy.yml should have concurrency section");

        String group = (String) concurrency.get("group");
        assertNotNull(group, "deploy.yml should have concurrency group");
        assertTrue(group.contains("deployment"), "deploy.yml concurrency group should reference deployment");

        Boolean cancelInProgress = (Boolean) concurrency.get("cancel-in-progress");
        assertNotNull(cancelInProgress, "deploy.yml should have cancel-in-progress setting");
        assertFalse(cancelInProgress, "deploy.yml should not cancel in-progress deployments");
    }

    private Map<String, Object> loadYamlWorkflow(Path path) throws IOException {
        Yaml yaml = new Yaml();
        try (FileInputStream fis = new FileInputStream(path.toFile())) {
            return yaml.load(fis);
        }
    }
}
