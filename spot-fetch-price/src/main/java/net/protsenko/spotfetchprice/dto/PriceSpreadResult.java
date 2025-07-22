package net.protsenko.spotfetchprice.dto;

import org.knowm.xchange.instrument.Instrument;

public record PriceSpreadResult(
        Instrument instrument,
        String buyExchange,
        double buyPrice,
        String sellExchange,
        double sellPrice,
        double spread
) {
    public double spreadPercent() {
        if (buyPrice == 0) {
            return 0.0;
        }
        return (sellPrice - buyPrice) / buyPrice * 100.0;
    }
}
