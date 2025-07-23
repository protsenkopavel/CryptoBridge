package net.protsenko.spotfetchprice.dto;

public record TickerDTO(
        String baseCurrency,
        String counterCurrency,
        double last,
        double bid,
        double ask,
        double volume,
        long timestamp
) {
}