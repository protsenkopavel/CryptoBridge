package net.protsenko.cryptobridge.cryptobridge.service;

import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ExchangeService {

    public Map<String, List<Ticker>> getAllMarketDataForAllExchanges(
            List<ExchangeType> exchanges,
            List<CurrencyPair> instruments
    ) {
        Map<String, List<Ticker>> result = new HashMap<>();

        List<ExchangeType> exchangeTypes = (exchanges == null || exchanges.isEmpty())
                ? List.of(ExchangeType.values())
                : exchanges;

        for (ExchangeType exchangeType : exchangeTypes) {
            try {
                Exchange exchange = exchangeType.createExchange();
                MarketDataService marketDataService = exchange.getMarketDataService();

                List<Instrument> availableInstruments = exchange.getExchangeInstruments();
                List<CurrencyPair> currencyPairs = availableInstruments.stream()
                        .filter(instr -> instr instanceof CurrencyPair)
                        .map(instr -> (CurrencyPair) instr)
                        .filter(pair -> instruments == null || instruments.isEmpty() || instruments.contains(pair))
                        .toList();

                List<Ticker> tickers = new ArrayList<>();
                for (CurrencyPair pair : currencyPairs) {
                    try {
                        Ticker ticker = marketDataService.getTicker(pair);
                        tickers.add(ticker);
                    } catch (IOException e) {
                        log.warn("Error fetching ticker for {} on {}: {}", pair, exchangeType, e.getMessage());
                    }
                }

                result.put(exchangeType.name(), tickers);
            } catch (Exception e) {
                log.error("Error with exchange {}: {}", exchangeType, e.getMessage());
            }
        }

        return result;
    }
}
