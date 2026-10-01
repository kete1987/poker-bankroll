package io.github.kete1987.pokerbankroll.game;

import java.util.Optional;

import jakarta.persistence.LockModeType;

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
}
