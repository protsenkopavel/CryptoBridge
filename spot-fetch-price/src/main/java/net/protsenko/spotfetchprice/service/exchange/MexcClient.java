package net.protsenko.spotfetchprice.service.exchange;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class MexcClient implements ExchangeClient {

    private static final String[] QUOTES = {"USDT", "USDC"};
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public MexcClient(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<TickerDTO> getTickers(List<CurrencyPair> pairsFilter) throws IOException {
        List<TickerDTO> result = new ArrayList<>();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mexc.com/api/v3/ticker/bookTicker"))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            JsonNode tickers = objectMapper.readTree(response.body());

            for (JsonNode tickerNode : tickers) {
                String symbol = tickerNode.get("symbol").asText();
                if (!isSupported(symbol)) {
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
            throw new IOException("Loading fail MEXC", e);
        }
        return result;
    }

    @Override
    public List<CurrencyPair> getCurrencyPairs() throws IOException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mexc.com/api/v3/exchangeInfo"))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode symbols = root.get("symbols");

            if (symbols == null || !symbols.isArray()) {
                throw new RuntimeException("Invalid response from MEXC");
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
            throw new IOException("Failed load MEXC", e);
        }
    }

    @Override
    public ExchangeType getExchangeType() {
        return ExchangeType.MEXC;
    }

    private boolean isSupported(String symbol) {
        for (String quote : QUOTES) {
            if (symbol.endsWith(quote)) {
                return true;
            }
        }
        return false;
    }

    private CurrencyPair parseMexcSymbol(String symbol) {
        for (String quote : QUOTES) {
            if (symbol.endsWith(quote)) {
                String base = symbol.substring(0, symbol.length() - quote.length());
                return new CurrencyPair(base, quote);
            }
        }
        throw new IllegalArgumentException("Unsupported type MEXC: " + symbol);
    }
}