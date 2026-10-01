package io.github.kete1987.pokerbankroll.game;

import io.github.kete1987.pokerbankroll.common.api.PageResponse;
import io.github.kete1987.pokerbankroll.common.error.ApiException;
import io.github.kete1987.pokerbankroll.common.error.ErrorCode;
import io.github.kete1987.pokerbankroll.room.Room;
import io.github.kete1987.pokerbankroll.room.RoomRepository;
import io.github.kete1987.pokerbankroll.variant.Variant;
import io.github.kete1987.pokerbankroll.variant.VariantRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GameService {

    private final GameRepository games;
    private final RoomRepository rooms;
    private final VariantRepository variants;

    GameService(GameRepository games, RoomRepository rooms, VariantRepository variants) {
        this.games = games;
        this.rooms = rooms;
        this.variants = variants;
    }

    @Transactional(readOnly = true)
    public PageResponse<GameResponse> list(GameFilter filter, int page, int size, Sort sort) {
        return PageResponse.of(
                games.findAll(filter.toSpecification(), PageRequest.of(page, size, sort)),
                GameResponse::of);
    }

    @Transactional(readOnly = true)
    public GameResponse get(long id) {
        return GameResponse.of(find(id));
    }

    public GameResponse create(GameRequest request) {
        Game game = new Game();
        apply(request, game);
        return GameResponse.of(games.saveAndFlush(game));
    }

    public GameResponse update(long id, GameRequest request) {
        Game game = find(id);
        apply(request, game);
        return GameResponse.of(games.saveAndFlush(game));
    }

    public void delete(long id) {
        games.delete(find(id));
    }

    private Game find(long id) {
        return games.findWithRoomAndVariantById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    /** Copies the request into the game; rules within the request itself are already validated. */
    private void apply(GameRequest request, Game game) {
        Room room = rooms.findById(request.roomId())
                .orElseThrow(() -> new ApiException(ErrorCode.UNKNOWN_ROOM, String.valueOf(request.roomId())));
        game.setRoom(room);
        game.setGameType(request.gameType());
        game.setVariant(variantOf(request));
        game.setModality(request.modalityOrDefault());
        game.setPlayedOn(request.playedOn());
        game.setPlayedAt(request.playedAt());
        game.setName(blankToNull(request.name()));
        game.setBuyIn(request.buyIn());
        game.setEntries(request.entriesOrDefault());
        game.setPrize(request.prizeOrZero());
        game.setBounty(request.bountyOrZero());
        game.setTicketPrizeValue(request.ticketPrizeValueOrZero());
        game.setTicketDescription(blankToNull(request.ticketDescription()));
        game.setPaidWithTicket(request.paidWithTicketOrDefault());
        game.setNotes(blankToNull(request.notes()));
    }

    private @Nullable Variant variantOf(GameRequest request) {
        if (request.variantId() == null) {
            return null;
        }
        Variant variant = variants.findById(request.variantId())
                .orElseThrow(() -> new ApiException(ErrorCode.UNKNOWN_VARIANT, String.valueOf(request.variantId())));
        if (variant.getGameType() != request.gameType()) {
            throw new ApiException(ErrorCode.VARIANT_GAME_TYPE_MISMATCH);
        }
        return variant;
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
