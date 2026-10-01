package io.github.kete1987.pokerbankroll.variant;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import io.github.kete1987.pokerbankroll.catalog.GameType;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/**
 * Sub-type within a game type. Built-in variants have a {@code code} (translated by the frontend);
 * variants created by the user have a free-text {@code name} instead.
 */
@Entity
@Table(name = "variant")
public class Variant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "game_type_code", nullable = false, updatable = false, length = 20)
    private GameType gameType;

    @Column(name = "code", updatable = false, length = 40)
    private @Nullable String code;

    @Column(name = "name", length = 100)
    private @Nullable String name;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Variant() {
    }

    /** A variant defined by the user. */
    public Variant(GameType gameType, String name) {
        this.gameType = gameType;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public GameType getGameType() {
        return gameType;
    }

    public @Nullable String getCode() {
        return code;
    }

    public @Nullable String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    /** Built-in variants come from the migrations and can only be activated or deactivated. */
    public boolean isBuiltIn() {
        return code != null;
    }
}
