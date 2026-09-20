package run.runnable.numfeelservice.web;

/**
 * Business exception carrying an HTTP status code and user-facing message.
 * It is converted by {@link GlobalExceptionHandler} into an
 * {@code {"status":xxx,"message":"..."}} response.
 */
public class ApiException extends RuntimeException {

    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(400, message);
    }

    public int status() {
        return status;
    }
}
