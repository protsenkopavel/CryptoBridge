package net.protsenko.spotfetchprice.dto;

import org.knowm.xchange.instrument.Instrument;

public record PriceSpreadResult(
        Instrument instrument,
        String buyExchange,
        double buyPrice,
        Double buyVolume,
        String sellExchange,
        double sellPrice,
        Double sellVolume,
        double spread,
        double spreadPercentage
) {
}
