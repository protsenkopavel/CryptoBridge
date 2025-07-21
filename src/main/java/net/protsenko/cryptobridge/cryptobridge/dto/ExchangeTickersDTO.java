package net.protsenko.cryptobridge.cryptobridge.dto;

import java.util.List;

public record ExchangeTickersDTO(
        String exchangeName,
        List<TickerDTO> tickers
) {
}
