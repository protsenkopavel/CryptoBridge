package net.protsenko.spotfetchprice.service.exchange;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.spotfetchprice.dto.TickerDTO;
import net.protsenko.spotfetchprice.service.ExchangeType;
import org.knowm.xchange.currency.CurrencyPair;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class BingxClient implements ExchangeClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Override
    public List<TickerDTO> getTickers(List<CurrencyPair> pairsFilter) throws IOException {
        List<TickerDTO> result = new ArrayList<>();
        try {
            long timestamp = System.currentTimeMillis();
            String url = "https://open-api.bingx.com/openApi/spot/v1/ticker/24hr?timestamp=" + timestamp;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());

            JsonNode data = root.get("data");
            if (data != null && data.isArray()) {
                for (JsonNode ticker : data) {
                    String symbol = ticker.get("symbol").asText();
                    CurrencyPair pair = parseBingxSymbol(symbol);

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
            }
        } catch (Exception e) {
            throw new IOException("Ошибка загрузки тикеров BingX", e);
        }
        return result;
    }

    @Override
    public List<CurrencyPair> getCurrencyPairs() throws IOException {
        List<CurrencyPair> result = new ArrayList<>();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://open-api.bingx.com/openApi/spot/v1/common/symbols"))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode data = root.get("data");
            if (data != null && data.isArray()) {
                for (JsonNode symbolNode : data) {
                    String symbol = symbolNode.get("symbol").asText();
                    result.add(parseBingxSymbol(symbol));
                }
            }
        } catch (Exception e) {
            throw new IOException("Ошибка загрузки валютных пар BingX", e);
        }
        return result;
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.BINGX;
    }

    private CurrencyPair parseBingxSymbol(String symbol) {
        String[] parts = symbol.split("-");
        if (parts.length == 2) {
            return new CurrencyPair(parts[0], parts[1]);
        }
        throw new IllegalArgumentException("Некорректный symbol BingX: " + symbol);
    }

    private double getDoubleSafe(JsonNode node, String field) {
        JsonNode valueNode = node.get(field);
        if (valueNode != null && !valueNode.isNull()) {
            try {
                return valueNode.asDouble();
            } catch (Exception ignore) {
            }
        }
        return 0.0;
    }
}
