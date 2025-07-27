package net.protsenko.spotfetchprice.props;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Data
@Configuration
@ConfigurationProperties(prefix = "exchanger.properties.mexc")
public class MEXCApiProperties {

    private final int maxInMemorySize = 20 * 1024 * 1024;
    private final int responseTimeoutSeconds = 60;
    private final String redisKeyAll = "tradingInfo:mexc:all";
    private final String[] quotes = {"USDT", "USDC"};
    private String key;
    private String secret;
    private String baseUrl = "https://api.mexc.com";
    private String spotConfigPath = "/api/v3/capital/config/getall";
    private String tickersPath = "/api/v3/ticker/24hr";
    private String exchangeInfoPath = "/api/v3/exchangeInfo";
    private String userAgent = "Mozilla/5.0";

    public ReactorClientHttpConnector createConnector() {
        return new ReactorClientHttpConnector(
                HttpClient.create().responseTimeout(Duration.ofSeconds(responseTimeoutSeconds))
        );
    }

}