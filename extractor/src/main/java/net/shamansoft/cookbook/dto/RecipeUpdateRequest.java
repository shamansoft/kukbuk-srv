package net.shamansoft.cookbook.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Body of {@code PATCH /v1/recipes/{id}}. Every field is optional: an absent field leaves the stored value
 * unchanged, a present one replaces it. Setters record presence, so {@code "userTags": null} is told apart
 * from a missing {@code userTags} (same for {@code userNotes}).
 */
public class RecipeUpdateRequest {

    private List<String> userTags;
    private boolean userTagsPresent;
    private String userNotes;
    private boolean userNotesPresent;

    public RecipeUpdateRequest() {
    }

    public static RecipeUpdateRequest withUserTags(List<String> userTags) {
        RecipeUpdateRequest request = new RecipeUpdateRequest();
        request.setUserTags(userTags);
        return request;
    }

    public static RecipeUpdateRequest withUserNotes(String userNotes) {
        RecipeUpdateRequest request = new RecipeUpdateRequest();
        request.setUserNotes(userNotes);
        return request;
    }

    public List<String> getUserTags() {
        return userTags;
    }

    @JsonProperty("userTags")
    public void setUserTags(List<String> userTags) {
        this.userTags = userTags;
        this.userTagsPresent = true;
    }

    @JsonIgnore
    public boolean hasUserTags() {
        return userTagsPresent;
    }

    public String getUserNotes() {
        return userNotes;
    }

    @JsonProperty("userNotes")
    public void setUserNotes(String userNotes) {
        this.userNotes = userNotes;
        this.userNotesPresent = true;
    }

    @JsonIgnore
    public boolean hasUserNotes() {
        return userNotesPresent;
    }

    /** True when the body sets no known field. */
    @JsonIgnore
    public boolean isEmpty() {
        return !userTagsPresent && !userNotesPresent;
    }
}
