package net.protsenko.spotfetchprice.service;

import lombok.extern.slf4j.Slf4j;
import net.protsenko.spotfetchprice.dto.ExchangeTickersDTO;
import net.protsenko.spotfetchprice.dto.TickerDTO;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ExchangeService {

    private static final long cacheTtlSeconds = 300;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ValueOperations<String, ExchangeTickersDTO> valueOps;
    private final Map<ExchangeType, Exchange> exchangeCache = new ConcurrentHashMap<>();

    public ExchangeService(RedisTemplate<String, ExchangeTickersDTO> redisTemplate) {
        this.valueOps = redisTemplate.opsForValue();
    }

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
            String cacheKey = generateCacheKey(exchangeType, instruments);

            ExchangeTickersDTO cachedData = valueOps.get(cacheKey);
            if (cachedData != null) {
                log.debug("Cache hit for key {}", cacheKey);
                return cachedData;
            }

            Exchange exchange = getOrCreateExchange(exchangeType);
            MarketDataService marketDataService = exchange.getMarketDataService();

            List<Instrument> pairsToQuery = filterCurrencyPairs(exchange, instruments);

            List<TickerDTO> tickerDTOs = pairsToQuery.parallelStream()
                    .map(pair -> {
                        try {
                            Ticker ticker = marketDataService.getTicker(pair);
                            return TickerDTO.fromTicker(ticker);
                        } catch (ExchangeException ex) {
                            log.error("ExchangeException for pair {} on {}: {}", pair, exchangeType, ex.getMessage());
                            return null;
                        } catch (IOException ioex) {
                            log.error("IOException for pair {} on {}: {}", pair, exchangeType, ioex.getMessage());
                            return null;
                        } catch (Exception ex) {
                            log.error("Unexpected error for pair {} on {}: {}", pair, exchangeType, ex.toString());
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .toList();

            ExchangeTickersDTO dto = new ExchangeTickersDTO(exchangeType.name(), tickerDTOs);

            valueOps.set(cacheKey, dto, Duration.ofSeconds(cacheTtlSeconds));
            log.debug("Cache set for key {}", cacheKey);

            return dto;
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
                Exchange exchange = getOrCreateExchange(exchangeType);
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

    private Exchange getOrCreateExchange(ExchangeType exchangeType) throws IOException {
        Exchange exchange = exchangeCache.computeIfAbsent(exchangeType, et -> {
            try {
                Exchange ex = et.createExchange();
                ex.remoteInit();
                return ex;
            } catch (IOException e) {
                log.error("Error initializing exchange {}: {}", et, e.getMessage());
                return null;
            }
        });

        if (exchange == null) {
            log.error("Failed to create exchange {}", exchangeType);
            throw new IOException("Failed to create exchange " + exchangeType);
        }

        return exchange;
    }

    private List<Instrument> filterCurrencyPairs(Exchange exchange, List<CurrencyPair> instrumentsFilter) {
        return exchange.getExchangeInstruments().stream()
                .filter(instr -> instr instanceof CurrencyPair)
                .filter(pair ->
                        instrumentsFilter == null
                                || instrumentsFilter.isEmpty()
                                || instrumentsFilter.contains(pair))
                .toList();
    }

    private String generateCacheKey(ExchangeType exchangeType, List<CurrencyPair> instruments) {
        if (instruments == null || instruments.isEmpty()) {
            return exchangeType.name() + ":ALL";
        }

        String instrumentsKey = instruments.stream()
                .map(CurrencyPair::toString)
                .sorted()
                .collect(Collectors.joining(","));

        return exchangeType.name() + ":" + instrumentsKey;
    }

    public void refreshCache() {
        List<ExchangeType> allExchanges = List.of(ExchangeType.values());
        List<CurrencyPair> allPairs = null;
        getAllMarketDataForAllExchanges(allExchanges, allPairs);
    }

}