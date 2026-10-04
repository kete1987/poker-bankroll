package io.github.kete1987.pokerbankroll.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import io.github.kete1987.pokerbankroll.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Checks the tables of the exchange rates and the base currency (migration V5). Statements run in
 * auto-commit, so the rows created by each test are deleted afterwards.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ExchangeRateSchemaTests {

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void deleteTestData() {
        jdbc.update("delete from exchange_rate");
        jdbc.update("update currency_setting set base_currency_code = null");
    }

    @Test
    void aRateIsPositiveOfACurrencyOtherThanTheEuro() {
        insert("USD", "2026-10-01", "ECB", "1.12345678");
        assertThatThrownBy(() -> insert("EUR", "2026-10-01", "ECB", "1"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert("XYZ", "2026-10-01", "ECB", "1"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert("USD", "2026-10-02", "ECB", "0"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insert("USD", "2026-10-02", "OTHER", "1"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("select rate from exchange_rate", BigDecimal.class))
                .isEqualByComparingTo("1.12345678");
    }

    @Test
    void aCurrencyHasOneRateADayOfEachSource() {
        insert("USD", "2026-10-01", "ECB", "1.10");
        insert("USD", "2026-10-01", "MANUAL", "1.12");
        assertThatThrownBy(() -> insert("USD", "2026-10-01", "MANUAL", "1.13"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theBaseCurrencyIsOneRowAutomaticUntilChosen() {
        assertThat(jdbc.queryForObject("select count(*) from currency_setting", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select base_currency_code from currency_setting", String.class)).isNull();
        assertThatThrownBy(() -> jdbc.update("insert into currency_setting (id) values (false)"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into currency_setting default values"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update currency_setting set base_currency_code = 'XYZ'"))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("update currency_setting set base_currency_code = 'USD'");
    }

    @Test
    void theOldSettingsTableIsGone() {
        assertThat(jdbc.queryForObject("select to_regclass('app_setting') is null", Boolean.class)).isTrue();
    }

    private void insert(String currency, String day, String source, String rate) {
        jdbc.update("insert into exchange_rate (currency_code, rate_date, source, rate) values (?, ?::date, ?, ?)",
                currency, day, source, new BigDecimal(rate));
    }
}
