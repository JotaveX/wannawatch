package br.com.jotave.wannawatch.shared.web;

import br.com.jotave.wannawatch.auth.UnauthorizedException;
import br.com.jotave.wannawatch.profile.photo.PhotoStorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Todos os erros da API saem no formato ProblemDetail (RFC 9457):
 * {@code { "type", "title", "status", "detail", "instance" }}.
 * Erros de parâmetro e de validação do Spring já são tratados pela classe base.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    ProblemDetail badRequest(BadRequestException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    ProblemDetail unauthorized(UnauthorizedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    ProblemDetail forbidden(ForbiddenException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(PhotoStorageException.class)
    ProblemDetail photoStorage(PhotoStorageException e) {
        log.error("Falha no armazenamento das fotos", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                "Não deu pra guardar a foto agora. Tente de novo em instantes.");
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, "A imagem pode ter até 5 MB.");
        return handleExceptionInternal(e, body, headers, status, request);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) {
        log.error("Erro inesperado na API", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Algo deu errado do nosso lado. Tente de novo em instantes.");
    }
}
