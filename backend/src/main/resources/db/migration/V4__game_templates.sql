-- Games played often (the Expresso of every evening, a weekly tournament, a cash table), kept to
-- start one in a click. A template is a shortcut, not a record: it does not make its room or
-- variant "in use", and it goes with them when they are deleted. Its buy-in is in the currency of
-- its room, like the one of a game.
CREATE TABLE game_template (
    id             BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- Optional: without it, clients name the template after its room, variant and buy-in.
    label          VARCHAR(80)   CHECK (btrim(label) <> ''),
    room_id        BIGINT        NOT NULL REFERENCES room (id) ON DELETE CASCADE,
    game_type_code VARCHAR(20)   NOT NULL REFERENCES game_type (code),
    modality_code  VARCHAR(20)   NOT NULL DEFAULT 'NLHE' REFERENCES modality (code),
    variant_id     BIGINT,
    -- Name of the games started from it (same limit as game.name).
    game_name      VARCHAR(150)  CHECK (btrim(game_name) <> ''),
    -- Price of one entry. Cash game: amount brought to the table.
    buy_in         NUMERIC(12,2) NOT NULL CHECK (buy_in >= 0),
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- The variant, if any, must be one of the template's type (as for a game).
    CONSTRAINT game_template_variant_fkey FOREIGN KEY (variant_id, game_type_code)
        REFERENCES variant (id, game_type_code) ON DELETE CASCADE
);

CREATE INDEX game_template_room_idx ON game_template (room_id);
CREATE INDEX game_template_variant_idx ON game_template (variant_id);
