package io.github.kete1987.pokerbankroll.variant;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import io.github.kete1987.pokerbankroll.catalog.GameType;
import io.github.kete1987.pokerbankroll.common.error.ApiException;
import io.github.kete1987.pokerbankroll.common.error.ErrorCode;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class VariantService {

    /** Game type order, then built-in variants in their own order, then user-defined ones by name. */
    private static final Comparator<Variant> DISPLAY_ORDER = Comparator
            .comparing(Variant::getGameType)
            .thenComparing(variant -> !variant.isBuiltIn())
            .thenComparing(Variant::getSortOrder)
            .thenComparing(variant -> variant.getName() == null ? "" : variant.getName().toLowerCase());

    private final VariantRepository variants;

    VariantService(VariantRepository variants) {
        this.variants = variants;
    }

    @Transactional(readOnly = true)
    public List<VariantResponse> list(@Nullable GameType gameType, @Nullable Boolean active) {
        Set<Long> inUse = variants.findIdsInUse();
        return variants.findAll().stream()
                .filter(variant -> gameType == null || variant.getGameType() == gameType)
                .filter(variant -> active == null || variant.isActive() == active)
                .sorted(DISPLAY_ORDER)
                .map(variant -> VariantResponse.of(variant, inUse.contains(variant.getId())))
                .toList();
    }

    public VariantResponse create(VariantCreateRequest request) {
        String name = request.name().strip();
        checkNameIsFree(request.gameType(), name, null);
        return VariantResponse.of(variants.saveAndFlush(new Variant(request.gameType(), name)), false);
    }

    public VariantResponse update(long id, VariantUpdateRequest request) {
        Variant variant = find(id);
        if (variant.isBuiltIn()) {
            if (request.name() != null) {
                throw new ApiException(ErrorCode.VARIANT_BUILT_IN);
            }
        } else if (request.name() != null) {
            String name = request.name().strip();
            checkNameIsFree(variant.getGameType(), name, id);
            variant.setName(name);
        }
        variant.setActive(request.active());
        return VariantResponse.of(variants.saveAndFlush(variant), variants.isInUse(id));
    }

    public void delete(long id) {
        Variant variant = find(id);
        if (variant.isBuiltIn()) {
            throw new ApiException(ErrorCode.VARIANT_BUILT_IN);
        }
        if (variants.isInUse(id)) {
            throw new ApiException(ErrorCode.VARIANT_IN_USE);
        }
        variants.delete(variant);
    }

    private Variant find(long id) {
        return variants.findById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    private void checkNameIsFree(GameType gameType, String name, @Nullable Long ownId) {
        variants.findByGameTypeAndNameIgnoreCase(gameType, name)
                .filter(other -> !other.getId().equals(ownId))
                .ifPresent(other -> {
                    throw new ApiException(ErrorCode.VARIANT_NAME_TAKEN, name);
                });
    }
}
