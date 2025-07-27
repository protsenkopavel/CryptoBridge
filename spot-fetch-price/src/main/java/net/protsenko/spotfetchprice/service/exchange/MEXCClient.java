package net.protsenko.spotfetchprice.service.exchange;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.spotfetchprice.dto.TickerDTO;
import net.protsenko.spotfetchprice.props.MEXCApiProperties;
import net.protsenko.spotfetchprice.service.ExchangeType;
import org.knowm.xchange.currency.CurrencyPair;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
public class MEXCClient implements ExchangeClient {

    private final MEXCApiProperties mexcApiProperties;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public MEXCClient(MEXCApiProperties mexcApiProperties, ObjectMapper objectMapper) {
        this.mexcApiProperties = mexcApiProperties;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(mexcApiProperties.getBaseUrl())
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(configurer -> configurer
                                .defaultCodecs()
                                .maxInMemorySize(mexcApiProperties.getMaxInMemorySize()))
                        .build())
                .build();
    }

    @Override
    public List<TickerDTO> getTickers(List<CurrencyPair> pairsFilter) {
        try {
            String response = webClient.get()
                    .uri(mexcApiProperties.getTickersPath())
                    .header("User-Agent", mexcApiProperties.getUserAgent())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .onErrorReturn("")
                    .block();

            if (response == null || response.isEmpty()) return Collections.emptyList();

            JsonNode tickers = objectMapper.readTree(response);

            List<TickerDTO> result = new ArrayList<>();
            for (JsonNode tickerNode : tickers) {
                String symbol = tickerNode.path("symbol").asText();
                if (!isSupported(symbol)) continue;

                try {
                    CurrencyPair pair = parseMexcSymbol(symbol);
                    if (pairsFilter == null || pairsFilter.isEmpty() || pairsFilter.contains(pair)) {
                        double bid = getDoubleSafe(tickerNode, "bidPrice");
                        double ask = getDoubleSafe(tickerNode, "askPrice");
                        double volume = getDoubleSafe(tickerNode, "volume");
                        result.add(new TickerDTO(
                                pair.getBase().getCurrencyCode(),
                                pair.getCounter().getCurrencyCode(),
                                0, bid, ask, volume, 0
                        ));
                    }
                } catch (IllegalArgumentException ex) {
                    log.warn("MEXC: ошибка парсинга symbol '{}': {}", symbol, ex.getMessage());
                }
            }
            return result;
        } catch (Exception e) {
            log.error("Ошибка загрузки тикеров MEXC: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<CurrencyPair> getCurrencyPairs() {
        try {
            String response = webClient.get()
                    .uri(mexcApiProperties.getExchangeInfoPath())
                    .header("User-Agent", mexcApiProperties.getUserAgent())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .onErrorReturn("")
                    .block();

            if (response == null || response.isEmpty()) return Collections.emptyList();

            JsonNode root = objectMapper.readTree(response);
            JsonNode symbols = root.get("symbols");
            if (symbols == null || !symbols.isArray()) {
                log.warn("Некорректный ответ symbols MEXC");
                return Collections.emptyList();
            }

            List<CurrencyPair> result = new ArrayList<>();
            for (JsonNode symbolNode : symbols) {
                String base = symbolNode.path("baseAsset").asText();
                String quote = symbolNode.path("quoteAsset").asText();
                JsonNode permissions = symbolNode.path("permissions");
                boolean isSpot = false;
                if (permissions.isArray()) {
                    for (JsonNode perm : permissions) {
                        if ("SPOT".equalsIgnoreCase(perm.asText())) {
                            isSpot = true;
                            break;
                        }
                    }
                }
                if (!base.isEmpty() && !quote.isEmpty() && isSpot) {
                    result.add(new CurrencyPair(base, quote));
                }
            }
            return result;
        } catch (Exception e) {
            log.error("Ошибка загрузки валютных пар MEXC: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.MEXC;
    }

    private boolean isSupported(String symbol) {
        for (String quote : mexcApiProperties.getQuotes()) {
            if (symbol.endsWith(quote)) return true;
        }
        return false;
    }

    private CurrencyPair parseMexcSymbol(String symbol) {
        for (String quote : mexcApiProperties.getQuotes()) {
            if (symbol.endsWith(quote)) {
                String base = symbol.substring(0, symbol.length() - quote.length());
                return new CurrencyPair(base, quote);
            }
        }
        throw new IllegalArgumentException("Unsupported type MEXC: " + symbol);
    }

    private double getDoubleSafe(JsonNode node, String field) {
        try {
            return node.path(field).asDouble(0.0);
        } catch (Exception e) {
            log.warn("MEXC: не удалось распарсить '{}' как double: {}", field, e.getMessage());
            return 0.0;
        }
    }

}