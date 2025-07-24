package net.protsenko.spotfetchprice.dto;


import java.util.List;

public record TradingInfoDTO(
        List<TradingNetworkInfoDTO> networks
) {
}