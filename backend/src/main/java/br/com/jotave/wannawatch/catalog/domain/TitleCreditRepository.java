package br.com.jotave.wannawatch.catalog.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TitleCreditRepository extends JpaRepository<TitleCredit, Long> {

    @Modifying
    @Query("delete from TitleCredit c where c.title.id = :titleId")
    void deleteByTitleId(Long titleId);
}
