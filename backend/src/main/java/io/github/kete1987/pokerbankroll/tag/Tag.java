package io.github.kete1987.pokerbankroll.tag;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Free-form label of games. Names are unique ignoring case: a game is given a tag by its name, and
 * the existing tag is used whatever the capitals it is written with.
 */
@Entity
@Table(name = "tag")
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, length = TagName.MAX_LENGTH)
    private String name;

    protected Tag() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    public TagRef toRef() {
        return new TagRef(id, name);
    }
}
