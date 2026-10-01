package io.github.kete1987.pokerbankroll.bankroll;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;

/**
 * Criteria to select movements; every field is optional and they are combined with AND. Several
 * rooms select the movements of any of them; an empty list is no filter.
 */
public record MovementFilter(
        @Nullable LocalDate from,
        @Nullable LocalDate to,
        @Nullable MovementType type,
        @Nullable List<Long> roomIds,
        @Nullable Boolean withoutRoom,
        @Nullable String currencyCode) {

    /** A blank parameter ({@code roomId=}) arrives as a list holding a null: it is no value. */
    public MovementFilter {
        roomIds = roomIds == null ? null : roomIds.stream().filter(Objects::nonNull).toList();
    }

    Specification<BankrollMovement> toSpecification() {
        return (movement, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(movement.get("occurredOn"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(movement.get("occurredOn"), to));
            }
            if (type != null) {
                predicates.add(cb.equal(movement.get("type"), type));
            }
            if (roomIds != null && !roomIds.isEmpty()) {
                predicates.add(movement.get("room").get("id").in(roomIds));
            }
            if (withoutRoom != null) {
                predicates.add(withoutRoom ? cb.isNull(movement.get("room")) : cb.isNotNull(movement.get("room")));
            }
            if (currencyCode != null && !currencyCode.isBlank()) {
                // The currency of the room, or the own one of a movement without a room.
                String code = currencyCode.strip();
                predicates.add(cb.or(
                        cb.equal(movement.get("currencyCode"), code),
                        cb.equal(movement.join("room", JoinType.LEFT).get("currencyCode"), code)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
