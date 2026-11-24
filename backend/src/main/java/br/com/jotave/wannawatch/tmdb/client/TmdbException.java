package br.com.jotave.wannawatch.tmdb.client;

public class TmdbException extends RuntimeException {

    private final int status;

    public TmdbException(String message, int status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public int status() {
        return status;
    }

    public boolean isNotFound() {
        return status == 404;
    }
}
