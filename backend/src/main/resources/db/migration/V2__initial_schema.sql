-- Core model: catalogs (currency, game type, modality, variant), rooms, games and settings.
-- Money is always NUMERIC(12,2). Lookup tables use a stable code as primary key; the frontend
-- translates the codes.

-- Currencies in which rooms hold money. Adding one is inserting a row.
CREATE TABLE currency (
    code     VARCHAR(3)  PRIMARY KEY CHECK (code ~ '^[A-Z]{3}$'), -- ISO 4217
    symbol   VARCHAR(5)  NOT NULL,
    -- Decimals shown to the user. Amounts are stored with two, so no more than that.
    decimals SMALLINT    NOT NULL DEFAULT 2 CHECK (decimals BETWEEN 0 AND 2)
);

INSERT INTO currency (code, symbol, decimals) VALUES
    ('EUR', '€', 2),
    ('USD', '$', 2);

-- Format of a game. The application behaves differently per type (e.g. cash games have no
-- entries or bounties), so the list is fixed by migrations, not editable by users.
CREATE TABLE game_type (
    code       VARCHAR(20) PRIMARY KEY CHECK (code ~ '^[A-Z][A-Z0-9_]*$'),
    sort_order SMALLINT    NOT NULL
);

INSERT INTO game_type (code, sort_order) VALUES
    ('TOURNAMENT', 1),
    ('SIT_AND_GO', 2), -- includes spins/lottery Sit&Go such as Expresso
    ('CASH', 3);

-- Poker game being played, independent of the format: any type and variant exists in any modality.
CREATE TABLE modality (
    code       VARCHAR(20) PRIMARY KEY CHECK (code ~ '^[A-Z][A-Z0-9_]*$'),
    sort_order SMALLINT    NOT NULL
);

INSERT INTO modality (code, sort_order) VALUES
    ('NLHE', 1),
    ('PLO', 2);

-- Poker site account. It holds money in one currency; games and movements inherit it.
CREATE TABLE room (
    id            BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name          VARCHAR(100) NOT NULL CHECK (btrim(name) <> ''),
    currency_code VARCHAR(3)   NOT NULL REFERENCES currency (code),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX room_name_key ON room (lower(name));

-- Sub-type within a game type. Known variants have a code (translated by the frontend);
-- variants created by the user have a free-text name instead.
CREATE TABLE variant (
    id             BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    game_type_code VARCHAR(20)  NOT NULL REFERENCES game_type (code),
    code           VARCHAR(40)  CHECK (code ~ '^[A-Z][A-Z0-9_]*$'),
    name           VARCHAR(100) CHECK (btrim(name) <> ''),
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order     SMALLINT     NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT variant_code_xor_name CHECK (num_nonnulls(code, name) = 1),
    CONSTRAINT variant_type_code_key UNIQUE (game_type_code, code),
    -- Target of the foreign key in game that ties a variant to its game type.
    CONSTRAINT variant_id_type_key UNIQUE (id, game_type_code)
);

CREATE UNIQUE INDEX variant_type_name_key ON variant (game_type_code, lower(name)) WHERE name IS NOT NULL;

INSERT INTO variant (game_type_code, code, sort_order) VALUES
    ('TOURNAMENT', 'REGULAR', 1),
    ('TOURNAMENT', 'KO', 2),
    ('TOURNAMENT', 'SPACE_KO', 3),
    ('TOURNAMENT', 'MYSTERY_KO', 4),
    ('SIT_AND_GO', 'REGULAR', 1),
    ('SIT_AND_GO', 'EXPRESSO', 2),
    ('SIT_AND_GO', 'EXPRESSO_NITRO', 3),
    ('SIT_AND_GO', 'DOUBLE_OR_NOTHING', 4),
    ('SIT_AND_GO', 'DOUBLE_OR_NOTHING_DEMENTE', 5),
    ('SIT_AND_GO', 'TRIPLE_OR_NOTHING', 6),
    ('SIT_AND_GO', 'TRIPLE_OR_NOTHING_DEMENTE', 7),
    ('SIT_AND_GO', 'HEADS_UP', 8);

-- One recorded result. Amounts are in the currency of the room.
CREATE TABLE game (
    id                 BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    played_on          DATE          NOT NULL,
    -- Optional local start time, to order several games of the same day.
    played_at          TIME,
    room_id            BIGINT        NOT NULL REFERENCES room (id),
    game_type_code     VARCHAR(20)   NOT NULL REFERENCES game_type (code),
    modality_code      VARCHAR(20)   NOT NULL DEFAULT 'NLHE' REFERENCES modality (code),
    variant_id         BIGINT,
    -- IN_PLAY: registered when it starts, no result yet. FINISHED: the result is known (possibly
    -- nothing won). The application always sets it; the default suits a row inserted with its result.
    status             VARCHAR(20)   NOT NULL DEFAULT 'FINISHED' CHECK (status IN ('IN_PLAY', 'FINISHED')),
    name               VARCHAR(150)  CHECK (btrim(name) <> ''),
    -- Price of one entry, also when it was paid with a ticket. Cash game: amount brought to the table.
    buy_in             NUMERIC(12,2) NOT NULL CHECK (buy_in >= 0),
    -- Entries including re-entries.
    entries            INTEGER       NOT NULL DEFAULT 1 CHECK (entries >= 1),
    -- Cash won, bounties apart. Cash game: amount when leaving the table.
    prize              NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (prize >= 0),
    bounty             NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (bounty >= 0),
    -- Value of a tournament ticket won as a prize. Informative: a ticket is not money until it is
    -- played, so it does not count in net.
    ticket_prize_value NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (ticket_prize_value >= 0),
    ticket_description VARCHAR(150)  CHECK (btrim(ticket_description) <> ''),
    -- One entry was paid with a ticket instead of cash, so it cost no money (re-entries are cash).
    paid_with_ticket   BOOLEAN       NOT NULL DEFAULT FALSE,
    notes              TEXT,
    -- Real money won or lost: cash prizes minus the entries paid in cash. Tickets count neither when
    -- won nor when used, whatever their origin (won, gift...), so the sum of net is always the cash
    -- result and matches the effect on the room balance. Computed by PostgreSQL, so it can never
    -- disagree with the amounts.
    net                NUMERIC(14,2) GENERATED ALWAYS AS (
                           prize + bounty
                           - buy_in * (entries - CASE WHEN paid_with_ticket THEN 1 ELSE 0 END)
                       ) STORED,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- The variant, if any, must be one of the game's type.
    CONSTRAINT game_variant_fkey FOREIGN KEY (variant_id, game_type_code)
        REFERENCES variant (id, game_type_code),
    CONSTRAINT game_ticket_description_needs_value
        CHECK (ticket_description IS NULL OR ticket_prize_value > 0),
    -- A cash game is one sitting: no re-entries, bounties or tickets.
    CONSTRAINT game_cash_fields CHECK (
        game_type_code <> 'CASH'
        OR (entries = 1 AND bounty = 0 AND ticket_prize_value = 0 AND NOT paid_with_ticket)
    ),
    -- A game in play has no result yet.
    CONSTRAINT game_in_play_has_no_result CHECK (
        status <> 'IN_PLAY'
        OR (prize = 0 AND bounty = 0 AND ticket_prize_value = 0 AND ticket_description IS NULL)
    )
);

CREATE INDEX game_played_on_idx ON game (played_on);
-- The games in play are few and listed often.
CREATE INDEX game_in_play_idx ON game (played_on) WHERE status = 'IN_PLAY';
CREATE INDEX game_room_played_on_idx ON game (room_id, played_on);
CREATE INDEX game_type_played_on_idx ON game (game_type_code, played_on);

-- Money put into or taken out of the poker bankroll, apart from the games. It belongs to a room
-- (and is in its currency) or, without a room, to the bankroll of a currency as a whole.
CREATE TABLE bankroll_movement (
    id            BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    occurred_on   DATE          NOT NULL,
    type          VARCHAR(20)   NOT NULL
                  CHECK (type IN ('DEPOSIT', 'WITHDRAWAL', 'BONUS', 'ADJUSTMENT')),
    room_id       BIGINT        REFERENCES room (id),
    currency_code VARCHAR(3)    REFERENCES currency (code),
    -- Positive: the type gives the direction. Only an adjustment can be negative.
    amount        NUMERIC(12,2) NOT NULL,
    notes         TEXT,
    -- Effect on the bankroll.
    signed_amount NUMERIC(12,2) GENERATED ALWAYS AS (
                      CASE WHEN type = 'WITHDRAWAL' THEN -amount ELSE amount END
                  ) STORED,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT bankroll_movement_room_xor_currency CHECK (num_nonnulls(room_id, currency_code) = 1),
    CONSTRAINT bankroll_movement_amount_sign CHECK (amount <> 0 AND (amount > 0 OR type = 'ADJUSTMENT'))
);

CREATE INDEX bankroll_movement_occurred_on_idx ON bankroll_movement (occurred_on);
CREATE INDEX bankroll_movement_room_idx ON bankroll_movement (room_id);

-- Amounts are stored without their currency: they are in the currency of the room. Changing that
-- currency would silently relabel every recorded amount, so it is only allowed while the room has
-- no games or bankroll movements (to fix a mistake right after creating it).
CREATE FUNCTION reject_room_currency_change() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF EXISTS (SELECT 1 FROM game WHERE room_id = OLD.id)
        OR EXISTS (SELECT 1 FROM bankroll_movement WHERE room_id = OLD.id) THEN
        RAISE EXCEPTION 'The currency of room % cannot change because it has games or bankroll movements', OLD.id
            USING ERRCODE = 'check_violation', CONSTRAINT = 'room_currency_immutable';
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER room_currency_immutable
    BEFORE UPDATE OF currency_code ON room
    FOR EACH ROW
    WHEN (OLD.currency_code IS DISTINCT FROM NEW.currency_code)
    EXECUTE FUNCTION reject_room_currency_change();

-- Application-wide preferences (there are no users).
CREATE TABLE app_setting (
    key        VARCHAR(50) PRIMARY KEY CHECK (key ~ '^[a-z][a-z0-9_]*$'),
    value      TEXT        NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO app_setting (key, value) VALUES
    ('base_currency', 'EUR');
