package io.github.kete1987.pokerbankroll.game;

import io.github.kete1987.pokerbankroll.catalog.GameType;
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
        game.setStatus(statusOf(request, GameStatus.IN_PLAY));
        apply(request, game);
        return GameResponse.of(games.saveAndFlush(game));
    }

    public GameResponse update(long id, GameRequest request) {
        Game game = findForUpdate(id);
        game.setStatus(statusOf(request, game.getStatus()));
        apply(request, game);
        return GameResponse.of(games.saveAndFlush(game));
    }

    public void delete(long id) {
        games.delete(findForUpdate(id));
    }

    /** Sets the result of a game in play and finishes it. */
    public GameResponse finish(long id, FinishGameRequest result) {
        Game game = findInPlay(id);
        if (game.getGameType() == GameType.CASH && result.hasTicketOrBounty()) {
            throw new ApiException(ErrorCode.CASH_GAME_RESULT);
        }
        game.setPrize(result.prizeOrZero());
        game.setBounty(result.bountyOrZero());
        game.setTicketPrizeValue(result.ticketPrizeValueOrZero());
        game.setTicketDescription(blankToNull(result.ticketDescription()));
        game.setStatus(GameStatus.FINISHED);
        return GameResponse.of(games.saveAndFlush(game));
    }

    /** Adds one entry, paid in cash, to a tournament or Sit&Go in play. */
    public GameResponse addReEntry(long id) {
        Game game = findInPlay(id);
        if (game.getGameType() == GameType.CASH) {
            throw new ApiException(ErrorCode.RE_ENTRY_NOT_FOR_CASH_GAMES);
        }
        game.setEntries(game.getEntries() + 1);
        return GameResponse.of(games.saveAndFlush(game));
    }

    /** Adds money brought to the table of a cash game in play. */
    public GameResponse addRebuy(long id, RebuyRequest rebuy) {
        Game game = findInPlay(id);
        if (game.getGameType() != GameType.CASH) {
            throw new ApiException(ErrorCode.REBUY_ONLY_FOR_CASH_GAMES);
        }
        game.setBuyIn(game.getBuyIn().add(rebuy.amount()));
        return GameResponse.of(games.saveAndFlush(game));
    }

    private Game findInPlay(long id) {
        Game game = findForUpdate(id);
        if (!game.isInPlay()) {
            throw new ApiException(ErrorCode.GAME_NOT_IN_PLAY);
        }
        return game;
    }

    private Game find(long id) {
        return games.findWithRoomAndVariantById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    /** For every change to a game: its row stays locked until the transaction ends. */
    private Game findForUpdate(long id) {
        return games.findForUpdateById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    /**
     * The status asked for; when it is omitted, a request carrying a result is a finished game and
     * otherwise the given status applies (in play for a new game, the current one for an existing game).
     */
    private static GameStatus statusOf(GameRequest request, GameStatus whenNoResult) {
        if (request.status() != null) {
            return request.status();
        }
        return request.hasResult() ? GameStatus.FINISHED : whenNoResult;
    }

    /** Copies the request into the game; rules within the request itself are already validated. */
    private void apply(GameRequest request, Game game) {
        game.setRoom(roomOf(request, game.getRoom()));
        game.setGameType(request.gameType());
        game.setVariant(variantOf(request, game.getVariant()));
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

    /**
     * Inactive rooms keep their history but take no new games: a game can stay in its room after
     * the room was deactivated, but cannot be created in, or moved to, an inactive one.
     */
    private Room roomOf(GameRequest request, @Nullable Room current) {
        Room room = rooms.findToRecordInById(request.roomId())
                .orElseThrow(() -> new ApiException(ErrorCode.UNKNOWN_ROOM, String.valueOf(request.roomId())));
        boolean unchanged = current != null && current.getId().equals(room.getId());
        if (!room.isActive() && !unchanged) {
            throw new ApiException(ErrorCode.ROOM_INACTIVE, room.getName());
        }
        return room;
    }

    /** Same rule as rooms: an inactive variant can be kept by a game, but not newly chosen. */
    private @Nullable Variant variantOf(GameRequest request, @Nullable Variant current) {
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

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
