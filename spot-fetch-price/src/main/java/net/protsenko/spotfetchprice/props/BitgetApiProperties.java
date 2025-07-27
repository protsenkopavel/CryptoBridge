package net.protsenko.spotfetchprice.props;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Data
@Configuration
@ConfigurationProperties(prefix = "exchanger.properties.bitget")
public class BitgetApiProperties {

    private String key;
    private String secret;
    private String passphrase;
    private final String baseUrl = "https://api.bitget.com";
    private final String spotConfigUrl = "/api/spot/v1/public/currencies";
    private final String redisKeyAll = "tradingInfo:bitget:all";
    private int maxInMemorySize = 20 * 1024 * 1024;
    private int responseTimeoutSeconds = 60;

    public ReactorClientHttpConnector createConnector() {
        return new ReactorClientHttpConnector(
                HttpClient.create().responseTimeout(Duration.ofSeconds(responseTimeoutSeconds))
        );
    }

}
