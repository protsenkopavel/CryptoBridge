package net.protsenko.spotfetchprice.dto;

public record ArbitrageOpportunityFoundEvent(
        PriceSpreadResultDTO spread
) {
}
