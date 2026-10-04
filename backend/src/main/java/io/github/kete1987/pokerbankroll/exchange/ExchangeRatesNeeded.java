package io.github.kete1987.pokerbankroll.exchange;

/**
 * Published when other rates may be needed (the base currency changed, a backup was restored):
 * once the transaction commits, the missing ones are downloaded in the background.
 */
public record ExchangeRatesNeeded() {
}
