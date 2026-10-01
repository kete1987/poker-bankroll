package io.github.kete1987.pokerbankroll.game;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface GameRepository extends JpaRepository<Game, Long>, JpaSpecificationExecutor<Game> {

    /** Loads the room and the variant in the same query (they are always part of the response). */
    @Override
    @EntityGraph(attributePaths = {"room", "variant"})
    Page<Game> findAll(Specification<Game> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"room", "variant"})
    Optional<Game> findWithRoomAndVariantById(long id);

    /**
     * Loads a game to modify it, locking its row until the transaction ends ({@code SELECT ... FOR
     * UPDATE}). Requests changing the same game then run one after another, each on the latest
     * state, so none is lost (e.g. a double click on "add re-entry", or a re-entry racing a finish).
     * No entity graph: PostgreSQL cannot lock the nullable side of the outer join to the variant.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Game g where g.id = :id")
    Optional<Game> findForUpdateById(long id);

    /**
     * The names of the games that contain the text, most used first (then most recent, then by
     * name). Names that differ only in case or surrounding spaces are one; each comes as written
     * in its most recent game (latest date, then latest id), with what that game had. The text is
     * searched literally and ignoring case; {@code gameType} is optional and restricts both the
     * names and their figures to the games of that type.
     */
    @Query(value = """
            select name, games, game_type as "gameType", modality, buy_in as "buyIn",
                   variant_id as "variantId", variant_code as "variantCode", variant_name as "variantName"
            from (
                select btrim(g.name) as name, lower(btrim(g.name)) as name_key, g.played_on,
                       g.game_type_code as game_type, g.modality_code as modality, g.buy_in,
                       v.id as variant_id, v.code as variant_code, v.name as variant_name,
                       count(*) over (partition by lower(btrim(g.name))) as games,
                       row_number() over (
                           partition by lower(btrim(g.name)) order by g.played_on desc, g.id desc) as recency
                from game g left join variant v on v.id = g.variant_id
                where strpos(lower(g.name), lower(:text)) > 0
                  and (cast(:gameType as varchar) is null or g.game_type_code = :gameType)
            ) named
            where recency = 1
            order by games desc, played_on desc, name_key
            limit :limit
            """, nativeQuery = true)
    List<NameUse> findNamesContaining(String text, @Nullable String gameType, int limit);

    /** A row of {@link #findNamesContaining}. */
    interface NameUse {

        String getName();

        long getGames();

        String getGameType();

        String getModality();

        BigDecimal getBuyIn();

        @Nullable Long getVariantId();

        @Nullable String getVariantCode();

        @Nullable String getVariantName();
    }
}
