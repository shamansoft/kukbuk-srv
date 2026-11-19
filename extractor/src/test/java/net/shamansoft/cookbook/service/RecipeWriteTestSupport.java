package net.shamansoft.cookbook.service;

import org.mockito.stubbing.Answer;

import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

final class RecipeWriteTestSupport {

    private RecipeWriteTestSupport() {
    }

    static Answer<DriveService.UploadResult> writesRecipe(DriveService.UploadResult result) {
        return writesRecipe(null, result);
    }

    static Answer<DriveService.UploadResult> writesRecipe(String expected, DriveService.UploadResult result) {
        return invocation -> {
            Function<Optional<String>, String> edit = invocation.getArgument(3);
            String yaml = edit.apply(Optional.empty());
            if (expected != null) {
                assertThat(yaml).isEqualTo(expected);
            }
            return result;
        };
    }
}
