package net.protsenko.spotfetchprice.props;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "exchanger.properties.okx")
public class OKXApiProperties {

    private final String baseUrl = "https://www.okx.com";
    private final String stopConfigPath = "/api/v5/asset/currencies";
    private final String tickersPath = "/api/v5/market/tickers?instType=SPOT";
    private final String instrumentsPath = "/api/v5/public/instruments?instType=SPOT";
    private final String userAgent = "Mozilla/5.0";
    private String key;
    private String secret;
    private String passphrase;
    private int maxInMemorySize = 20 * 1024 * 1024;
    private int responseTimeoutSeconds = 60;

}
