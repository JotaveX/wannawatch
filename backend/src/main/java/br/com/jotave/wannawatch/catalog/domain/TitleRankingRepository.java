package br.com.jotave.wannawatch.catalog.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TitleRankingRepository extends JpaRepository<TitleRanking, TitleRanking.Key> {

    @Modifying
    @Query("delete from TitleRanking r where r.id.listKey = :listKey")
    void deleteByListKey(String listKey);
}
