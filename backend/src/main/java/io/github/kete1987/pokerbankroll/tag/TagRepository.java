package io.github.kete1987.pokerbankroll.tag;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface TagRepository extends JpaRepository<Tag, Long> {

    /**
     * Creates the tag unless there is one with that name ignoring case. Two requests creating the
     * same tag at once both succeed: the second waits for the first and inserts nothing.
     */
    @Modifying
    @Query(value = "insert into tag (name) values (:name) on conflict ((lower(name))) do nothing", nativeQuery = true)
    int insertIfAbsent(String name);

    /** Every tag with the number of games that have it. */
    @Query(value = """
            select t.id, t.name, count(gt.game_id) as games
            from tag t left join game_tag gt on gt.tag_id = t.id
            group by t.id, t.name
            """, nativeQuery = true)
    List<TagUse> findAllWithGames();

    @Query(value = "select count(*) from game_tag where tag_id = :id", nativeQuery = true)
    long countGames(long id);

    /** Gives the games of one tag the other one (those that already have both keep it once). */
    @Modifying
    @Query(value = """
            insert into game_tag (game_id, tag_id)
            select game_id, :target from game_tag where tag_id = :source
            on conflict do nothing
            """, nativeQuery = true)
    int copyGames(long source, long target);

    /** A row of {@link #findAllWithGames}. */
    interface TagUse {

        long getId();

        String getName();

        long getGames();
    }
}
