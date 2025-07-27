package net.protsenko.spotfetchprice.service.exchange;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.spotfetchprice.dto.TickerDTO;
import net.protsenko.spotfetchprice.props.OKXApiProperties;
import net.protsenko.spotfetchprice.service.ExchangeType;
import org.knowm.xchange.currency.CurrencyPair;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
public class OKXClient implements ExchangeClient {

    private final OKXApiProperties okxApiProperties;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public OKXClient(OKXApiProperties okxApiProperties, ObjectMapper objectMapper) {
        this.okxApiProperties = okxApiProperties;
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(okxApiProperties.getBaseUrl())
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(configurer -> configurer
                                .defaultCodecs()
                                .maxInMemorySize(okxApiProperties.getMaxInMemorySize()))
                        .build())
                .build();
    }

    @Override
    public List<TickerDTO> getTickers(List<CurrencyPair> pairsFilter) {
        try {
            String response = webClient.get()
                    .uri(okxApiProperties.getTickersPath())
                    .header("User-Agent", okxApiProperties.getUserAgent())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .onErrorReturn("")
                    .block();

            if (response == null || response.isEmpty()) return Collections.emptyList();

            JsonNode root = objectMapper.readTree(response);
            JsonNode tickers = root.get("data");

            List<TickerDTO> result = new ArrayList<>();
            if (tickers != null && tickers.isArray()) {
                for (JsonNode ticker : tickers) {
                    String instId = ticker.path("instId").asText();
                    CurrencyPair pair = parseOkxPairSafe(instId);
                    if (pair == null) continue;

                    if (pairsFilter == null || pairsFilter.isEmpty() || pairsFilter.contains(pair)) {
                        double last = getDoubleSafe(ticker, "last");
                        double bid = getDoubleSafe(ticker, "bidPx");
                        double ask = getDoubleSafe(ticker, "askPx");
                        double volume = getDoubleSafe(ticker, "vol24h");

                        result.add(new TickerDTO(
                                pair.getBase().getCurrencyCode(),
                                pair.getCounter().getCurrencyCode(),
                                last, bid, ask, volume, 0
                        ));
                    }
                }
            }
            return result;
        } catch (Exception e) {
            log.error("Ошибка загрузки тикеров OKX: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<CurrencyPair> getCurrencyPairs() {
        try {
            String response = webClient.get()
                    .uri(okxApiProperties.getInstrumentsPath())
                    .header("User-Agent", okxApiProperties.getUserAgent())
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .onErrorReturn("")
                    .block();

            if (response == null || response.isEmpty()) return Collections.emptyList();

            JsonNode root = objectMapper.readTree(response);
            JsonNode symbols = root.get("data");
            List<CurrencyPair> result = new ArrayList<>();
            if (symbols != null && symbols.isArray()) {
                for (JsonNode instrument : symbols) {
                    String instId = instrument.path("instId").asText();
                    CurrencyPair pair = parseOkxPairSafe(instId);
                    if (pair != null) result.add(pair);
                }
            }
            return result;
        } catch (Exception e) {
            log.error("Ошибка загрузки валютных пар OKX: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.OKX;
    }

    private CurrencyPair parseOkxPairSafe(String instId) {
        if (instId == null || instId.isEmpty()) return null;
        String[] parts = instId.split("-");
        if (parts.length == 2) {
            return new CurrencyPair(parts[0], parts[1]);
        }
        log.warn("OKX: некорректный instId: {}", instId);
        return null;
    }

    private double getDoubleSafe(JsonNode node, String field) {
        try {
            return node.path(field).asDouble(0.0);
        } catch (Exception e) {
            log.warn("OKX: не удалось распарсить '{}' как double: {}", field, e.getMessage());
            return 0.0;
        }
    }
}