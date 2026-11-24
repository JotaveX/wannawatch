package br.com.jotave.wannawatch.catalog.domain;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonRepository extends JpaRepository<Person, Long> {

    List<Person> findByExternalIdIn(Collection<Integer> externalIds);
}
