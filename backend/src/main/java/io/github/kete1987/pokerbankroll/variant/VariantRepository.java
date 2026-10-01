package io.github.kete1987.pokerbankroll.variant;

import java.util.Optional;
import java.util.Set;

import io.github.kete1987.pokerbankroll.catalog.GameType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface VariantRepository extends JpaRepository<Variant, Long> {

    Optional<Variant> findByGameTypeAndNameIgnoreCase(GameType gameType, String name);

    /** Ids of the variants used by games. */
    @Query(value = "select distinct variant_id from game where variant_id is not null", nativeQuery = true)
    Set<Long> findIdsInUse();

    @Query(value = "select exists (select 1 from game where variant_id = :id)", nativeQuery = true)
    boolean isInUse(long id);
}
