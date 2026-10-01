package io.github.kete1987.pokerbankroll.room;

import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RoomRepository extends JpaRepository<Room, Long> {

    Optional<Room> findByNameIgnoreCase(String name);

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
