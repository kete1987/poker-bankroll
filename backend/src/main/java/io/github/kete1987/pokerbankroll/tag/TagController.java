package io.github.kete1987.pokerbankroll.tag;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tags")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Tags", description = "Free-form labels of games")
// Method names are the operation ids of the contract: unique ones keep the ids of other endpoints.
class TagController {

    private final TagService service;

    TagController(TagService service) {
        this.service = service;
    }

    // A plain list, not a page: there are a few tags, and the form of a game suggests all of them.
    @GetMapping
    @Operation(summary = "List tags",
            description = "Every tag with its number of games, by name ignoring case. Tags are created when a "
                    + "game is given a name that no tag has.")
    List<TagResponse> listTags() {
        return service.list();
    }

    @PutMapping("/{id}")
    @Operation(summary = "Rename a tag",
            description = "When another tag already has the new name (ignoring case), this tag is merged into "
                    + "it: its games get the other tag and this one is deleted. Returns the tag the games have now.")
    TagResponse renameTag(@PathVariable long id, @Valid @RequestBody TagRequest request) {
        return service.rename(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a tag", description = "Its games lose it; nothing else changes.")
    void deleteTag(@PathVariable long id) {
        service.delete(id);
    }
}
