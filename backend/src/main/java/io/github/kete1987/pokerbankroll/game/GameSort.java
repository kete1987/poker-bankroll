package io.github.kete1987.pokerbankroll.game;

import java.util.Locale;
import java.util.Set;

import io.github.kete1987.pokerbankroll.common.error.ApiException;
import io.github.kete1987.pokerbankroll.common.error.ErrorCode;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.domain.Sort.Order;

/**
 * Parses the {@code sort} parameter of the games list: {@code <field>,<asc|desc>}, e.g.
 * {@code net,desc}. The order is always total (ties are broken by date and id), so pages are stable.
 */
final class GameSort {

    /** Newest first; within a day by start time (games without time last), then newest created. */
    static final String DEFAULT = "playedOn,desc";

    private static final Set<String> FIELDS = Set.of("playedOn", "net", "buyIn", "prize", "won", "createdAt");

    private GameSort() {
    }

    static Sort parse(@Nullable String value) {
        // limit -1 keeps empty components, so "," or "net," are rejected instead of half-parsed.
        String[] parts = (value == null || value.isBlank() ? DEFAULT : value).split(",", -1);
        String field = parts[0].strip();
        String directionText = parts.length > 1 ? parts[1].strip().toLowerCase(Locale.ROOT) : "asc";
        if (parts.length > 2 || !FIELDS.contains(field) || !Set.of("asc", "desc").contains(directionText)) {
            throw new ApiException(ErrorCode.INVALID_SORT, String.join(", ", FIELDS.stream().sorted().toList()));
        }
        Direction direction = Direction.fromString(directionText);

        if (field.equals("playedOn")) {
            return Sort.by(
                    new Order(direction, "playedOn"),
                    new Order(direction, "playedAt").nullsLast(),
                    new Order(direction, "id"));
        }
        return Sort.by(
                new Order(direction, field),
                Order.desc("playedOn"),
                Order.desc("id"));
    }
}
