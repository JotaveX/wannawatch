package br.com.jotave.wannawatch.tmdb.importer;

import br.com.jotave.wannawatch.catalog.domain.MediaType;

/** Um título da TMDB ainda não buscado: id dela + tipo. */
record TitleRef(int id, MediaType type) {}
