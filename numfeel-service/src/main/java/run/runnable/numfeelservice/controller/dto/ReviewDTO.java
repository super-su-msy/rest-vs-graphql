package run.runnable.numfeelservice.controller.dto;

/**
 * Book review DTO.
 */
public record ReviewDTO(
        long id,
        int rating,
        String content,
        String reviewer,
        long createdAt
) {
}
