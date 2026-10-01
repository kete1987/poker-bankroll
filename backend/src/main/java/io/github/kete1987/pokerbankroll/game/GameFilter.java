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

/**
 * Criteria to select games; every field is optional and they are combined with AND. The ones that
 * take several values (game types, rooms, variants) select the games matching any of them; an
 * empty list is no filter.
 */
public record GameFilter(
        @Nullable LocalDate from,
        @Nullable LocalDate to,
        @Nullable List<GameType> gameTypes,
        @Nullable Modality modality,
        @Nullable List<Long> roomIds,
        @Nullable List<Long> variantIds,
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
            if (isGiven(gameTypes)) {
                predicates.add(game.get("gameType").in(gameTypes));
            }
            if (modality != null) {
                predicates.add(cb.equal(game.get("modality"), modality));
            }
            if (isGiven(roomIds)) {
                predicates.add(game.get("room").get("id").in(roomIds));
            }
            if (isGiven(variantIds)) {
                predicates.add(game.get("variant").get("id").in(variantIds));
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

    private static boolean isGiven(@Nullable List<?> values) {
        return values != null && !values.isEmpty();
    }

    /** The text is searched literally: % and _ typed by the user are not wildcards. */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
