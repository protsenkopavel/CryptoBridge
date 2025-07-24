package net.protsenko.spotfetchprice.service.exchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.spotfetchprice.service.ExchangeType;
import org.knowm.xchange.Exchange;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;

@Slf4j
@Component
public class ExchangeClientFactory {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ExchangeClientFactory() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public ExchangeClient createClient(ExchangeType exchangeType) throws IOException {
        if (exchangeType == ExchangeType.MEXC) {
            return new MexcClient(httpClient, objectMapper);
        }

        if (exchangeType == ExchangeType.OKX) {
            return new OkxClient(httpClient, objectMapper);
        }

        Exchange exchange = exchangeType.createExchange();
        try {
            exchange.remoteInit();
        } catch (NullPointerException npe) {
            log.error("NullPointerException during remoteInit for {}: {}", exchangeType, npe.getMessage());
            throw new IOException("Failed to initialize exchange " + exchangeType, npe);
        } catch (IOException e) {
            log.error("Error initializing exchange {}: {}", exchangeType, e.getMessage());
            throw e;
        }

        return switch (exchangeType) {
            case KUCOIN -> new KucoinClient(exchange);
            case BITFINEX -> new BitfinexClient(exchange);
            default -> new BaseXChangeClient(exchange) {
                @Override
                public ExchangeType getExchangeType() {
                    return exchangeType;
                }
            };
        };
    }
}