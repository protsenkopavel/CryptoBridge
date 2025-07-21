package net.protsenko.cryptobridge.cryptobridge.service;

import lombok.extern.slf4j.Slf4j;
import net.protsenko.cryptobridge.cryptobridge.dto.ExchangeTickersDTO;
import net.protsenko.cryptobridge.cryptobridge.dto.TickerDTO;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class ExchangeService {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public List<ExchangeTickersDTO> getAllMarketDataForAllExchanges(
            List<ExchangeType> exchanges,
            List<CurrencyPair> instruments
    ) {
        List<ExchangeType> exchangeTypes = normalizeExchanges(exchanges);

        List<CompletableFuture<ExchangeTickersDTO>> futures = exchangeTypes.stream()
                .map(exchangeType -> CompletableFuture.supplyAsync(
                        () -> fetchExchangeTickers(exchangeType, instruments), executor)
                )
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .toList();
    }

    private ExchangeTickersDTO fetchExchangeTickers(ExchangeType exchangeType, List<CurrencyPair> instruments) {
        try {
            Exchange exchange = createExchange(exchangeType);
            MarketDataService marketDataService = exchange.getMarketDataService();

            List<CurrencyPair> pairsToQuery = filterCurrencyPairs(exchange, instruments);

            List<TickerDTO> tickerDTOs = pairsToQuery.parallelStream()
                    .map(pair -> {
                        try {
                            Ticker ticker = marketDataService.getTicker(pair);
                            return TickerDTO.fromTicker(ticker);
                        } catch (IOException e) {
                            log.warn("Error fetching ticker for {} on {}: {}", pair, exchangeType, e.getMessage());
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .toList();

            return new ExchangeTickersDTO(exchangeType.name(), tickerDTOs);

        } catch (Exception e) {
            log.error("Error processing exchange {}: {}", exchangeType, e.getMessage(), e);
            return null;
        }
    }

    public List<ExchangeType> getAvailableExchanges() {
        return List.of(ExchangeType.values());
    }

    public List<CurrencyPair> getAvailableCurrencyPairs(List<ExchangeType> exchanges) {
        List<ExchangeType> exchangeTypes = normalizeExchanges(exchanges);
        Set<CurrencyPair> pairsSet = new HashSet<>();

        for (ExchangeType exchangeType : exchangeTypes) {
            try {
                Exchange exchange = createExchange(exchangeType);
                pairsSet.addAll(
                        exchange.getExchangeInstruments().stream()
                                .filter(instr -> instr instanceof CurrencyPair)
                                .map(instr -> (CurrencyPair) instr)
                                .toList()
                );
            } catch (Exception e) {
                log.error("Error fetching instruments for exchange {}: {}", exchangeType, e.getMessage(), e);
            }
        }

        return pairsSet.stream()
                .sorted(Comparator.comparing(CurrencyPair::toString))
                .toList();
    }

    private List<ExchangeType> normalizeExchanges(List<ExchangeType> exchanges) {
        return (exchanges == null || exchanges.isEmpty())
                ? List.of(ExchangeType.values())
                : exchanges;
    }

    private Exchange createExchange(ExchangeType exchangeType) throws IOException {
        Exchange exchange = exchangeType.createExchange();
        try {
            exchange.remoteInit();
        } catch (IOException e) {
            log.warn("Failed to remoteInit exchange {}: {}", exchangeType, e.getMessage());
        }
        return exchange;
    }

    private List<CurrencyPair> filterCurrencyPairs(Exchange exchange, List<CurrencyPair> instrumentsFilter) {
        return exchange.getExchangeInstruments().stream()
                .filter(instr -> instr instanceof CurrencyPair)
                .map(instr -> (CurrencyPair) instr)
                .filter(pair -> instrumentsFilter == null || instrumentsFilter.isEmpty() || instrumentsFilter.contains(pair))
                .toList();
    }

    private List<Ticker> fetchTickers(MarketDataService marketDataService, ExchangeType exchangeType, List<CurrencyPair> pairs) {
        List<Ticker> tickers = new ArrayList<>();
        for (CurrencyPair pair : pairs) {
            try {
                Ticker ticker = marketDataService.getTicker(pair);
                tickers.add(ticker);
            } catch (IOException e) {
                log.warn("Error fetching ticker for {} on {}: {}", pair, exchangeType, e.getMessage());
            }
        }
        return tickers;
    }
}
