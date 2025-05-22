package net.shamansoft.cookbook.service;

public interface Transformer {

    /**
     * Transforms html to yaml.
     *
     * @param what the html string to transform
     * @return the transformed string
     */
    String transform(String what);
}
