package net.protsenko.cryptobridge.cryptobridge.dto;

public record TickerData(
        double bid,
        double ask,
        double volume
) {
}
