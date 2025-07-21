package net.protsenko.cryptobridge.cryptobridge.dto;

import org.knowm.xchange.instrument.Instrument;

public record PriceSpreadResult(
        Instrument instrument,
        String exchangeA,
        double priceA,
        String exchangeB,
        double priceB,
        double spread
) {
}
