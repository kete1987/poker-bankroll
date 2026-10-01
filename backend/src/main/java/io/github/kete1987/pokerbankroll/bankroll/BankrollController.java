package io.github.kete1987.pokerbankroll.bankroll;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import io.github.kete1987.pokerbankroll.common.api.PageResponse;
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
@RequestMapping("/bankroll")
@Tag(name = "Bankroll", description = "Money set aside for poker and what was won or lost with it")
class BankrollController {

    static final int MAX_PAGE_SIZE = 200;

    private final BankrollService service;

    BankrollController(BankrollService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    @Operation(summary = "Poker bankroll per currency and room",
            description = "bankroll = deposited - withdrawn + adjustments + result, with result = net of the "
                    + "games + bonuses. It is not the balance of the room account: with no movements recorded "
                    + "it is just the result, negative when losing.")
    BankrollSummaryResponse summary(
            @Parameter(description = "Only movements and games on or after this date: with dates, the "
                    + "figures are those of the period (what the bankroll changed, what was won or lost)")
            @RequestParam(required = false) @Nullable LocalDate from,
            @Parameter(description = "Only movements and games on or before this date")
            @RequestParam(required = false) @Nullable LocalDate to,
            @Parameter(description = "One or more rooms: only they are listed and added up, without the "
                    + "movements that belong to no room")
            @RequestParam(required = false) @Nullable List<Long> roomId) {
        return service.summary(from, to, roomId);
    }

    @GetMapping("/movements")
    @Operation(summary = "List bankroll movements, newest first",
            description = "All filters are optional and combined with AND.")
    PageResponse<MovementResponse> list(
            @Parameter(description = "On or after this date")
            @RequestParam(required = false) @Nullable LocalDate from,
            @Parameter(description = "On or before this date")
            @RequestParam(required = false) @Nullable LocalDate to,
            @RequestParam(required = false) @Nullable MovementType type,
            @RequestParam(required = false) @Nullable Long roomId,
            @Parameter(description = "true: only movements that belong to no room; false: only those of a room")
            @RequestParam(required = false) @Nullable Boolean withoutRoom,
            @Parameter(description = "Currency of the amount, e.g. EUR")
            @RequestParam(required = false) @Nullable String currency,
            @Parameter(description = "Zero-based page number")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return service.list(new MovementFilter(from, to, type, roomId, withoutRoom, currency), page, size);
    }

    @GetMapping("/movements/{id}")
    @Operation(summary = "Get a bankroll movement")
    MovementResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping("/movements")
    @Operation(summary = "Record a bankroll movement",
            description = "Of a room (`roomId`), or of the bankroll as a whole (`currencyCode`, no room).")
    @ApiResponse(responseCode = "201", description = "Created")
    ResponseEntity<MovementResponse> create(@Valid @RequestBody MovementRequest request) {
        MovementResponse movement = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(movement.id());
        return ResponseEntity.created(location).body(movement);
    }

    @PutMapping("/movements/{id}")
    @Operation(summary = "Update a bankroll movement", description = "Replaces every field.")
    MovementResponse update(@PathVariable long id, @Valid @RequestBody MovementRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/movements/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a bankroll movement")
    void delete(@PathVariable long id) {
        service.delete(id);
    }
}
