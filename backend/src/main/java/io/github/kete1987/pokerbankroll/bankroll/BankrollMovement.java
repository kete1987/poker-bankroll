package io.github.kete1987.pokerbankroll.bankroll;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

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

import io.github.kete1987.pokerbankroll.room.Room;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.generator.EventType;
import org.jspecify.annotations.Nullable;

/**
 * Money put into or taken out of the poker bankroll, apart from the games. It belongs to a room,
 * in whose currency it is, or to no room and then carries its own currency.
 */
@Entity
@Table(name = "bankroll_movement")
public class BankrollMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private MovementType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private @Nullable Room room;

    /** Only set when there is no room. */
    @Column(name = "currency_code", length = 3)
    private @Nullable String currencyCode;

    /** Positive, except for a negative adjustment: the type gives the direction. */
    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "notes", columnDefinition = "text")
    private @Nullable String notes;

    /** Effect on the bankroll, computed by PostgreSQL (see the migration). */
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "signed_amount", insertable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal signedAmount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public BankrollMovement() {
    }

    /** The movement belongs to this room, or to no room when {@code room} is null. */
    public void setOwner(@Nullable Room room, @Nullable String currencyCodeWithoutRoom) {
        this.room = room;
        this.currencyCode = room == null ? currencyCodeWithoutRoom : null;
    }

    /** Currency of the amount: the one of the room, or its own. */
    public String getEffectiveCurrencyCode() {
        return room != null ? room.getCurrencyCode() : currencyCode;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getOccurredOn() {
        return occurredOn;
    }

    public void setOccurredOn(LocalDate occurredOn) {
        this.occurredOn = occurredOn;
    }

    public MovementType getType() {
        return type;
    }

    public void setType(MovementType type) {
        this.type = type;
    }

    public @Nullable Room getRoom() {
        return room;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public @Nullable String getNotes() {
        return notes;
    }

    public void setNotes(@Nullable String notes) {
        this.notes = notes;
    }

    public BigDecimal getSignedAmount() {
        return signedAmount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
