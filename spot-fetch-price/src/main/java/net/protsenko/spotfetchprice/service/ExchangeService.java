package net.protsenko.spotfetchprice.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.spotfetchprice.dto.ExchangeTickersDTO;
import net.protsenko.spotfetchprice.dto.TickerDTO;
import net.protsenko.spotfetchprice.service.exchange.ExchangeClient;
import net.protsenko.spotfetchprice.service.exchange.ExchangeClientFactory;
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
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ValueOperations<String, ExchangeTickersDTO> valueOps;
    private final ExchangeClientFactory exchangeClientFactory;
    private final Map<ExchangeType, ExchangeClient> exchangeClients = new ConcurrentHashMap<>();

    public ExchangeService(RedisTemplate<String, ExchangeTickersDTO> redisTemplate, ExchangeClientFactory exchangeClientFactory) {
        this.valueOps = redisTemplate.opsForValue();
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

            ExchangeClient client = getOrCreateExchangeClient(exchangeType);
            List<TickerDTO> tickerDTOs = client.getTickers(instruments);

            ExchangeTickersDTO dto = new ExchangeTickersDTO(exchangeType.name(), tickerDTOs);
            valueOps.set(cacheKey, dto, Duration.ofSeconds(CACHE_TTL_SECONDS));
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
        return exchangeClients.computeIfAbsent(exchangeType, et -> {
            try {
                return exchangeClientFactory.createClient(et);
            } catch (IOException e) {
                log.error("Failed to create exchange client for {}: {}", et, e.getMessage());
                throw new RuntimeException(e);
            }
        });
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
        getAllMarketDataForAllExchanges(allExchanges, null);
    }
}