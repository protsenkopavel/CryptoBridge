package net.protsenko.cryptobridge.cryptobridge.dto;

import org.knowm.xchange.dto.marketdata.Ticker;

public record TickerDTO(
        String baseCurrency,
        String counterCurrency,
        double last,
        double bid,
        double ask,
        double volume,
        long timestamp
) {
    public static TickerDTO fromTicker(Ticker ticker) {
        return new TickerDTO(
                ticker.getInstrument().getBase().getCurrencyCode(),
                ticker.getInstrument().getCounter().getCurrencyCode(),
                ticker.getLast().doubleValue(),
                ticker.getBid().doubleValue(),
                ticker.getAsk().doubleValue(),
                ticker.getVolume().doubleValue(),
                ticker.getTimestamp() != null ? ticker.getTimestamp().getTime() : 0L
        );
    }
}
