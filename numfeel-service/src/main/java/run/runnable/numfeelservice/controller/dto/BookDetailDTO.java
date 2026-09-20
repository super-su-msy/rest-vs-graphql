package run.runnable.numfeelservice.controller.dto;

import java.util.List;

/**
 * Single-book detail DTO with the book's reviews.
 */
public record BookDetailDTO(
        CatalogItemDTO book,
        List<ReviewDTO> reviews
) {
}
