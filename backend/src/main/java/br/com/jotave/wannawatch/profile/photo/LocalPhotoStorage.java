package br.com.jotave.wannawatch.profile.photo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Fotos numa pasta local, para desenvolver e testar sem conta no R2. O back serve a pasta em
 * {@code /fotos/**} (ver {@link PhotoStorageConfig}), então o {@code publicUrl} aponta para ele.
 */
class LocalPhotoStorage implements PhotoStorage {

    private final Path dir;
    private final String publicUrl;

    LocalPhotoStorage(Path dir, String publicUrl) {
        this.dir = dir.toAbsolutePath().normalize();
        this.publicUrl = publicUrl.replaceAll("/+$", "");
        try {
            Files.createDirectories(this.dir);
        } catch (IOException e) {
            throw new UncheckedIOException("Não deu pra criar a pasta de fotos " + this.dir, e);
        }
    }

    Path dir() {
        return dir;
    }

    @Override
    public String put(String key, byte[] jpeg) {
        Path file = resolve(key);
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, jpeg);
        } catch (IOException e) {
            throw new PhotoStorageException("Falha ao gravar a foto " + file, e);
        }
        return publicUrl + "/" + key;
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new PhotoStorageException("Falha ao apagar a foto " + key, e);
        }
    }

    private Path resolve(String key) {
        Path file = dir.resolve(key).normalize();
        // a chave é montada pelo back, mas não custa garantir que não sai da pasta
        if (!file.startsWith(dir)) throw new IllegalArgumentException("Chave de foto inválida: " + key);
        return file;
    }
}
