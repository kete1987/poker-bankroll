package io.github.kete1987.pokerbankroll.bankroll;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BankrollMovementRepository
        extends JpaRepository<BankrollMovement, Long>, JpaSpecificationExecutor<BankrollMovement> {

    /** Loads the room in the same query (it is always part of the response). */
    @Override
    @EntityGraph(attributePaths = "room")
    Page<BankrollMovement> findAll(Specification<BankrollMovement> spec, Pageable pageable);

    @EntityGraph(attributePaths = "room")
    Optional<BankrollMovement> findWithRoomById(long id);
}
