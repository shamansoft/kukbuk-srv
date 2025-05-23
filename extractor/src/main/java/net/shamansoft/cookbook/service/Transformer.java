package net.shamansoft.cookbook.service;

public interface Transformer {

    /**
     * Transforms HTML content to YAML.
     *
     * @param htmlContent the HTML string to transform
     * @return the transformed result
     */
    Response transform(String htmlContent);

    /**
     * Represents the response of a transformation.
     * Contains information on whether the content is a recipe and the transformed value.
     */
    record Response(boolean isRecipe, String value) {}
}