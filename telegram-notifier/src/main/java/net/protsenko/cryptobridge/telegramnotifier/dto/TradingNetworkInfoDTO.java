package net.protsenko.cryptobridge.telegramnotifier.dto;

public record TradingNetworkInfoDTO(
        String network,
        double withdrawFee,
        boolean depositEnabled,
        boolean withdrawEnabled
) {
}
