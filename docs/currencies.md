# Currencies

Each room holds money in one currency (EUR, USD...), and its games and bankroll movements are in
that currency. They always keep it: the lists of games and movements, the exports, the import and
the backups show every amount in its own currency.

## What you see

- When what a screen shows is in a **single currency** (for example the Dashboard filtered to your
  dollar room), it is shown in that currency, as it is.
- When it **mixes currencies**, everything is converted to your **base currency**, and the cards
  show below each figure the amount of every currency in its own money, e.g.
  `1.180,00 € · 320,00 US$`. Rates such as ROI or ITM have no such breakdown.

There is no currency selector on the Dashboard, Statistics or Bankroll: the screen chooses by
itself.

### Which exchange rate is used

- What **happened** (the result of a game, what you invested and won, deposits, withdrawals,
  bonuses, adjustments) is converted with the rate of **the day it happened**.
- The **bankroll now** is a balance: what you have in each currency, converted with **today's**
  rate. In the evolution chart, each point is what you had in each currency then, at that day's
  rate.

So the converted bankroll is not exactly deposits − withdrawals + result: the difference is the
exchange difference (your dollars are worth more or fewer euros than when you won them). The
Bankroll screen says so under the cards.

On weekends and holidays, when there is no rate, the last one before is used. A rate is never made
up: when there is none on or before a day, the amounts of that day are left out of the converted
figures, and the screen warns which currency and days are missing, with a link to Settings →
Currencies.

## Settings → Currencies

- **Base currency**: automatic (the currency with most games) or one you choose.
- **Exchange rates**: the reference rates of the European Central Bank, downloaded from
  [Frankfurter](https://frankfurter.dev) (free, no key) when the app starts and every afternoon
  after the ECB publishes. The table says, per currency, from which day to which day there are
  rates and whether some are missing for your games. **Update now** downloads them at once.
- **Rates typed by hand**: for a currency the ECB does not publish, or to correct one. A rate is
  what 1 EUR is worth in the currency (`1 EUR = 1.0850 USD`), from its day until the next rate,
  and it wins over the downloaded one of the same day.

The base currency and the rates typed by hand are part of the [backup](backups.md#backup-from-the-app);
the downloaded rates are not (they are downloaded again).

## Internet access

To download the rates, the API container connects to `https://api.frankfurter.dev`. Without
internet everything else works, with the rates already downloaded and the ones typed by hand. To
turn the downloads off, set `EXCHANGE_RATES_ENABLED=false` in `.env` (the stack passes it to the
API as `POKER_BANKROLL_EXCHANGE_RATES_ENABLED`).
