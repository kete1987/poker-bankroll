-- Free-form labels to group games ("challenge", "with friends"...). A game has any number of them.

CREATE TABLE tag (
    id   BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- Stored trimmed; two tags never differ only in case. No ';' nor ',', which separate the tags
    -- of a game in the CSV and Excel files.
    name VARCHAR(40) NOT NULL CHECK (name = btrim(name) AND name <> '' AND name !~ '[;,]')
);

CREATE UNIQUE INDEX tag_name_key ON tag (lower(name));

CREATE TABLE game_tag (
    game_id BIGINT NOT NULL REFERENCES game (id) ON DELETE CASCADE,
    tag_id  BIGINT NOT NULL REFERENCES tag (id) ON DELETE CASCADE,
    PRIMARY KEY (game_id, tag_id)
);

-- The games of a tag: filters, groups and counts go from the tag to its games.
CREATE INDEX game_tag_tag_idx ON game_tag (tag_id);
