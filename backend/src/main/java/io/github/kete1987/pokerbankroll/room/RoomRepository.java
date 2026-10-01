package io.github.kete1987.pokerbankroll.room;

import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RoomRepository extends JpaRepository<Room, Long> {

    Optional<Room> findByNameIgnoreCase(String name);

    /** Ids of the rooms that have games. */
    @Query(value = "select distinct room_id from game", nativeQuery = true)
    Set<Long> findIdsInUse();

    @Query(value = "select exists (select 1 from game where room_id = :id)", nativeQuery = true)
    boolean isInUse(long id);
}
