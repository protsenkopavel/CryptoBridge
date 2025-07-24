package net.protsenko.spotfetchprice.dto;

public record TradingNetworkInfoDTO(
        String network,
        double withdrawFee,
        boolean depositEnabled,
        boolean withdrawEnabled
) {
}
