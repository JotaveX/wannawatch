package br.com.jotave.wannawatch.catalog.domain;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GenreRepository extends JpaRepository<Genre, Long> {

    List<Genre> findByExternalIdIn(Collection<Integer> externalIds);
}
