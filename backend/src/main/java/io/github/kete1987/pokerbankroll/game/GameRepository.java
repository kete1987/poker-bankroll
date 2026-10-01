package io.github.kete1987.pokerbankroll.game;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface GameRepository extends JpaRepository<Game, Long>, JpaSpecificationExecutor<Game> {

    /** Loads the room and the variant in the same query (they are always part of the response). */
    @Override
    @EntityGraph(attributePaths = {"room", "variant"})
    Page<Game> findAll(Specification<Game> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"room", "variant"})
    Optional<Game> findWithRoomAndVariantById(long id);
}
