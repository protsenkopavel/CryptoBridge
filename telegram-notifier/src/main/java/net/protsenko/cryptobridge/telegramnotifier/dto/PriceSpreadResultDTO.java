package net.protsenko.cryptobridge.telegramnotifier.dto;

public record PriceSpreadResultDTO(
        String instrument,
        String baseCurrency,
        String counterCurrency,
        TradingInfoDTO buyTradingInfo,
        String buyExchange,
        double buyPrice,
        Double buyVolume,
        TradingInfoDTO sellTradingInfo,
        String sellExchange,
        double sellPrice,
        Double sellVolume,
        double spread,
        double spreadPercentage
) {
}
