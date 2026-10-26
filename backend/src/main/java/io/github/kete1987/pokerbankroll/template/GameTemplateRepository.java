package io.github.kete1987.pokerbankroll.template;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GameTemplateRepository extends JpaRepository<GameTemplate, Long> {

    /** Loading entities here could leave stale room/variant state for GameService's later checks. */
    @Query("""
            select new io.github.kete1987.pokerbankroll.template.GameTemplateStart(
                t.room.id, t.gameType, t.modality, v.id, t.gameName, t.buyIn)
            from GameTemplate t left join t.variant v where t.id = :id
            """)
    Optional<GameTemplateStart> findStartFieldsById(long id);

    /** Every template with its room and variant (they are always part of the response). */
    @EntityGraph(attributePaths = {"room", "variant"})
    @Query("select t from GameTemplate t")
    List<GameTemplate> findAllWithRoomAndVariant();
}
