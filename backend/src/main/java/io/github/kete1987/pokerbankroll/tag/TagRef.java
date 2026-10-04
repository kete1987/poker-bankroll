package io.github.kete1987.pokerbankroll.tag;

import java.util.Comparator;

/** A tag as games and groups of statistics carry it. */
public record TagRef(long id, String name) {

    /** By name ignoring case; the id only makes the order stable. */
    public static final Comparator<TagRef> BY_NAME = Comparator.comparing(TagRef::name, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(TagRef::name)
            .thenComparingLong(TagRef::id);
}
