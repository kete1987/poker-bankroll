package io.github.kete1987.pokerbankroll.template;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import io.github.kete1987.pokerbankroll.common.error.ApiException;
import io.github.kete1987.pokerbankroll.common.error.ErrorCode;
import io.github.kete1987.pokerbankroll.room.Room;
import io.github.kete1987.pokerbankroll.room.RoomRepository;
import io.github.kete1987.pokerbankroll.variant.Variant;
import io.github.kete1987.pokerbankroll.variant.VariantRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Templates of games played often. They follow the rules of a game where they apply: a variant of
 * the game type, and no inactive room or variant newly chosen (a template can keep the ones it has).
 */
@Service
@Transactional
public class GameTemplateService {

    /**
     * By label or, without one, by the name of the room (what clients name it after), ignoring
     * case; then by type, buy-in and creation.
     */
    private static final Comparator<GameTemplate> DISPLAY_ORDER = Comparator
            .comparing((GameTemplate template) -> sortKey(template))
            .thenComparing(GameTemplate::getGameType)
            .thenComparing(GameTemplate::getBuyIn)
            .thenComparing(GameTemplate::getId);

    private final GameTemplateRepository templates;
    private final RoomRepository rooms;
    private final VariantRepository variants;

    GameTemplateService(GameTemplateRepository templates, RoomRepository rooms, VariantRepository variants) {
        this.templates = templates;
        this.rooms = rooms;
        this.variants = variants;
    }

    @Transactional(readOnly = true)
    public List<GameTemplateResponse> list() {
        return templates.findAllWithRoomAndVariant().stream()
                .sorted(DISPLAY_ORDER)
                .map(GameTemplateResponse::of)
                .toList();
    }

    public GameTemplateResponse create(GameTemplateRequest request) {
        GameTemplate template = new GameTemplate();
        apply(request, template);
        return GameTemplateResponse.of(templates.saveAndFlush(template));
    }

    public GameTemplateResponse update(long id, GameTemplateRequest request) {
        GameTemplate template = find(id);
        apply(request, template);
        return GameTemplateResponse.of(templates.saveAndFlush(template));
    }

    public void delete(long id) {
        templates.delete(find(id));
    }

    private GameTemplate find(long id) {
        return templates.findById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    private void apply(GameTemplateRequest request, GameTemplate template) {
        template.setLabel(blankToNull(request.label()));
        template.setRoom(roomOf(request, template.getRoom()));
        template.setGameType(request.gameType());
        template.setVariant(variantOf(request, template.getVariant()));
        template.setModality(request.modalityOrDefault());
        template.setGameName(blankToNull(request.name()));
        template.setBuyIn(request.buyIn());
    }

    /** As for a game: an inactive room can be kept by a template, but not newly chosen. */
    private Room roomOf(GameTemplateRequest request, @Nullable Room current) {
        Room room = rooms.findById(request.roomId())
                .orElseThrow(() -> new ApiException(ErrorCode.UNKNOWN_ROOM, String.valueOf(request.roomId())));
        boolean unchanged = current != null && current.getId().equals(room.getId());
        if (!room.isActive() && !unchanged) {
            throw new ApiException(ErrorCode.ROOM_INACTIVE, room.getName());
        }
        return room;
    }

    /** As for a game: a variant of the game type; an inactive one can be kept, not newly chosen. */
    private @Nullable Variant variantOf(GameTemplateRequest request, @Nullable Variant current) {
        if (request.variantId() == null) {
            return null;
        }
        Variant variant = variants.findById(request.variantId())
                .orElseThrow(() -> new ApiException(ErrorCode.UNKNOWN_VARIANT, String.valueOf(request.variantId())));
        if (variant.getGameType() != request.gameType()) {
            throw new ApiException(ErrorCode.VARIANT_GAME_TYPE_MISMATCH);
        }
        boolean unchanged = current != null && current.getId().equals(variant.getId());
        if (!variant.isActive() && !unchanged) {
            throw new ApiException(ErrorCode.VARIANT_INACTIVE);
        }
        return variant;
    }

    private static String sortKey(GameTemplate template) {
        String label = template.getLabel();
        return (label != null ? label : template.getRoom().getName()).toLowerCase(Locale.ROOT);
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
