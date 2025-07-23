package net.protsenko.spotfetchprice.service.exchange;

import net.protsenko.spotfetchprice.dto.TickerDTO;
import net.protsenko.spotfetchprice.service.ExchangeType;
import org.knowm.xchange.currency.CurrencyPair;

import java.io.IOException;
import java.util.List;

public interface ExchangeClient {
    List<TickerDTO> getTickers(List<CurrencyPair> instruments) throws IOException;

    List<CurrencyPair> getCurrencyPairs() throws IOException;

    ExchangeType getExchangeType();
}