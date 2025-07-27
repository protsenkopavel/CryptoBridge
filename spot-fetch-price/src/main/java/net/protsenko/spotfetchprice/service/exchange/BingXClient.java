package net.protsenko.spotfetchprice.service.exchange;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.spotfetchprice.dto.TickerDTO;
import net.protsenko.spotfetchprice.props.BingXApiProperties;
import net.protsenko.spotfetchprice.service.ExchangeType;
import org.knowm.xchange.currency.CurrencyPair;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class BingXClient implements ExchangeClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final BingXApiProperties bingxApiProperties;

    public BingXClient(BingXApiProperties bingxApiProperties, ObjectMapper objectMapper) {
        this.bingxApiProperties = bingxApiProperties;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(bingxApiProperties.getBaseUrl())
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(configurer -> configurer
                                .defaultCodecs()
                                .maxInMemorySize(bingxApiProperties.getMaxInMemorySize()))
                        .build())
                .clientConnector(bingxApiProperties.createConnector())
                .build();
    }

    @Override
    public List<TickerDTO> getTickers(List<CurrencyPair> pairsFilter) {
        try {
            String url = bingxApiProperties.getTickersPath() + "?timestamp=" + System.currentTimeMillis();

            String response = webClient.get()
                    .uri(url)
                    .header("User-Agent", bingxApiProperties.getUserAgent())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(60))
                    .onErrorReturn("")
                    .block();

            if (response == null || response.isEmpty()) return Collections.emptyList();

            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.get("data");
            if (data == null || !data.isArray() || data.isEmpty()) {
                log.warn("BingX: пустой или некорректный ответ (data=null)");
                return Collections.emptyList();
            }

            List<TickerDTO> result = new ArrayList<>();
            for (JsonNode ticker : data) {
                String symbol = ticker.path("symbol").asText("");
                CurrencyPair pair = parseBingxSymbolSafe(symbol);
                if (pair == null) continue;

                if (pairsFilter == null || pairsFilter.isEmpty() || pairsFilter.contains(pair)) {
                    double last = getDoubleSafe(ticker, "lastPrice");
                    double bid = getDoubleSafe(ticker, "bidPrice");
                    double ask = getDoubleSafe(ticker, "askPrice");
                    double volume = getDoubleSafe(ticker, "volume");

                    result.add(new TickerDTO(
                            pair.getBase().getCurrencyCode(),
                            pair.getCounter().getCurrencyCode(),
                            last, bid, ask, volume, 0
                    ));
                }
            }
            return result;
        } catch (Exception e) {
            log.error("Ошибка загрузки тикеров BingX: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<CurrencyPair> getCurrencyPairs() {
        try {
            String url = bingxApiProperties.getSymbolsPath();
            String response = webClient.get()
                    .uri(url)
                    .header("User-Agent", bingxApiProperties.getUserAgent())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .onErrorReturn("")
                    .block();

            if (response == null || response.isEmpty()) return Collections.emptyList();

            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.get("data");
            JsonNode symbols = data != null ? data.get("symbols") : null;
            if (symbols == null || !symbols.isArray() || symbols.isEmpty()) {
                log.warn("BingX: пустой или некорректный symbols (symbols=null)");
                return Collections.emptyList();
            }

            List<CurrencyPair> result = new ArrayList<>();
            for (JsonNode symbolNode : symbols) {
                String symbol = symbolNode.path("symbol").asText("");
                CurrencyPair pair = parseBingxSymbolSafe(symbol);
                if (pair != null) result.add(pair);
            }
            return result;
        } catch (Exception e) {
            log.error("Ошибка загрузки валютных пар BingX: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.BINGX;
    }

    private CurrencyPair parseBingxSymbolSafe(String symbol) {
        if (symbol == null || symbol.isEmpty()) return null;
        String[] parts = symbol.split("-");
        if (parts.length == 2) {
            return new CurrencyPair(parts[0], parts[1]);
        }
        return null;
    }

    private double getDoubleSafe(JsonNode node, String field) {
        try {
            return node.path(field).asDouble(0.0);
        } catch (Exception e) {
            log.warn("BingX: не удалось распарсить поле '{}' как double: {}", field, e.getMessage());
            return 0.0;
        }
    }
}
