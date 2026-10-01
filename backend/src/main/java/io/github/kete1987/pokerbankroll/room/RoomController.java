package io.github.kete1987.pokerbankroll.room;

import java.net.URI;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

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
@RequestMapping("/rooms")
@Tag(name = "Rooms", description = "Poker site accounts")
class RoomController {

    private final RoomService service;

    RoomController(RoomService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List rooms, ordered by name")
    List<RoomResponse> list(@RequestParam(required = false) @Nullable Boolean active) {
        return service.list(active);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a room")
    RoomResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    @Operation(summary = "Create a room")
    ResponseEntity<RoomResponse> create(@Valid @RequestBody RoomRequest request) {
        RoomResponse room = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(room.id());
        return ResponseEntity.created(location).body(room);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a room",
            description = "The currency can only change while the room has no games. Omit `active` to keep it.")
    RoomResponse update(@PathVariable long id, @Valid @RequestBody RoomRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a room", description = "Only rooms without games; otherwise deactivate it.")
    void delete(@PathVariable long id) {
        service.delete(id);
    }
}
