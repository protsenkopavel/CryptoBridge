package net.protsenko.cryptobridge.telegramnotifier.dto;


import java.util.List;

public record TradingInfoDTO(
        List<TradingNetworkInfoDTO> networks
) {
}