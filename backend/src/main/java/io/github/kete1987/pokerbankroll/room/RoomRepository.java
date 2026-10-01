package io.github.kete1987.pokerbankroll.room;

import java.util.Optional;
import java.util.Set;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface RoomRepository extends JpaRepository<Room, Long> {

    Optional<Room> findByNameIgnoreCase(String name);

    /**
     * Loads the room a game or a bankroll movement is being recorded in, with a shared lock on its
     * row ({@code SELECT ... FOR SHARE}) until the transaction ends. A change of the room's currency
     * then waits for that transaction and is rejected by the {@code room_currency_immutable} trigger,
     * instead of relabelling an amount it could not see yet. Recording in the same room is not blocked.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> findToRecordInById(long id);

    /** Ids of the rooms that have games or bankroll movements. */
    @Query(value = """
            select room_id from game
            union
            select room_id from bankroll_movement where room_id is not null
            """, nativeQuery = true)
    Set<Long> findIdsInUse();

    @Query(value = """
            select exists (select 1 from game where room_id = :id)
                or exists (select 1 from bankroll_movement where room_id = :id)
            """, nativeQuery = true)
    boolean isInUse(long id);
}
