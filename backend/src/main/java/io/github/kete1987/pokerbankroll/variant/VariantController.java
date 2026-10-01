package io.github.kete1987.pokerbankroll.variant;

import java.net.URI;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import io.github.kete1987.pokerbankroll.catalog.GameType;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/variants")
@Tag(name = "Variants", description = "Sub-types within a game type")
class VariantController {

    private final VariantService service;

    VariantController(VariantService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List variants",
            description = "Ordered by game type, built-in variants first, then user-defined ones by name.")
    List<VariantResponse> list(@RequestParam(required = false) @Nullable GameType gameType,
            @RequestParam(required = false) @Nullable Boolean active) {
        return service.list(gameType, active);
    }

    @PostMapping
    @Operation(summary = "Create a user-defined variant")
    ResponseEntity<VariantResponse> create(@Valid @RequestBody VariantCreateRequest request) {
        VariantResponse variant = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(variant.id());
        return ResponseEntity.created(location).body(variant);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a variant",
            description = "Built-in variants can only be activated or deactivated; user-defined ones can also be renamed.")
    VariantResponse update(@PathVariable long id, @Valid @RequestBody VariantUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a user-defined variant",
            description = "Only when no game uses it; otherwise deactivate it.")
    void delete(@PathVariable long id) {
        service.delete(id);
    }
}
