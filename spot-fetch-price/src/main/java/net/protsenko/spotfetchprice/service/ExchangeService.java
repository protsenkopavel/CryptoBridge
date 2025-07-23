package net.protsenko.spotfetchprice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.spotfetchprice.dto.ExchangeTickersDTO;
import net.protsenko.spotfetchprice.dto.TickerDTO;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.bitfinex.BitfinexExchange;
import org.knowm.xchange.bitfinex.service.BitfinexMarketDataServiceRaw;
import org.knowm.xchange.bitfinex.v2.dto.marketdata.BitfinexTicker;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.kucoin.KucoinMarketDataServiceRaw;
import org.knowm.xchange.kucoin.dto.response.AllTickersResponse;
import org.knowm.xchange.kucoin.dto.response.AllTickersTickerResponse;
import org.knowm.xchange.mexc.MEXCExchange;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static net.protsenko.spotfetchprice.dto.TickerDTO.parseKucoinSymbol;

@Slf4j
@Service
public class ExchangeService {

    private static final long cacheTtlSeconds = 300;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ValueOperations<String, ExchangeTickersDTO> valueOps;
    private final Map<ExchangeType, Exchange> exchangeCache = new ConcurrentHashMap<>();
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ExchangeService(RedisTemplate<String, ExchangeTickersDTO> redisTemplate) {
        this.valueOps = redisTemplate.opsForValue();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @PostConstruct
    public void initializeExchanges() {
        log.info("Initializing exchanges asynchronously...");
        List<ExchangeType> exchangeTypes = Arrays.stream(ExchangeType.values())
                .filter(e -> e != ExchangeType.MEXC)
                .toList();

        for (ExchangeType exchangeType : exchangeTypes) {
            executor.submit(() -> {
                try {
                    log.info("Initializing exchange: {}", exchangeType);
                    getOrCreateExchange(exchangeType);
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

            if (exchangeType == ExchangeType.MEXC) {
                List<TickerDTO> tickerDTOs = fetchMexcTickersViaHttp(instruments);
                ExchangeTickersDTO dto = new ExchangeTickersDTO(exchangeType.name(), tickerDTOs);
                valueOps.set(cacheKey, dto, Duration.ofSeconds(cacheTtlSeconds));
                log.debug("Cache set for key {}", cacheKey);
                return dto;
            }

            Exchange exchange = getOrCreateExchange(exchangeType);
            MarketDataService marketDataService = exchange.getMarketDataService();

            List<TickerDTO> tickerDTOs;

            if (exchangeType == ExchangeType.KUCOIN) {
                KucoinMarketDataServiceRaw rawService = (KucoinMarketDataServiceRaw) marketDataService;
                AllTickersResponse allTickersResponse = rawService.getKucoinTickers();

                AllTickersTickerResponse[] tickersArray = allTickersResponse.getTicker();

                tickerDTOs = Arrays.stream(tickersArray)
                        .filter(ticker -> instruments == null || instruments.isEmpty() ||
                                instruments.contains(parseKucoinSymbol(ticker.getSymbol())))
                        .map(TickerDTO::fromKucoinTicker)
                        .toList();
            } else if (exchangeType == ExchangeType.BITFINEX) {
                BitfinexExchange bitfinexExchange = (BitfinexExchange) exchange;
                @SuppressWarnings("UnstableApiUsage")
                BitfinexMarketDataServiceRaw rawService = new BitfinexMarketDataServiceRaw(
                        (BitfinexExchange) exchange, bitfinexExchange.getResilienceRegistries()
                );

                List<CurrencyPair> pairsToQuery = filterCurrencyPairs(exchange, instruments).stream()
                        .filter(instr -> instr instanceof CurrencyPair)
                        .map(instr -> (CurrencyPair) instr)
                        .toList();

                try {
                    BitfinexTicker[] bitfinexTickers = rawService.getBitfinexTickers(pairsToQuery);
                    tickerDTOs = Arrays.stream(bitfinexTickers)
                            .filter(ticker -> !ticker.getSymbol().startsWith("f"))
                            .map(TickerDTO::fromBitfinexV2Ticker)
                            .filter(Objects::nonNull)
                            .toList();
                } catch (IOException e) {
                    log.error("Error fetching tickers from Bitfinex: {}", e.getMessage(), e);
                    tickerDTOs = Collections.emptyList();
                }
            } else {
                try {
                    List<Ticker> tickers = marketDataService.getTickers(null);
                    tickerDTOs = tickers.stream()
                            .filter(ticker -> instruments == null || instruments.isEmpty() || instruments.contains(ticker.getInstrument()))
                            .map(TickerDTO::fromTicker)
                            .collect(Collectors.toList());
                } catch (IOException e) {
                    log.error("Error fetching tickers from {}: {}", exchangeType, e.getMessage(), e);
                    tickerDTOs = Collections.emptyList();
                }
            }

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
            if (exchangeType == ExchangeType.MEXC) {
                return fetchMexcPairsViaHttp();
            }

            Exchange exchange = getOrCreateExchange(exchangeType);
            return exchange.getExchangeInstruments().stream()
                    .filter(instr -> instr instanceof CurrencyPair)
                    .map(instr -> (CurrencyPair) instr)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error fetching instruments for exchange {}: {}", exchangeType, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    private List<ExchangeType> normalizeExchanges(List<ExchangeType> exchanges) {
        return (exchanges == null || exchanges.isEmpty())
                ? List.of(ExchangeType.values())
                : exchanges;
    }

    private Exchange getOrCreateExchange(ExchangeType exchangeType) throws IOException {
        if (exchangeType == ExchangeType.MEXC) {
            throw new UnsupportedOperationException();
        }

        Exchange exchange = exchangeCache.computeIfAbsent(exchangeType, et -> {
            try {
                Exchange ex = et.createExchange();
                try {
                    ex.remoteInit();
                } catch (NullPointerException npe) {
                    log.error("NullPointerException during remoteInit for {}: {}", et, npe.getMessage());
                    return null;
                }
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
        if (exchange instanceof MEXCExchange) {
            List<CurrencyPair> allPairs = fetchMexcPairsViaHttp();

            if (instrumentsFilter == null || instrumentsFilter.isEmpty()) {
                return new ArrayList<>(allPairs);
            } else {
                return allPairs.stream()
                        .filter(instrumentsFilter::contains)
                        .collect(Collectors.toList());
            }
        }

        Collection<Instrument> instrs = exchange.getExchangeInstruments();
        if (instrs == null) return Collections.emptyList();
        return instrs.stream()
                .filter(instr -> instr instanceof CurrencyPair)
                .filter(pair -> instrumentsFilter == null || instrumentsFilter.isEmpty() || instrumentsFilter.contains(pair))
                .collect(Collectors.toList());
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

    public List<CurrencyPair> fetchMexcPairsViaHttp() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mexc.com/api/v3/exchangeInfo"))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode symbols = root.get("symbols");

            if (symbols == null || !symbols.isArray()) {
                throw new RuntimeException();
            }

            List<CurrencyPair> result = new ArrayList<>();
            for (JsonNode symbolNode : symbols) {
                JsonNode base = symbolNode.get("baseAsset");
                JsonNode quote = symbolNode.get("quoteAsset");
                JsonNode permissions = symbolNode.get("permissions");

                boolean isSpot = false;
                if (permissions != null && permissions.isArray()) {
                    for (JsonNode perm : permissions) {
                        if ("SPOT".equalsIgnoreCase(perm.asText())) {
                            isSpot = true;
                            break;
                        }
                    }
                }

                if (base != null && quote != null && isSpot) {
                    result.add(new CurrencyPair(base.asText(), quote.asText()));
                }
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed load MEXC", e);
        }
    }

    public List<TickerDTO> fetchMexcTickersViaHttp(List<CurrencyPair> pairsFilter) {
        List<TickerDTO> result = new ArrayList<>();
        String[] QUOTES = {"USDT", "USDC"};

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mexc.com/api/v3/ticker/bookTicker"))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            JsonNode tickers = objectMapper.readTree(response.body());

            for (JsonNode tickerNode : tickers) {
                String symbol = tickerNode.get("symbol").asText();
                boolean supported = false;
                for (String quote : QUOTES) {
                    if (symbol.endsWith(quote)) {
                        supported = true;
                        break;
                    }
                }
                if (!supported) {
                    continue;
                }

                try {
                    CurrencyPair pair = parseMexcSymbol(symbol);
                    if (pairsFilter == null || pairsFilter.isEmpty() || pairsFilter.contains(pair)) {
                        double bid = tickerNode.has("bidPrice") ? tickerNode.get("bidPrice").asDouble() : 0;
                        double ask = tickerNode.has("askPrice") ? tickerNode.get("askPrice").asDouble() : 0;
                        result.add(new TickerDTO(pair.getBase().getCurrencyCode(), pair.getCounter().getCurrencyCode(), 0, bid, ask, 0, 0));
                    }
                } catch (IllegalArgumentException ex) {
                    log.warn("Parsing exception MEXC: {}", symbol, ex);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Loading fail MEXC", e);
        }
        return result;
    }

    private CurrencyPair parseMexcSymbol(String symbol) {
        String[] QUOTES = {"USDT", "USDC"};

        for (String quote : QUOTES) {
            if (symbol.endsWith(quote)) {
                String base = symbol.substring(0, symbol.length() - quote.length());
                return new CurrencyPair(base, quote);
            }
        }
        throw new IllegalArgumentException("Unsupported type MEXC: " + symbol);
    }

}