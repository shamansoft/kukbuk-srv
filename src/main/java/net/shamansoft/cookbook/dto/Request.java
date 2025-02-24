package net.shamansoft.cookbook.dto;

import jakarta.validation.constraints.NotBlank;

public record Request(
    @NotBlank(message = "HTML content is required") String html,
    @NotBlank(message = "Title is required") String title
) {}
