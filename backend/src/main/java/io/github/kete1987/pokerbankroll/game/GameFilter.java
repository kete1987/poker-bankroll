package io.github.kete1987.pokerbankroll.game;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jakarta.persistence.criteria.Predicate;

import io.github.kete1987.pokerbankroll.catalog.GameType;
import io.github.kete1987.pokerbankroll.catalog.Modality;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;

/** Criteria to select games; every field is optional and they are combined with AND. */
public record GameFilter(
        @Nullable LocalDate from,
        @Nullable LocalDate to,
        @Nullable GameType gameType,
        @Nullable Modality modality,
        @Nullable Long roomId,
        @Nullable Long variantId,
        @Nullable GameStatus status,
        @Nullable String currencyCode,
        @Nullable String text) {

    private static final char ESCAPE = '\\';

    public Specification<Game> toSpecification() {
        return (game, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(game.get("playedOn"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(game.get("playedOn"), to));
            }
            if (gameType != null) {
                predicates.add(cb.equal(game.get("gameType"), gameType));
            }
            if (modality != null) {
                predicates.add(cb.equal(game.get("modality"), modality));
            }
            if (roomId != null) {
                predicates.add(cb.equal(game.get("room").get("id"), roomId));
            }
            if (variantId != null) {
                predicates.add(cb.equal(game.get("variant").get("id"), variantId));
            }
            if (status != null) {
                predicates.add(cb.equal(game.get("status"), status));
            }
            if (currencyCode != null && !currencyCode.isBlank()) {
                predicates.add(cb.equal(game.get("room").get("currencyCode"), currencyCode.strip()));
            }
            if (text != null && !text.isBlank()) {
                String pattern = "%" + escapeLike(text.strip().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(game.get("name")), pattern, ESCAPE),
                        cb.like(cb.lower(game.get("notes")), pattern, ESCAPE)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** The text is searched literally: % and _ typed by the user are not wildcards. */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
