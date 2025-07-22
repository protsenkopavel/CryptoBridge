package net.protsenko.spotfetchprice.dto;

import java.util.List;

public record ExchangeTickersDTO(
        String exchangeName,
        List<TickerDTO> tickers
) {
}