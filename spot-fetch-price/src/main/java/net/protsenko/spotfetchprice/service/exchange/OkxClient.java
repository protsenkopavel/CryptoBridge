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
public class OkxClient implements ExchangeClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Override
    public List<TickerDTO> getTickers(List<CurrencyPair> pairsFilter) throws IOException {
        List<TickerDTO> result = new ArrayList<>();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://www.okx.com/api/v5/market/tickers?instType=SPOT"))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode tickers = root.get("data");

            if (tickers != null && tickers.isArray()) {
                for (JsonNode ticker : tickers) {
                    String instId = ticker.get("instId").asText();
                    CurrencyPair pair = parseOkxPair(instId);

                    if (pairsFilter == null || pairsFilter.isEmpty() || pairsFilter.contains(pair)) {
                        double last = ticker.has("last") ? ticker.get("last").asDouble() : 0;
                        double bid = ticker.has("bidPx") ? ticker.get("bidPx").asDouble() : 0;
                        double ask = ticker.has("askPx") ? ticker.get("askPx").asDouble() : 0;
                        double volume = ticker.has("vol24h") ? ticker.get("vol24h").asDouble() : 0;

                        result.add(new TickerDTO(
                                pair.getBase().getCurrencyCode(),
                                pair.getCounter().getCurrencyCode(),
                                last, bid, ask, volume, 0
                        ));
                    }
                }
            }
        } catch (Exception e) {
            throw new IOException("Failed fetching OKX", e);
        }
        return result;
    }

    @Override
    public List<CurrencyPair> getCurrencyPairs() throws IOException {
        List<CurrencyPair> result = new ArrayList<>();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://www.okx.com/api/v5/public/instruments?instType=SPOT"))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode symbols = root.get("data");

            if (symbols != null && symbols.isArray()) {
                for (JsonNode instrument : symbols) {
                    String instId = instrument.get("instId").asText();
                    result.add(parseOkxPair(instId));
                }
            }
        } catch (Exception e) {
            throw new IOException("Failed fetching OKX pairs", e);
        }
        return result;
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.OKX;
    }

    private CurrencyPair parseOkxPair(String instId) {
        String[] parts = instId.split("-");
        if (parts.length == 2) {
            return new CurrencyPair(parts[0], parts[1]);
        }
        throw new IllegalArgumentException("Wrong instId OKX: " + instId);
    }
}
