package br.com.jotave.wannawatch.catalog.application;

import br.com.jotave.wannawatch.catalog.api.CatalogDtos.ImageSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Monta as URLs do CDN de imagens da TMDB (não usa a chave da API). */
@Component
public class ImageUrls {

    private final String base;

    public ImageUrls(@Value("${wannawatch.images.base-url}") String base) {
        this.base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    public ImageSet poster(String path) {
        return path == null ? null : new ImageSet(url("w342", path), url("w780", path));
    }

    public ImageSet backdrop(String path) {
        return path == null ? null : new ImageSet(url("w780", path), url("w1280", path));
    }

    public String profile(String path) {
        return path == null ? null : url("w185", path);
    }

    public String logo(String path) {
        return path == null ? null : url("w154", path);
    }

    private String url(String size, String path) {
        return base + "/" + size + path;
    }
}
