package net.shamansoft.cookbook.client;

import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;

import java.lang.reflect.Method;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleDriveLambdaTest {

    @Test
    void exerciseAllUriLambdas() throws Exception {
        // Create instance (clients not used for these tests)
        GoogleDrive gd = new GoogleDrive(null, null);

        // Helper to invoke possibly-static private lambda methods.
        // We don't attempt to match exact parameter types; instead find a declared method
        // by name and arity and invoke it. This avoids issues with null args or primitive types.
        var invoke = (java.util.function.BiFunction<String, Object[], URI>) (name, args) -> {
            for (Method mm : GoogleDrive.class.getDeclaredMethods()) {
                if (!mm.getName().equals(name)) continue;
                if (mm.getParameterCount() != args.length) continue;
                try {
                    mm.setAccessible(true);
                    Object target = java.lang.reflect.Modifier.isStatic(mm.getModifiers()) ? null : gd;
                    return (URI) mm.invoke(target, args);
                } catch (IllegalArgumentException iae) {
                    // Try next candidate if invocation failed due to arg types
                    continue;
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
            throw new RuntimeException("No matching method found: " + name + " arity=" + args.length);
        };

        var builder = UriComponentsBuilder.fromUriString("https://example.com");

        // Invoke lambdas that take (String, UriBuilder)
        URI u1 = invoke.apply("lambda$getFolder$0", new Object[]{"TestFolder", builder});
        assertThat(u1.toString()).contains("/files").contains("TestFolder");

        URI u2 = invoke.apply("lambda$getFile$0", new Object[]{"TestFile", "folder123", builder});
        // Query may be URL encoded and combined; ensure key parts are present
        assertThat(u2.toString()).contains("/files").contains("TestFile").contains("folder123");

        URI u3 = invoke.apply("lambda$createFolder$0", new Object[]{builder});
        assertThat(u3.toString()).contains("/files");

        // createFile lambdas
        GoogleDrive.Item dummy = new GoogleDrive.Item("id123", "name.yaml");
        URI u4 = invoke.apply("lambda$createFile$0", new Object[]{builder});
        assertThat(u4.toString()).contains("/files");
        URI u5 = invoke.apply("lambda$createFile$1", new Object[]{dummy, builder});
        assertThat(u5.toString()).contains("/files/").contains(dummy.id());

        // updateFile lambdas
        URI u6 = invoke.apply("lambda$updateFile$0", new Object[]{dummy, builder});
        assertThat(u6.toString()).contains(dummy.id());
        URI u7 = invoke.apply("lambda$updateFile$1", new Object[]{dummy, builder});
        assertThat(u7.toString()).contains("uploadType=media");

        // download and metadata lambdas
        URI u8 = invoke.apply("lambda$downloadFileAsString$0", new Object[]{"file123", builder});
        assertThat(u8.toString()).contains("/files/").contains("alt=media");
        URI u9 = invoke.apply("lambda$downloadFileAsBytes$0", new Object[]{"file123", builder});
        assertThat(u9.toString()).contains("alt=media");
        URI u10 = invoke.apply("lambda$getFileMetadata$0", new Object[]{"file123", builder});
        assertThat(u10.toString()).contains("fields=");

        // listFiles lambda with (String, int, String, UriBuilder)
        URI u11 = invoke.apply("lambda$listFiles$0", new Object[]{"folder123", Integer.valueOf(10), null, builder});
        assertThat(u11.toString()).contains("pageSize").contains("q=");
    }
}
