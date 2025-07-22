package net.protsenko.spotfetchprice.dto;

public record TickerData(
        double bid,
        double ask,
        double volume
) {
}