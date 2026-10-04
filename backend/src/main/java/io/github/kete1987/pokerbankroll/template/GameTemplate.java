package io.github.kete1987.pokerbankroll.template;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import io.github.kete1987.pokerbankroll.catalog.GameType;
import io.github.kete1987.pokerbankroll.catalog.Modality;
import io.github.kete1987.pokerbankroll.room.Room;
import io.github.kete1987.pokerbankroll.variant.Variant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.Nullable;

/**
 * A game played often, kept to start one in a click. The buy-in is in the currency of the room. It
 * does not make its room or variant "in use": the database deletes it with them.
 */
@Entity
@Table(name = "game_template")
public class GameTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "label", length = 80)
    private @Nullable String label;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Enumerated(EnumType.STRING)
    @Column(name = "game_type_code", nullable = false, length = 20)
    private GameType gameType;

    @Enumerated(EnumType.STRING)
    @Column(name = "modality_code", nullable = false, length = 20)
    private Modality modality = Modality.NLHE;

    /** Must be a variant of {@link #gameType}; the database enforces it too. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private @Nullable Variant variant;

    @Column(name = "game_name", length = 150)
    private @Nullable String gameName;

    @Column(name = "buy_in", nullable = false, precision = 12, scale = 2)
    private BigDecimal buyIn;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public GameTemplate() {
    }

    /** Neither its room nor its variant (if any) is inactive: games can be started from it. */
    public boolean isUsable() {
        return room.isActive() && (variant == null || variant.isActive());
    }

    public Long getId() {
        return id;
    }

    public @Nullable String getLabel() {
        return label;
    }

    public void setLabel(@Nullable String label) {
        this.label = label;
    }

    public Room getRoom() {
        return room;
    }

    public void setRoom(Room room) {
        this.room = room;
    }

    public GameType getGameType() {
        return gameType;
    }

    public void setGameType(GameType gameType) {
        this.gameType = gameType;
    }

    public Modality getModality() {
        return modality;
    }

    public void setModality(Modality modality) {
        this.modality = modality;
    }

    public @Nullable Variant getVariant() {
        return variant;
    }

    public void setVariant(@Nullable Variant variant) {
        this.variant = variant;
    }

    public @Nullable String getGameName() {
        return gameName;
    }

    public void setGameName(@Nullable String gameName) {
        this.gameName = gameName;
    }

    public BigDecimal getBuyIn() {
        return buyIn;
    }

    public void setBuyIn(BigDecimal buyIn) {
        this.buyIn = buyIn;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
