package net.protsenko.spotfetchprice.service.exchange;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.spotfetchprice.props.BingXApiProperties;
import net.protsenko.spotfetchprice.props.MEXCApiProperties;
import net.protsenko.spotfetchprice.props.OKXApiProperties;
import net.protsenko.spotfetchprice.service.ExchangeType;
import org.knowm.xchange.Exchange;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;

@Slf4j
@Component
public class ExchangeClientFactory {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final BingXApiProperties bingXApiProperties;
    private final MEXCApiProperties mexcApiProperties;
    private final OKXApiProperties okxApiProperties;

    public ExchangeClientFactory(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            BingXApiProperties bingXApiProperties,
            MEXCApiProperties mexcApiProperties,
            OKXApiProperties okxApiProperties
    ) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
        this.bingXApiProperties = bingXApiProperties;
        this.mexcApiProperties = mexcApiProperties;
        this.okxApiProperties = okxApiProperties;
    }

    public ExchangeClient createClient(ExchangeType exchangeType) throws IOException {
        return switch (exchangeType) {
            case MEXC -> new MEXCClient(mexcApiProperties, objectMapper);
            case OKX -> new OKXClient(okxApiProperties, objectMapper);
            case BINGX -> new BingxClient(bingXApiProperties, objectMapper);
            case KUCOIN -> {
                Exchange exchange = exchangeType.createExchange();
                try {
                    exchange.remoteInit();
                    yield new KucoinClient(exchange);
                } catch (Exception e) {
                    log.error("Ошибка инициализации Kucoin: {}", e.getMessage());
                    throw new IOException("Failed to initialize Kucoin", e);
                }
            }
            case BITFINEX -> {
                Exchange exchange = exchangeType.createExchange();
                try {
                    exchange.remoteInit();
                    yield new BitfinexClient(exchange);
                } catch (Exception e) {
                    log.error("Ошибка инициализации Bitfinex: {}", e.getMessage());
                    throw new IOException("Failed to initialize Bitfinex", e);
                }
            }
            default -> {
                Exchange exchange = exchangeType.createExchange();
                try {
                    exchange.remoteInit();
                    yield new BaseXChangeClient(exchange) {
                        @Override
                        public ExchangeType getExchangeType() {
                            return exchangeType;
                        }
                    };
                } catch (Exception e) {
                    log.error("Ошибка инициализации {}: {}", exchangeType, e.getMessage());
                    throw new IOException("Failed to initialize exchange " + exchangeType, e);
                }
            }
        };
    }
}