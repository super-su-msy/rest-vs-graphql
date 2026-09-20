package run.runnable.numfeelservice.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import run.runnable.numfeelservice.controller.dto.BookDetailDTO;
import run.runnable.numfeelservice.controller.dto.BookStoreStatusDTO;
import run.runnable.numfeelservice.controller.dto.CatalogResponseDTO;
import run.runnable.numfeelservice.service.RestVsGraphqlService;
import run.runnable.numfeelservice.web.ApiEnvelope;

import java.util.concurrent.TimeUnit;

/**
 * REST vs GraphQL comparison demo - REST endpoints.
 * <p>
 * Provides GET endpoints to compare with the GraphQL POST endpoint. Catalog responses include
 * {@code Cache-Control} headers so the demo can show a core REST advantage: GET works naturally
 * with browser/CDN caching while GraphQL POST does not.
 * <p>
 * {@link ResponseEntity} is used here because this demo explicitly needs to control response headers.
 */
@RestController
@RequestMapping("/api/rest-vs-graphql")
public class RestVsGraphqlController {

    private final RestVsGraphqlService service;

    public RestVsGraphqlController(RestVsGraphqlService service) {
        this.service = service;
    }

    /**
     * Full catalog payload: returns every book field to demonstrate over-fetch bytes.
     *
     * @param limit number of rows, default 20
     * @return full catalog response with 60-second Cache-Control
     */
    @GetMapping("/catalog/full")
    public Mono<ResponseEntity<ApiEnvelope<CatalogResponseDTO>>> catalogFull(
            @RequestParam(defaultValue = "20") int limit) {
        return service.catalog(true, limit)
                .map(ApiEnvelope::ok)
                .map(body -> apiCached(body));
    }

    /**
     * Slim catalog payload: returns only the fields this page needs.
     *
     * @param limit number of rows, default 20
     * @return slim catalog response with 60-second Cache-Control
     */
    @GetMapping("/catalog/light")
    public Mono<ResponseEntity<ApiEnvelope<CatalogResponseDTO>>> catalogLight(
            @RequestParam(defaultValue = "20") int limit) {
        return service.catalog(false, limit)
                .map(ApiEnvelope::ok)
                .map(body -> apiCached(body));
    }

    /**
     * Single book detail, including reviews.
     *
     * @param id book ID
     * @return book detail response with 60-second Cache-Control
     */
    @GetMapping("/book/{id}")
    public Mono<ResponseEntity<ApiEnvelope<BookDetailDTO>>> book(@PathVariable int id) {
        return service.book(id)
                .map(ApiEnvelope::ok)
                .map(body -> apiCached(body));
    }

    /**
     * Dataset initialization status.
     *
     * @return row counts for the three tables and readiness flag
     */
    @GetMapping("/status")
    public Mono<ResponseEntity<ApiEnvelope<BookStoreStatusDTO>>> status() {
        return service.status()
                .map(ApiEnvelope::ok)
                .map(body -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body));
    }

    /** Wrap a response with 60-second Cache-Control for the REST caching demo. */
    private <T> ResponseEntity<ApiEnvelope<T>> apiCached(ApiEnvelope<T> body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS).cachePublic())
                .body(body);
    }
}
