package io.github.kete1987.pokerbankroll.room;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import io.github.kete1987.pokerbankroll.catalog.CurrencyRepository;
import io.github.kete1987.pokerbankroll.common.error.ApiException;
import io.github.kete1987.pokerbankroll.common.error.ErrorCode;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RoomService {

    private final RoomRepository rooms;
    private final CurrencyRepository currencies;

    RoomService(RoomRepository rooms, CurrencyRepository currencies) {
        this.rooms = rooms;
        this.currencies = currencies;
    }

    /** Rooms ordered by name; {@code active} filters by that flag when given. */
    @Transactional(readOnly = true)
    public List<RoomResponse> list(@Nullable Boolean active) {
        Set<Long> inUse = rooms.findIdsInUse();
        return rooms.findAll().stream()
                .filter(room -> active == null || room.isActive() == active)
                .sorted(Comparator.comparing(room -> room.getName().toLowerCase()))
                .map(room -> RoomResponse.of(room, inUse.contains(room.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public RoomResponse get(long id) {
        return RoomResponse.of(find(id), rooms.isInUse(id));
    }

    public RoomResponse create(RoomRequest request) {
        String name = request.name().strip();
        checkNameIsFree(name, null);
        checkCurrencyExists(request.currencyCode());

        Room room = new Room(name, request.currencyCode());
        room.setActive(request.active() == null || request.active());
        return RoomResponse.of(rooms.saveAndFlush(room), false);
    }

    public RoomResponse update(long id, RoomRequest request) {
        Room room = find(id);
        boolean inUse = rooms.isInUse(id);
        String name = request.name().strip();
        checkNameIsFree(name, id);
        if (!room.getCurrencyCode().equals(request.currencyCode())) {
            // Amounts are stored without currency: changing it would relabel the whole history.
            if (inUse) {
                throw new ApiException(ErrorCode.ROOM_CURRENCY_LOCKED);
            }
            checkCurrencyExists(request.currencyCode());
            room.setCurrencyCode(request.currencyCode());
        }
        room.setName(name);
        if (request.active() != null) {
            room.setActive(request.active());
        }
        return RoomResponse.of(rooms.saveAndFlush(room), inUse);
    }

    public void delete(long id) {
        Room room = find(id);
        if (rooms.isInUse(id)) {
            throw new ApiException(ErrorCode.ROOM_IN_USE);
        }
        rooms.delete(room);
    }

    private Room find(long id) {
        return rooms.findById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    private void checkNameIsFree(String name, @Nullable Long ownId) {
        rooms.findByNameIgnoreCase(name)
                .filter(other -> !other.getId().equals(ownId))
                .ifPresent(other -> {
                    throw new ApiException(ErrorCode.ROOM_NAME_TAKEN, name);
                });
    }

    private void checkCurrencyExists(String code) {
        if (!currencies.existsById(code)) {
            throw new ApiException(ErrorCode.UNKNOWN_CURRENCY, code);
        }
    }
}
