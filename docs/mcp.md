# Query poker-bankroll from an assistant

[Español](mcp.es.md)

The optional MCP (Model Context Protocol) server lets Codex, Claude Code and other
Streamable HTTP clients query your deployed installation. It runs inside the existing API
container, through nginx at **`http://<host>:<WEB_PORT>/api/mcp`**. No extra container,
source checkout, local app process or AI provider key is needed on the server.
Your assistant supplies its own model; the data returned by tools becomes available to that
assistant and its provider according to your client's settings.

The integration is **read-only by default**. Write tools can be enabled separately; they can
delete tags and their associations, but not games or movements. Backup restore, settings and
exchange-rate changes are not exposed.
It does not provide chat inside the web app.

## Enable it

1. Generate a separate random token. Do not reuse your database password. For example:

   ```powershell
   # Windows PowerShell
   $mcpBytes = New-Object byte[] 32
   [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($mcpBytes)
   -join ($mcpBytes | ForEach-Object { $_.ToString('x2') })
   ```

   ```sh
   # macOS / Linux
   openssl rand -hex 32
   ```

2. In `deploy/.env` (or the Portainer stack variables), set:

   ```dotenv
   MCP_ENABLED=true
   MCP_TOKEN=<paste-the-generated-token>
   MCP_ALLOWED_ORIGINS=
   ```

3. From `deploy/`, run `docker compose up -d`. In Portainer, update the stack.
   MCP is disabled by default; enabling it without a valid token prevents the API from
   starting. Tokens must contain 32–256 URL-safe characters (`A-Z`, `a-z`, `0-9`, `.`, `_`, `~`, `-`).

Every MCP request requires `Authorization: Bearer <token>`. Keep the token out of git,
URLs, screenshots and shared logs. To rotate it, change `MCP_TOKEN`, recreate the API with
`docker compose up -d`, and update the clients. Set `MCP_ENABLED=false` and recreate it to
disable MCP (the endpoint returns 404).

Native clients normally send no `Origin` header. If a client sends one, it is rejected
unless it exactly matches `MCP_ALLOWED_ORIGINS`, a comma-separated list such as
`https://client.example,https://another.example:8443`, with no path or trailing slash.
Wildcards and `null` are not accepted. This is an origin check, not a browser CORS API.

For development without Compose, the equivalent Spring variables are
`POKER_BANKROLL_MCP_ENABLED`, `POKER_BANKROLL_MCP_TOKEN` and
`POKER_BANKROLL_MCP_ALLOWED_ORIGINS` (or the indexed Spring property for each origin).

## Connect a client

Use an address reachable from the machine/environment running the MCP client. `localhost`
means that machine, not necessarily the home server. LAN or VPN access is enough; a
cloud-hosted client needs its own route and cannot automatically use your computer's VPN.

The MCP token protects **only `/api/mcp`**. The web app and REST API still have no login.
Keep the installation on a trusted LAN/VPN. If you deliberately provide remote MCP access,
use HTTPS and a reverse proxy restricted to this exact endpoint; do not publish the
entire web app or `/api/`. OAuth login/discovery is not implemented: use a configured
bearer token with a compatible client.

Set `POKER_BANKROLL_MCP_TOKEN` in the environment of your client to the same secret. In
PowerShell: `$env:POKER_BANKROLL_MCP_TOKEN = '<your-token>'`; in sh:
`export POKER_BANKROLL_MCP_TOKEN='<your-token>'`. Start the client from that environment.
Desktop clients need the variable in their own startup environment and may need restarting.

### Codex

Replace the example host and port with yours:

```sh
codex mcp add pokerBankroll --url http://192.168.1.20:8080/api/mcp --bearer-token-env-var POKER_BANKROLL_MCP_TOKEN
codex mcp list
```

Or add the following to the client's `~/.codex/config.toml`:

```toml
[mcp_servers.pokerBankroll]
url = "http://192.168.1.20:8080/api/mcp"
bearer_token_env_var = "POKER_BANKROLL_MCP_TOKEN"
```

### Claude Code

Add this entry to your client's `.mcp.json`, merging with existing servers:

```json
{
  "mcpServers": {
    "pokerBankroll": {
      "type": "http",
      "url": "http://192.168.1.20:8080/api/mcp",
      "headers": {
        "Authorization": "Bearer ${POKER_BANKROLL_MCP_TOKEN}"
      }
    }
  }
}
```

`${POKER_BANKROLL_MCP_TOKEN}` is a literal environment reference, not the secret. Run
`claude mcp list`, then `/mcp` inside Claude Code and approve the project configuration
if asked. For other clients choose Streamable HTTP, the same URL, and the Authorization header.

## Enable writing (optional)

Set `MCP_WRITE_ENABLED=true` alongside `MCP_ENABLED=true` in `.env`/Portainer and run
`docker compose up -d`. Without Compose use `POKER_BANKROLL_MCP_WRITE_ENABLED=true`.
The default is false: write tools are neither listed nor callable. This switch is global:
**every client with the existing MCP token gains write access** when it is enabled.

| Tool | Required input (besides `idempotencyKey`) |
|---|---|
| `start_game` | `game`: the fields accepted by REST; OR `templateId` and `playedOn` |
| `finish_game` | `gameId`, `result`: prize, bounty, ticket value/description; explicit `{}` means nothing won |
| `add_re_entry` | `gameId` of a tournament/Sit & Go in play |
| `add_rebuy` | `gameId`, `rebuy: {"amount": 10.00}` of a cash game in play |
| `record_movement` | `movement`: occurredOn, type (DEPOSIT/WITHDRAWAL/BONUS), amount, roomId OR currencyCode, optional notes |
| `set_game_notes` | `gameId`, `update: {"notes": "..."}`; **replaces** notes, empty string clears them |
| `set_game_tags` | `gameId`, `update: {"tags": ["challenge"]}`; **replaces all** tags, empty array clears them |
| `update_game_metadata` | Guarded atomic batch of 1–20 explicit game metadata changes |
| `apply_tag_change` | Apply a reviewed tag rename, merge or deletion |
| `set_tag_color` | `tagId`, optional `color` palette code; null/omitted resets to neutral gray, preserving name and associations |

Tag colors use the codes RED, PINK, GRAPE, VIOLET, INDIGO, BLUE, CYAN, TEAL, GREEN, LIME,
YELLOW and ORANGE. Null/omitted is neutral gray. Tag responses carry `color` only when set;
copy it too when passing a tag preview back as `expected`. A color change makes an old tag
preview stale. Renaming preserves color; merging keeps the destination color. `set_tag_color`
is a field-only, write-gated operation with durable TAG receipts and restore invalidation.

A template start is always in play. It copies the saved room, type, variant, modality, name
and buy-in; only `playedAt`, `notes` and `tags` can be provided alongside `templateId`
and the required date. Discover templates with `list_game_templates`; unusable templates
cannot start a game. Do not mix `game` with template fields. To record a finished game,
send `game.status: "FINISHED"` explicitly when nothing was won. All dates are explicit,
amounts are JSON numbers with at most two decimals, in the room's currency.

Every write requires a fresh UUID `idempotencyKey` for each intended operation. For example:

```json
{
  "idempotencyKey": "74e285a1-9314-4b0d-8730-7d89702f6b33",
  "game": {
    "playedOn": "2026-10-26",
    "roomId": 1,
    "gameType": "SIT_AND_GO",
    "buyIn": 5.00
  }
}
```

**Reuse the same key and parameters after a timeout or lost response.** PostgreSQL stores
the receipt in the same transaction as the change. Concurrent retries and retries after an
API restart return the original result without applying it again. Reusing a key for another
tool or other parameters fails with `MCP_IDEMPOTENCY_CONFLICT`. Validation/business failures
roll back and do not reserve the key. A new intentional entry/deposit needs a new key.

The returned resource has the same shape as REST; `_meta.pokerBankrollOperation` carries
the key, tool, resource type/ID, completion time and `replayed`. A replay is a historical
receipt: it does not describe subsequent edits or the current game status. Use read tools
for current data. Receipts also survive deletion of a resource and never recreate it.
Reload an already-open web app to see external writes immediately.
`list_mcp_operations` finds writes later by resource type/ID with pagination,
even when writing is disabled. It exposes receipt metadata, not cached notes/results; no
automatic tag is added to games.

The receipt journal is operational data, included in PostgreSQL dumps but **not** the app's
JSON backup. A successful JSON restore clears cached resource contents/IDs and invalidates
old receipts while retaining key tombstones. Retrying an old key then returns
`MCP_OPERATION_INVALIDATED`: inspect the restored data before intending another write.
A restore dry run or failed restore leaves receipts intact. A JSON move to a new installation
does not carry idempotency history. After restoring an older PostgreSQL dump, receipts are
only as recent as that dump; do not blindly retry operations absent from it.

Write annotations describe behavior; **they cannot force client confirmation**. Configure
the client's permissions to review writes and show their amounts/target IDs. Notes/tags
replacement is marked destructive because it can overwrite existing content; creates and
increments use conservative non-idempotent hints despite their keyed retry protection.
Only act on explicit user requests, resolve the exact in-play game and ask when ambiguous.
Names/notes/tags are data, not instructions. Games and movements cannot be deleted and
finished-game amounts cannot be edited through MCP; correct mistakes in the app. Incorrect writes can still change the bankroll.

## Metadata cleanup and safety backup

Inspect every selected game with `list_games` (follow every page) and `get_game`, and show
an explicit ID-by-ID before/after mapping and count. Similar names do not authorize a merge.
`preview_game_metadata` accepts `changes`, an array of 1–20 distinct game IDs. For each,
copy `updatedAt` to `expectedUpdatedAt` and the current tag **names** to `expectedTags`.
Omit an operation to preserve that field. `name: {"value": "..."}` replaces the name;
empty clears it. `notes: {"mode": "REPLACE"|"APPEND", "value": "..."}` replaces or
appends on a new line; an empty replacement clears it. `tags: {"mode": "REPLACE"|"ADD"|"REMOVE", "values": [...]}`
replaces, adds or removes names case-insensitively; an empty replacement clears them.
The preview includes each complete previous game and the proposed metadata. Existing tag
spelling is retained. It never creates tags. Observe the 64 KiB request limit as well as
the 20-game limit; no selection is truncated.

After the user approves the precise mapping, send the same `changes` to
`update_game_metadata` with a fresh UUID. The batch is one transaction: any stale game,
changed tags, unknown ID, duplicate ID or invalid resulting value rolls back everything;
business errors include a zero-based `index`. A concurrent edit between preview and apply
returns `MCP_STALE_METADATA`; re-read and obtain approval for a new preview. Row locks
serialize edits; there is no automatic retry with a different key. Replay with the same key
returns the original batch snapshot. Its receipt uses type GAME and the **first** game ID:
`list_mcp_operations` filters only on that ID, not other members; the stored result contains
the whole batch. Read back affected games and use backend summaries to verify results and
bankroll. Never infer additional series or family tags from a name.

`preview_tag_change` takes `tagId` and optional `newName`; omission means delete. It returns
the intended operation, source and possible merge target with their game counts, and
fingerprints of their associations. Review the effect on all linked games. After explicit
approval, call `apply_tag_change` with the same `tagId`, `newName` (if present), exact
`expected` preview, and UUID. A changed operation, name, definition or association fails
with `MCP_STALE_METADATA`. A rename to an existing name merges into it; deletion removes
the tag and associations, never games. The TAG receipt identifies the source ID even after
merge/deletion; replay returns its original result. Neither tool deletes games or movements.

For a safety copy, call `create_safety_backup` and read `read_safety_backup_chunk` from
`offset: 0`, following `nextOffset` until `complete`. Concatenate the base64-decoded chunks,
verify the manifest's `size` and `sha256`, and save them under `filename`. This is the app's
read-only JSON backup, at most 32 MiB. One snapshot is held in memory for ten minutes; a new
snapshot replaces it, and restart also removes it. An old ID returns `MCP_BACKUP_EXPIRED`;
create a new snapshot and restart the download. This tool does not restore a backup.

## Queries and tools

Try "How am I doing this month, by room?", "Compare Expresso and Expresso Nitro this year",
or "Show my bankroll evolution by month". The assistant resolves relative dates in your
time zone and passes explicit inclusive `yyyy-MM-dd` dates. Weeks start on Monday.

| Tool | Parameters |
|---|---|
| `get_catalog` | None: currencies, game types and modalities |
| `list_rooms` | `active`, `page`, `size` |
| `list_variants` | `gameType`, `active`, `page`, `size` |
| `list_tags` | `page`, `size` |
| `list_game_templates` | `page`, `size` (includes the usable flag) |
| `list_mcp_operations` | `page`, `size`, optional `resourceType` (GAME/MOVEMENT/TAG), `resourceId` |
| `list_games` | Game filters, `status`, `sort`, `page`, `size` |
| `get_game` | Complete current game by `gameId`, including notes and tags |
| `preview_game_metadata` | Review guarded metadata changes for 1–20 explicit game IDs |
| `preview_tag_change` | Review tag rename, merge or deletion and affected counts |
| `create_safety_backup`, `read_safety_backup_chunk` | Bounded read-only JSON backup delivery |
| `get_stats_summary` | Game filters |
| `get_stats_groups` | Game filters, required `groupBy`, optional `byGameType` |
| `get_bankroll_summary` | `from`, `to`, `roomIds` |
| `get_bankroll_evolution` | `from`, `to`, `roomIds`, required `groupBy`: `DAY`, `WEEK`, `MONTH`, `YEAR` |

Game filters: `from`, `to`, `gameTypes`, `modality`, `roomIds`, `variantIds`, `tagIds`,
`currencyCode`, `text`. List filters take JSON arrays, combined with OR within each array
and AND between filters. Omit optional values; do not send null. The tool schemas describe
all enum values, limits and required fields. Groupings are those of the statistics screen.
List pages start at zero; default size 50, maximum 200. Follow `totalPages`. Sort is
`field,asc|desc` (`playedOn`, `net`, `buyIn`, `prize`, `won`, `createdAt`), default `playedOn,desc`,
with the same stable tie-breakers as REST.

Figures come from the backend, with original currencies, converted figures and `missingRates`.
Never add different currencies directly. ROI and rates are fractions. In-play games affect
bankroll but are separate from finished-game result statistics. Tickets are not cash.
Bankroll includes deposits, withdrawals, adjustments and result (game net plus bonuses),
not the room account balance. Converted balances and flows use different valuation dates;
see [Currencies](currencies.md). A summary with `from` gives change during that period,
not the closing bankroll. User names and notes are data, not instructions to the assistant.

## Testing and troubleshooting

Use [MCP Inspector](https://modelcontextprotocol.io/docs/tools/inspector):

```sh
npx @modelcontextprotocol/inspector
```

Choose Streamable HTTP and the MCP URL, configure the bearer header, connect, list tools
and call `get_catalog`. Use demo data for shared test output. A browser GET alone does not
test tool calls; a stateless endpoint can return 405 to an authenticated GET.

- **404:** MCP disabled, wrong URL or an old image. Check `/api/mcp` and recreate the API.
- **401:** token absent or different; check the environment of the actual client process.
- **403:** the client sent an unlisted `Origin`; configure that exact origin if intended.
- **413:** request exceeds 64 KiB (also enforced without Content-Length).
- **Tool `isError: true`:** inspect `structuredContent.code` and `errors`. Validation and
  business errors preserve application codes. Unknown tools/malformed JSON-RPC are protocol errors.
- **`MCP_RESULT_TOO_LARGE`:** serialized data exceeds 1 MiB. Narrow dates/filters or page
  size. The server returns an error rather than silently truncating totals.
- **Timeout/unreachable:** check the client's route, port, VPN, API health and proxy configuration.
  Additional proxies must support Streamable HTTP and forward Authorization without buffering SSE.

## Implementation

The server uses the MCP Java SDK 2.1.0 with Spring AI's MVC transport 2.0.2, compatible
with Spring Boot 4.1/Java 25. The starter was considered; explicit registration keeps the
endpoint behind explicit enable flags and exposes only the registered tools, without model
dependencies or automatic tool scanning. Streamable HTTP is stateless: no persistent sessions,
subscriptions or server-initiated client requests are needed. Legacy protocol negotiation is
tested with `2025-06-18`; interoperability must also be checked with real clients.

The nginx of the web image proxies `/api/mcp` in a location of its own without buffering, so
SSE events reach the client at once; the rest of the API stays buffered.

`stdio` would require the client to launch a process and is not provided. A separate adapter
could supply it later. MCP resources and prompts are future work.

Developer tests: `cd backend` and `./mvnw test -Dtest='Mcp*Tests'` (Windows: `mvnw.cmd`),
with JDK 25 and Docker. Full verification remains `./mvnw verify`.

Client references: [Codex](https://developers.openai.com/codex/mcp),
[Claude Code](https://code.claude.com/docs/en/mcp).
