package run.runnable.numfeelservice.web;

/**
 * Typed success response envelope: {@code {"status":200,"data":<T>}}.
 * <p>
 * Controllers return business DTOs directly and Spring serializes them while
 * preserving the site-wide {@code status}/{@code data} contract.
 *
 * @param status status code, always 200 for success
 * @param data   business DTO
 * @param <T>    business DTO type
 */
public record ApiEnvelope<T>(int status, T data) {

    /** Build an HTTP 200 success envelope. */
    public static <T> ApiEnvelope<T> ok(T data) {
        return new ApiEnvelope<>(200, data);
    }
}
