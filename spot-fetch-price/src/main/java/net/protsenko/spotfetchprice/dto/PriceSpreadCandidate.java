package net.protsenko.spotfetchprice.dto;

public record PriceSpreadCandidate(
        String buyExchange,
        double buyPrice,
        Double buyVolume,
        String sellExchange,
        double sellPrice,
        Double sellVolume
) {
    public double spread() {
        return sellPrice - buyPrice;
    }
}
