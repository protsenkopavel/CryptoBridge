package net.protsenko.spotfetchprice.props;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Data
@Configuration
@ConfigurationProperties(prefix = "exchanger.properties.bingx")
public class BingXApiProperties {

    private String key;
    private String secret;
    private String baseUrl = "https://open-api.bingx.com";
    private String spotConfigPath = "/openApi/wallets/v1/capital/config/getall";
    private String tickersPath = "/openApi/spot/v1/ticker/24hr";
    private String symbolsPath = "/openApi/spot/v1/common/symbols";
    private String userAgent = "Mozilla/5.0";
    private int maxInMemorySize = 20 * 1024 * 1024;
    private int responseTimeoutSeconds = 60;
    private String redis_key = "tradingInfo:bingx:all";

    public ReactorClientHttpConnector createConnector() {
        return new ReactorClientHttpConnector(
                HttpClient.create().responseTimeout(Duration.ofSeconds(responseTimeoutSeconds))
        );
    }

}
