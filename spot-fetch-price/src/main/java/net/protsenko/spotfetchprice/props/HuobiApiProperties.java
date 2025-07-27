package net.protsenko.spotfetchprice.props;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Data
@Configuration
public class HuobiApiProperties {

    private String baseUrl = "https://api.huobi.pro";
    private String tickersPath = "/market/tickers";
    private String symbolsPath = "/v1/common/symbols";
    private String userAgent = "Mozilla/5.0";
    private int maxInMemorySize = 20 * 1024 * 1024;
    private int responseTimeoutSeconds = 60;

    public ReactorClientHttpConnector createConnector() {
        return new ReactorClientHttpConnector(
                HttpClient.create().responseTimeout(Duration.ofSeconds(responseTimeoutSeconds))
        );
    }

}
