package io.github.kete1987.pokerbankroll.catalog;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import io.github.kete1987.pokerbankroll.catalog.CatalogService.Catalog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/catalog")
@Tag(name = "Catalog", description = "Reference data used by the forms")
class CatalogController {

    private final CatalogService service;

    CatalogController(CatalogService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Currencies, game types and modalities, in display order")
    Catalog catalog() {
        return service.catalog();
    }
}
