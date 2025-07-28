package net.protsenko.spotfetchprice.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.spotfetchprice.dto.ExchangeTickersDTO;
import net.protsenko.spotfetchprice.dto.TickerDTO;
import net.protsenko.spotfetchprice.service.exchange.ExchangeClient;
import net.protsenko.spotfetchprice.service.exchange.ExchangeClientFactory;
import net.protsenko.spotfetchprice.service.exchange.ExchangeClientHolder;
import org.knowm.xchange.currency.CurrencyPair;
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

    private static final long CACHE_TTL_SECONDS = 300;
    private static final int BULK_THRESHOLD = 40;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ValueOperations<String, TickerDTO> tickerValueOps;
    private final ValueOperations<String, ExchangeTickersDTO> bulkValueOps;
    private final ExchangeClientFactory exchangeClientFactory;
    private final Map<ExchangeType, ExchangeClientHolder> exchangeClients = new ConcurrentHashMap<>();

    public ExchangeService(
            RedisTemplate<String, TickerDTO> tickerRedisTemplate,
            RedisTemplate<String, ExchangeTickersDTO> bulkRedisTemplate,
            ExchangeClientFactory exchangeClientFactory
    ) {
        this.tickerValueOps = tickerRedisTemplate.opsForValue();
        this.bulkValueOps = bulkRedisTemplate.opsForValue();
        this.exchangeClientFactory = exchangeClientFactory;
    }

    @PostConstruct
    public void initializeExchanges() {
        log.info("Initializing exchanges asynchronously...");
        for (ExchangeType exchangeType : ExchangeType.values()) {
            executor.submit(() -> {
                try {
                    log.info("Initializing exchange: {}", exchangeType);
                    getOrCreateExchangeClient(exchangeType);
                    log.info("Successfully initialized exchange: {}", exchangeType);
                } catch (Exception e) {
                    log.warn("Failed to pre-initialize exchange {}: {}", exchangeType, e.getMessage());
                }
            });
        }
    }

    public Map<ExchangeType, Map<CurrencyPair, TickerDTO>> getAllMarketDataForAllExchanges(
            List<ExchangeType> exchanges,
            List<CurrencyPair> currencyPairs
    ) {
        List<ExchangeType> exchangeTypes = normalizeExchanges(exchanges);

        Map<ExchangeType, Map<CurrencyPair, TickerDTO>> result = new HashMap<>();

        List<CompletableFuture<Void>> futures = exchangeTypes.stream()
                .map(exchangeType -> CompletableFuture.runAsync(() -> {
                    Map<CurrencyPair, TickerDTO> tickers = getMarketDataForExchange(exchangeType, currencyPairs);
                    synchronized (result) {
                        result.put(exchangeType, tickers);
                    }
                }, executor))
                .toList();

        futures.forEach(CompletableFuture::join);

        return result;
    }

    public Map<CurrencyPair, TickerDTO> getMarketDataForExchange(ExchangeType exchangeType, List<CurrencyPair> pairs) {
        if (pairs.size() > BULK_THRESHOLD) {
            String bulkKey = exchangeType.name() + ":ALL";
            ExchangeTickersDTO allTickers = bulkValueOps.get(bulkKey);

            if (allTickers != null && allTickers.tickers() != null && !allTickers.tickers().isEmpty()) {
                log.debug("Bulk cache hit for {}", bulkKey);
                return allTickers.tickers().stream()
                        .filter(t -> pairs.contains(new CurrencyPair(t.baseCurrency(), t.counterCurrency())))
                        .collect(Collectors.toMap(
                                t -> new CurrencyPair(t.baseCurrency(), t.counterCurrency()),
                                t -> t
                        ));
            }
            try {
                ExchangeClient client = getOrCreateExchangeClient(exchangeType);
                List<TickerDTO> freshTickers = client.getTickers(List.of()); // Пустой список = все пары
                bulkValueOps.set(bulkKey, new ExchangeTickersDTO(exchangeType.name(), freshTickers), Duration.ofSeconds(CACHE_TTL_SECONDS));
                log.debug("Bulk cache set for {}", bulkKey);
                return freshTickers.stream()
                        .filter(t -> pairs.contains(new CurrencyPair(t.baseCurrency(), t.counterCurrency())))
                        .collect(Collectors.toMap(
                                t -> new CurrencyPair(t.baseCurrency(), t.counterCurrency()),
                                t -> t
                        ));
            } catch (Exception e) {
                log.error("Ошибка bulk-запроса у {}: {}", exchangeType, e.getMessage());
                return Collections.emptyMap();
            }
        } else {
            List<String> keys = pairs.stream()
                    .map(pair -> generateCacheKey(exchangeType, pair))
                    .toList();

            List<TickerDTO> cachedTickers = tickerValueOps.multiGet(keys);
            Map<CurrencyPair, TickerDTO> result = new HashMap<>();
            List<CurrencyPair> missingPairs = new ArrayList<>();

            for (int i = 0; i < pairs.size(); i++) {
                TickerDTO ticker = cachedTickers.get(i);
                if (ticker != null) {
                    result.put(pairs.get(i), ticker);
                } else {
                    missingPairs.add(pairs.get(i));
                }
            }
            if (!missingPairs.isEmpty()) {
                try {
                    ExchangeClient client = getOrCreateExchangeClient(exchangeType);
                    List<TickerDTO> freshTickers = client.getTickers(missingPairs);
                    for (TickerDTO ticker : freshTickers) {
                        CurrencyPair pair = new CurrencyPair(ticker.baseCurrency(), ticker.counterCurrency());
                        String cacheKey = generateCacheKey(exchangeType, pair);
                        tickerValueOps.set(cacheKey, ticker, Duration.ofSeconds(CACHE_TTL_SECONDS));
                        result.put(pair, ticker);
                    }
                } catch (Exception e) {
                    log.error("Ошибка получения тикеров у {}: {}", exchangeType, e.getMessage());
                }
            }
            return result;
        }
    }

    public List<ExchangeType> getAvailableExchanges() {
        return List.of(ExchangeType.values());
    }

    public List<CurrencyPair> getAvailableCurrencyPairs(List<ExchangeType> exchanges) {
        List<ExchangeType> exchangeTypes = normalizeExchanges(exchanges);

        List<CompletableFuture<Collection<CurrencyPair>>> futures = exchangeTypes.stream()
                .map(exchangeType -> CompletableFuture.supplyAsync(
                        () -> fetchPairsForExchange(exchangeType), executor))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .flatMap(Collection::stream)
                .distinct()
                .sorted(Comparator.comparing(CurrencyPair::toString))
                .toList();
    }

    private Collection<CurrencyPair> fetchPairsForExchange(ExchangeType exchangeType) {
        try {
            ExchangeClient client = getOrCreateExchangeClient(exchangeType);
            return client.getCurrencyPairs();
        } catch (Exception e) {
            log.error("Error fetching instruments for exchange {}: {}", exchangeType, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private List<ExchangeType> normalizeExchanges(List<ExchangeType> exchanges) {
        return (exchanges == null || exchanges.isEmpty())
                ? Arrays.asList(ExchangeType.values())
                : exchanges;
    }

    private ExchangeClient getOrCreateExchangeClient(ExchangeType exchangeType) throws IOException {
        ExchangeClientHolder holder = exchangeClients.get(exchangeType);

        if (holder != null && !holder.isDisabled()) {
            try {
                return holder.getClient();
            } catch (Exception e) {
                throw new IOException("Клиент " + exchangeType + " недоступен", e);
            }
        }

        if (holder != null && holder.isDisabled() && !holder.canRetry()) {
            throw new IOException("Не удалось инициализировать " + exchangeType + " (cooldown)");
        }

        synchronized (exchangeClients) {
            holder = exchangeClients.get(exchangeType);
            if (holder != null && !holder.isDisabled()) {
                try {
                    return holder.getClient();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            if (holder != null && holder.isDisabled() && !holder.canRetry()) {
                throw new IOException("Не удалось инициализировать " + exchangeType + " (cooldown)");
            }
            try {
                ExchangeClient newClient = exchangeClientFactory.createClient(exchangeType);
                ExchangeClientHolder newHolder = new ExchangeClientHolder(newClient);
                exchangeClients.put(exchangeType, newHolder);
                return newClient;
            } catch (Exception e) {
                ExchangeClientHolder errorHolder = new ExchangeClientHolder(e);
                exchangeClients.put(exchangeType, errorHolder);
                log.error("Ошибка инициализации {}: {}", exchangeType, e.getMessage());
                throw new IOException("Не удалось инициализировать " + exchangeType, e);
            }
        }
    }

    private String generateCacheKey(ExchangeType exchangeType, CurrencyPair pair) {
        return exchangeType.name() + ":" + pair.toString();
    }

}