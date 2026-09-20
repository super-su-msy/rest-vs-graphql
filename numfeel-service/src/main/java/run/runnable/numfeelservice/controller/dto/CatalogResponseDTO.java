package run.runnable.numfeelservice.controller.dto;

import java.util.List;

/**
 * Book catalog list response DTO.
 *
 * @param count     number of returned items
 * @param items     catalog items
 * @param elapsedMs server-side processing latency in milliseconds, used to compare REST / GraphQL cost
 * @param sqlCalls  number of SQL calls for this request; REST stays at one
 */
public record CatalogResponseDTO(
        int count,
        List<CatalogItemDTO> items,
        long elapsedMs,
        int sqlCalls
) {
}
