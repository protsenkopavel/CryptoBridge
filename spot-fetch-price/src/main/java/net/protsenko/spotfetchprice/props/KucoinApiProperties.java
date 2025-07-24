package net.protsenko.spotfetchprice.props;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "exchanger.properties.kucoin")
public class KucoinApiProperties {

    public static String API_URL = "https://api.kucoin.com/api/v3/currencies/";

    private String key;

    private String secret;

    private String passphrase;

}
