package br.com.jotave.wannawatch.tmdb.importer;

/** Publicado ao fim de uma importação que gravou títulos (com sucesso total ou parcial). */
public record CatalogImportedEvent(ImportResult result) {}
