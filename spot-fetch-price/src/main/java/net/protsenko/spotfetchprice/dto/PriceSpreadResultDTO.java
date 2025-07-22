package net.protsenko.spotfetchprice.dto;

import org.knowm.xchange.instrument.Instrument;

public record PriceSpreadResultDTO(
        Instrument instrument,
        String buyExchange,
        double buyPrice,
        String sellExchange,
        double sellPrice,
        double spread,
        double spreadPercentage
) {
}
