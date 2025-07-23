package net.protsenko.spotfetchprice.dto;

public record PriceSpreadResultDTO(
        String instrument,
        String baseCurrency,
        String counterCurrency,
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
