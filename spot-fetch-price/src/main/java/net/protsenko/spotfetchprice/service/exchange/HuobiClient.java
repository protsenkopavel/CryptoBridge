package net.protsenko.spotfetchprice.service.exchange;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.spotfetchprice.dto.TickerDTO;
import net.protsenko.spotfetchprice.props.HuobiApiProperties;
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
public class HuobiClient implements ExchangeClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final HuobiApiProperties apiProperties;

    public HuobiClient(HuobiApiProperties apiProperties, ObjectMapper objectMapper) {
        this.apiProperties = apiProperties;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(apiProperties.getBaseUrl())
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(configurer -> configurer
                                .defaultCodecs()
                                .maxInMemorySize(apiProperties.getMaxInMemorySize()))
                        .build())
                .clientConnector(apiProperties.createConnector())
                .build();
    }

    @Override
    public List<TickerDTO> getTickers(List<CurrencyPair> pairsFilter) {
        try {
            String response = webClient.get()
                    .uri(apiProperties.getTickersPath())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .onErrorReturn("")
                    .block();

            if (response == null || response.isEmpty()) return Collections.emptyList();

            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.get("data");
            if (data == null || !data.isArray() || data.isEmpty()) {
                log.warn("Huobi: пустой/некорректный ответ на тикеры");
                return Collections.emptyList();
            }

            List<CurrencyPair> allPairs = getCurrencyPairs();
            var symbolToPair = new java.util.HashMap<String, CurrencyPair>(allPairs.size());
            for (CurrencyPair p : allPairs) {
                symbolToPair.put((p.getBase().getCurrencyCode() + p.getCounter().getCurrencyCode()).toLowerCase(), p);
            }

            List<TickerDTO> result = new ArrayList<>();
            for (JsonNode ticker : data) {
                String symbol = ticker.path("symbol").asText("");
                if (symbol.isEmpty()) continue;

                CurrencyPair pair = symbolToPair.get(symbol.toLowerCase());
                if (pair == null) continue;

                if (pairsFilter == null || pairsFilter.isEmpty() || pairsFilter.contains(pair)) {
                    double last = ticker.path("close").asDouble(0.0);
                    double bid = ticker.path("bid").asDouble(0.0);
                    double ask = ticker.path("ask").asDouble(0.0);
                    double volume = ticker.path("vol").asDouble(0.0);

                    result.add(new TickerDTO(
                            pair.getBase().getCurrencyCode(),
                            pair.getCounter().getCurrencyCode(),
                            last, bid, ask, volume, 0
                    ));
                }
            }
            return result;
        } catch (Exception e) {
            log.error("Ошибка загрузки тикеров Huobi: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<CurrencyPair> getCurrencyPairs() {
        try {
            String response = webClient.get()
                    .uri(apiProperties.getSymbolsPath())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .onErrorReturn("")
                    .block();

            if (response == null || response.isEmpty()) return Collections.emptyList();

            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.get("data");
            if (data == null || !data.isArray() || data.isEmpty()) {
                log.warn("Huobi: пустой/некорректный symbols");
                return Collections.emptyList();
            }

            List<CurrencyPair> result = new ArrayList<>();
            for (JsonNode node : data) {
                String base = node.path("base-currency").asText("");
                String quote = node.path("quote-currency").asText("");
                if (base.isEmpty() || quote.isEmpty()) continue;
                result.add(new CurrencyPair(base.toUpperCase(), quote.toUpperCase()));
            }
            return result;
        } catch (Exception e) {
            log.error("Ошибка загрузки валютных пар Huobi: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.HUOBI;
    }

}