package br.com.jotave.wannawatch.catalog.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TitleRepository extends JpaRepository<Title, Long> {

    Optional<Title> findByExternalIdAndMediaType(Integer externalId, MediaType mediaType);
}
