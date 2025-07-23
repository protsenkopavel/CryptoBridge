package net.protsenko.spotfetchprice.service.exchange;

import lombok.extern.slf4j.Slf4j;
import net.protsenko.spotfetchprice.dto.TickerDTO;
import net.protsenko.spotfetchprice.mapper.TickerMapper;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Ticker;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public abstract class BaseXChangeClient implements ExchangeClient {

    protected final Exchange exchange;

    public BaseXChangeClient(Exchange exchange) {
        this.exchange = exchange;
    }

    @Override
    public List<TickerDTO> getTickers(List<CurrencyPair> instruments) throws IOException {
        List<Ticker> tickers = exchange.getMarketDataService().getTickers(null);
        return tickers.stream()
                .filter(ticker -> instruments == null || instruments.isEmpty() || instruments.contains(ticker.getInstrument()))
                .map(TickerMapper::fromTicker)
                .collect(Collectors.toList());
    }

    @Override
    public List<CurrencyPair> getCurrencyPairs() {
        return exchange.getExchangeInstruments().stream()
                .filter(instr -> instr instanceof CurrencyPair)
                .map(instr -> (CurrencyPair) instr)
                .collect(Collectors.toList());
    }
}