# Importing games

Games recorded somewhere else (a spreadsheet, another tool) can be loaded from a **CSV file** in the
format described here. The application does not know any other format: convert your data to this
one first. [`import-example.csv`](../frontend/public/import-example.csv) is a file to start from; the *Import*
section of the application offers it for download.

Only games are imported. Bankroll movements (deposits, withdrawals, bonuses) are entered by hand.

The **CSV that the games list exports is this same format** (see [Exported files](#exported-files)),
so it can be imported as it is.

## The file

- **CSV**, UTF-8 (with or without byte order mark), values separated by **commas**.
- Values with a comma, a quote or a line break go between double quotes, and a quote inside them is
  written twice (`"Fish, chips & ""more"""`), as any spreadsheet does when it saves a CSV.
- The **first row names the columns**. They can come in any order and the optional ones can be left
  out, but a column that is not in the table below is an error: a misspelt name must not lose data
  silently.
- **One game per row.** Empty rows are skipped.
- At most 5 MB and 50,000 rows.

## Columns

| Column | Required | Value |
|---|---|---|
| `playedOn` | yes | Date, `2026-01-31` |
| `playedAt` | no | Local start time, `21:30` |
| `room` | yes | Name of the room. Upper and lower case are the same |
| `currency` | no | ISO code (`EUR`, `USD`) of the room. Needed to create a room that does not exist |
| `gameType` | yes | `TOURNAMENT`, `SIT_AND_GO` (also spins such as Expresso) or `CASH` |
| `variant` | no | Code of a built-in variant (below) or name of one of your own |
| `modality` | no | `NLHE` (default) or `PLO` |
| `name` | no | Name of the tournament |
| `buyIn` | yes | Price of one entry. Cash game: money brought to the table |
| `entries` | no | Entries including re-entries. Default 1 |
| `prize` | no | Cash won, bounties apart. Cash game: money when leaving the table. Default 0 |
| `bounty` | no | Bounties won. Default 0 |
| `ticketPrizeValue` | no | Value of a ticket **won** as a prize. Default 0 |
| `ticketDescription` | no | What the ticket won is for. Needs `ticketPrizeValue` |
| `paidWithTicket` | no | `true` when one entry was paid with a ticket instead of cash. Default `false` |
| `notes` | no | Free text |
| `tags` | no | Tags of the game separated by semicolons: `Challenge;With friends` |

Amounts are plain numbers with a point for decimals and at most two of them: `1234.56`, never
`1.234,56 €`. They are in the currency of the room; no symbol is written.

### Rooms

A row belongs to the room named in `room`. A room that does not exist yet is **created** with the
currency given in `currency` by any of its rows; without one, its rows are errors. For a room that
exists `currency` is optional, and it is an error if it is not the currency of the room.

Two currencies on the same site are two rooms, with different names.

### Variants

`variant` is looked up among the variants of the game type of the row: first as the code of a
built-in one, then as the name of one of yours. If there is none, it is **created** as a variant of
your own for that game type.

| Game type | Built-in codes |
|---|---|
| `TOURNAMENT` | `REGULAR`, `KO`, `SPACE_KO`, `MYSTERY_KO` |
| `SIT_AND_GO` | `REGULAR`, `EXPRESSO`, `EXPRESSO_NITRO`, `DOUBLE_OR_NOTHING`, `DOUBLE_OR_NOTHING_DEMENTE`, `TRIPLE_OR_NOTHING`, `TRIPLE_OR_NOTHING_DEMENTE`, `HEADS_UP` |
| `CASH` | none |

The current list, with the variants you have added, is in *Settings → Variants*.

### Tags

`tags` holds the tags of the game separated by **semicolons** (`;`); spaces around each one are
removed and empty ones are skipped. Each tag has at most 40 characters, no commas, and a game at
most 10 tags.
A tag is looked up among the existing ones ignoring upper and lower case, and **created** when there
is none. Tags are in *Settings → Tags*.

## Rules

Rows follow the same rules as games recorded in the application:

- Every game is imported as **finished**.
- A cash game has one entry and no bounty or ticket columns.
- Inactive rooms and variants take no games: activate them before importing.
- Tickets are not money: a ticket won does not count in the result, and an entry paid with one costs
  nothing. See the glossary in [`AGENTS.md`](../AGENTS.md#domain-glossary).

## How it works

The **Import** section of the application takes the file and first shows what it contains — games
per type, dates, the net result per currency, the rooms and variants it would create — and the
errors, row by row (rows are numbered as in a spreadsheet: the header is row 1).

The import is **all or nothing**: while any row has an error nothing is stored. Fix the file and
load it again.

The application does not look for duplicates: **importing the same file twice records its games
twice**.

With the API it is `POST /api/imports/games` with the file as the body (`Content-Type: text/csv`);
`?dryRun=true` only checks it:

```bash
curl -X POST -H "Content-Type: text/csv" --data-binary @games.csv   "http://localhost:8080/api/imports/games?dryRun=true"
```

## Exported files

The *Export* button of the games list writes, as **CSV**, a file in this format with every column
and the finished games that match the filters of the list, oldest first. Rooms go by name with
their `currency`, variants by code (built-in) or by name (yours).

- Imported into an **empty** installation it records the same games, creating the rooms and the
  variants of your own. It is a way to move your games to another installation, not a backup: the
  bankroll movements, the logos and what is inactive are not in it (see [backups](backups.md)).
- **Games in play are not exported**: they have no result yet, and an import would record them as
  finished.
- Imported into the installation it came from, it records the games a second time.
- A file with more than 50,000 games is over the limit of an import: export it by periods.

The Excel file of the same button is made to be read, not to be imported: it has translated
headers and values, two computed columns (invested and net), and the tags separated by commas. The bankroll screen exports its
movements too (CSV or Excel), but there is no import for them.

With the API it is `GET /api/exports/games?format=CSV` (or `XLSX`) with the filters of
`GET /api/games`, and `GET /api/exports/movements` with those of `GET /api/bankroll/movements`.
