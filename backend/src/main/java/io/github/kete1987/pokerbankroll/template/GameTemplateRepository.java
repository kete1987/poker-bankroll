package io.github.kete1987.pokerbankroll.template;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GameTemplateRepository extends JpaRepository<GameTemplate, Long> {

    /** Every template with its room and variant (they are always part of the response). */
    @EntityGraph(attributePaths = {"room", "variant"})
    @Query("select t from GameTemplate t")
    List<GameTemplate> findAllWithRoomAndVariant();
}
