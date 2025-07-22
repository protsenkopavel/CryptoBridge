package net.protsenko.spotfetchprice.dto;

public record PriceSpreadCandidate(
        String buyExchange,
        double buyPrice,
        String sellExchange,
        double sellPrice
) {
    public double spread() {
        return sellPrice - buyPrice;
    }
}
