package net.shamansoft.cookbook.dto;

import jakarta.validation.constraints.NotBlank;

public record Request(
    String html,
    @NotBlank(message = "Title is required") String title,
    @NotBlank(message = "URL is required") String url
) {}
