package net.protsenko.spotfetchprice.props;

import lombok.Data;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Data
@Configuration
public class CoinEXApiProperties {

    private final String baseUrl = "https://api.coinex.com/v2";
    private final String spotConfigPath = "/assets/all-deposit-withdraw-config";
    private final String redisKeyAll = "tradingInfo:coinex:all";
    private int maxInMemorySize = 20 * 1024 * 1024;
    private int responseTimeoutSeconds = 60;

    public ReactorClientHttpConnector createConnector() {
        return new ReactorClientHttpConnector(
                HttpClient.create().responseTimeout(Duration.ofSeconds(responseTimeoutSeconds))
        );
    }

}
