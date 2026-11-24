package br.com.jotave.wannawatch.shared.web;

public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
