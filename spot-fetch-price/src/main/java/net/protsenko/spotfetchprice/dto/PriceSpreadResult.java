package net.protsenko.spotfetchprice.dto;

import org.knowm.xchange.instrument.Instrument;

public record PriceSpreadResult(
        Instrument instrument,
        String buyExchange,
        double buyPrice,
        double buyVolume,
        TradingInfoDTO buyTradingInfo,
        String sellExchange,
        double sellPrice,
        double sellVolume,
        TradingInfoDTO sellTradingInfo,
        double spread,
        double profitPercent
) {
}
