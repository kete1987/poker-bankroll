-- Exchange rates, to show amounts in different currencies together, converted to a base currency.
-- Stored amounts never change: every game and movement keeps its amount in its own currency, and
-- amounts are only converted when they are shown together.

-- Rates per 1 EUR, as the European Central Bank publishes them: 1 EUR = rate units of the currency.
-- EUR itself is always 1 and has no rows. Converting X to Y is amount / rate(X) * rate(Y), with the
-- rates of the day of the amount; a day without a rate (weekends, holidays) takes the last one
-- before it.
CREATE TABLE exchange_rate (
    currency_code VARCHAR(3)    NOT NULL REFERENCES currency (code) CHECK (currency_code <> 'EUR'),
    rate_date     DATE          NOT NULL,
    -- ECB: downloaded (ECB data through Frankfurter). MANUAL: typed by the user, for currencies the
    -- ECB does not publish or to correct one; it wins over the ECB rate of the same day.
    source        VARCHAR(10)   NOT NULL CHECK (source IN ('ECB', 'MANUAL')),
    rate          NUMERIC(18,8) NOT NULL CHECK (rate > 0),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- Also the index of the lookups: the last rate of a currency on or before a day.
    PRIMARY KEY (currency_code, rate_date, source)
);

-- How amounts in several currencies are shown together. A single row.
CREATE TABLE currency_setting (
    id                 BOOLEAN     PRIMARY KEY DEFAULT TRUE CHECK (id),
    -- The currency everything is converted to. NULL: automatic, the currency with most games.
    base_currency_code VARCHAR(3)  REFERENCES currency (code),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO currency_setting DEFAULT VALUES;

-- Its only row was a base currency (EUR) that nothing read: the base currency is now the one above,
-- automatic until the user chooses one.
DROP TABLE app_setting;
