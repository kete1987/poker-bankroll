package io.github.kete1987.pokerbankroll.game;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.github.kete1987.pokerbankroll.tag.TagName;

/** Replace only the tags; an empty list clears them. */
public record GameTagsRequest(
        @NotNull @Size(max = GameRequest.MAX_TAGS) List<@NotNull @TagName String> tags) {
}

