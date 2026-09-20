package run.runnable.numfeelservice.controller.dto;

/**
 * Bookstore dataset initialization status DTO.
 */
public record BookStoreStatusDTO(
        boolean dataReady,
        long authors,
        long books,
        long reviews
) {
}
