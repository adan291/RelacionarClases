package org.example.api;

/**
 * Error que se devuelve al cliente como RFC 7807 con el estado indicado.
 */
public class ApiException extends RuntimeException {

    private final int status;
    private final String title;

    public ApiException(int status, String title, String detail) {
        super(detail);
        this.status = status;
        this.title = title;
    }

    public static ApiException badRequest(String detail) {
        return new ApiException(400, "Bad Request", detail);
    }

    public static ApiException tooLarge(String detail) {
        return new ApiException(413, "Payload Too Large", detail);
    }

    public int status() {
        return status;
    }

    public String title() {
        return title;
    }
}
