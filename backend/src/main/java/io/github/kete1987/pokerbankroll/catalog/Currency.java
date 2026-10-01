package io.github.kete1987.pokerbankroll.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Currency in which rooms hold money. Read-only: currencies are added by inserting rows. */
@Entity
@Immutable
@Table(name = "currency")
public class Currency {

    /** ISO 4217 code. */
    @Id
    @Column(name = "code", length = 3)
    private String code;

    @Column(name = "symbol", nullable = false, length = 5)
    private String symbol;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "decimals", nullable = false)
    private int decimals;

    protected Currency() {
    }

    public String getCode() {
        return code;
    }

    public String getSymbol() {
        return symbol;
    }

    public int getDecimals() {
        return decimals;
    }
}
