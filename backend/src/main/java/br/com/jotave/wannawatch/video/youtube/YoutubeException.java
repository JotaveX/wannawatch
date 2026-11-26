package br.com.jotave.wannawatch.video.youtube;

public class YoutubeException extends RuntimeException {

    private final int status;
    private final boolean quotaExceeded;

    public YoutubeException(String message, int status, boolean quotaExceeded, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.quotaExceeded = quotaExceeded;
    }

    public int status() {
        return status;
    }

    public boolean quotaExceeded() {
        return quotaExceeded;
    }
}
