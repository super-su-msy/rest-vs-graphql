package run.runnable.numfeelservice.controller.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Book catalog item DTO.
 * <p>
 * Used by the three REST comparison endpoints: the full payload returns every field, including
 * isbn / category / pages / stock / publishedYear / description, while the slim payload keeps only
 * the core fields the page actually uses. Unused fields are set to null and stripped by
 * {@code @JsonInclude(NON_NULL)}, making the over-fetch byte waste visible in the response body.
 */
public record CatalogItemDTO(
        int id,
        String title,
        String author,

        @JsonInclude(JsonInclude.Include.NON_NULL) String isbn,
        @JsonInclude(JsonInclude.Include.NON_NULL) String category,
        double price,
        double rating,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer pages,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer stock,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer publishedYear,
        @JsonInclude(JsonInclude.Include.NON_NULL) String description
) {
}
