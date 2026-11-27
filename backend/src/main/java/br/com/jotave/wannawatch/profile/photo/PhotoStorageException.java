package br.com.jotave.wannawatch.profile.photo;

/** O armazenamento das fotos (R2 ou disco) falhou: vira 502 para o front tentar de novo. */
public class PhotoStorageException extends RuntimeException {

    public PhotoStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
