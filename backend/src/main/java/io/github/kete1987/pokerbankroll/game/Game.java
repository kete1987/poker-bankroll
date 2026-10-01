package io.github.kete1987.pokerbankroll.game;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

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
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.generator.EventType;
import org.jspecify.annotations.Nullable;

/** One recorded result. Amounts are in the currency of the room. */
@Entity
@Table(name = "game")
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "played_on", nullable = false)
    private LocalDate playedOn;

    /** Optional local start time, only used to order the games of a day. */
    @Column(name = "played_at")
    private @Nullable LocalTime playedAt;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private GameStatus status = GameStatus.IN_PLAY;

    @Column(name = "name", length = 150)
    private @Nullable String name;

    @Column(name = "buy_in", nullable = false, precision = 12, scale = 2)
    private BigDecimal buyIn;

    @Column(name = "entries", nullable = false)
    private int entries = 1;

    @Column(name = "prize", nullable = false, precision = 12, scale = 2)
    private BigDecimal prize = BigDecimal.ZERO;

    @Column(name = "bounty", nullable = false, precision = 12, scale = 2)
    private BigDecimal bounty = BigDecimal.ZERO;

    @Column(name = "ticket_prize_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal ticketPrizeValue = BigDecimal.ZERO;

    @Column(name = "ticket_description", length = 150)
    private @Nullable String ticketDescription;

    @Column(name = "paid_with_ticket", nullable = false)
    private boolean paidWithTicket;

    @Column(name = "notes", columnDefinition = "text")
    private @Nullable String notes;

    /** Real money won or lost, computed by PostgreSQL (see the migration). */
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "net", insertable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal net;

    /**
     * Money won, as an expression of the database: only there to order and filter by it. Read it
     * with {@link #getWon()}, which follows changes not saved yet.
     */
    @Formula("prize + bounty")
    private BigDecimal won;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Game() {
    }

    /** Money paid for the entries: the one paid with a ticket, if any, cost nothing. */
    public BigDecimal getInvested() {
        return buyIn.multiply(BigDecimal.valueOf(entries - (paidWithTicket ? 1 : 0)));
    }

    public Long getId() {
        return id;
    }

    public LocalDate getPlayedOn() {
        return playedOn;
    }

    public void setPlayedOn(LocalDate playedOn) {
        this.playedOn = playedOn;
    }

    public @Nullable LocalTime getPlayedAt() {
        return playedAt;
    }

    public void setPlayedAt(@Nullable LocalTime playedAt) {
        this.playedAt = playedAt;
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

    public GameStatus getStatus() {
        return status;
    }

    public void setStatus(GameStatus status) {
        this.status = status;
    }

    public boolean isInPlay() {
        return status == GameStatus.IN_PLAY;
    }

    public @Nullable String getName() {
        return name;
    }

    public void setName(@Nullable String name) {
        this.name = name;
    }

    public BigDecimal getBuyIn() {
        return buyIn;
    }

    public void setBuyIn(BigDecimal buyIn) {
        this.buyIn = buyIn;
    }

    public int getEntries() {
        return entries;
    }

    public void setEntries(int entries) {
        this.entries = entries;
    }

    public BigDecimal getPrize() {
        return prize;
    }

    public void setPrize(BigDecimal prize) {
        this.prize = prize;
    }

    public BigDecimal getBounty() {
        return bounty;
    }

    public void setBounty(BigDecimal bounty) {
        this.bounty = bounty;
    }

    public BigDecimal getTicketPrizeValue() {
        return ticketPrizeValue;
    }

    public void setTicketPrizeValue(BigDecimal ticketPrizeValue) {
        this.ticketPrizeValue = ticketPrizeValue;
    }

    public @Nullable String getTicketDescription() {
        return ticketDescription;
    }

    public void setTicketDescription(@Nullable String ticketDescription) {
        this.ticketDescription = ticketDescription;
    }

    public boolean isPaidWithTicket() {
        return paidWithTicket;
    }

    public void setPaidWithTicket(boolean paidWithTicket) {
        this.paidWithTicket = paidWithTicket;
    }

    public @Nullable String getNotes() {
        return notes;
    }

    public void setNotes(@Nullable String notes) {
        this.notes = notes;
    }

    /** Money won: the prize plus the bounties. */
    public BigDecimal getWon() {
        return prize.add(bounty);
    }

    public BigDecimal getNet() {
        return net;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
