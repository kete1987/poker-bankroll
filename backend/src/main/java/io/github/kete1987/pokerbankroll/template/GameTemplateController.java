package io.github.kete1987.pokerbankroll.template;

import java.net.URI;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/game-templates")
@Tag(name = "Game templates", description = "Games played often, to start one in a click")
class GameTemplateController {

    private final GameTemplateService service;

    GameTemplateController(GameTemplateService service) {
        this.service = service;
    }

    // A plain list, not a page: templates are a handful.
    @GetMapping
    @Operation(summary = "List templates",
            description = "Ordered by label or, without one, by the name of the room (ignoring case), then by "
                    + "game type and buy-in. Those in an inactive room or with an inactive variant are listed "
                    + "too, with `usable: false`.")
    List<GameTemplateResponse> listTemplates() {
        return service.list();
    }

    @PostMapping
    @Operation(summary = "Create a template",
            description = "The rules of a game apply: a variant of the game type, and no inactive room or variant.")
    @ApiResponse(responseCode = "201", description = "Created")
    ResponseEntity<GameTemplateResponse> createTemplate(@Valid @RequestBody GameTemplateRequest request) {
        GameTemplateResponse template = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(template.id());
        return ResponseEntity.created(location).body(template);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a template",
            description = "Replaces every field; omitted optional fields take their default. It can keep an "
                    + "inactive room or variant it already has, but not choose a new one.")
    GameTemplateResponse updateTemplate(@PathVariable long id, @Valid @RequestBody GameTemplateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a template", description = "The games started from it are not affected.")
    void deleteTemplate(@PathVariable long id) {
        service.delete(id);
    }
}
