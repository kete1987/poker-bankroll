package io.github.kete1987.pokerbankroll.common.api;

import java.util.List;
import java.util.function.Function;

import io.swagger.v3.oas.annotations.media.Schema;

import org.springframework.data.domain.Page;

/** One page of a list, with what a client needs to paginate. */
public record PageResponse<T>(
        List<T> items,
        @Schema(description = "Zero-based page number") int page,
        @Schema(description = "Requested page size") int size,
        long totalItems,
        int totalPages) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
