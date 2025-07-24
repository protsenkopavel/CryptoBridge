package net.protsenko.spotfetchprice.props;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "exchanger.properties.bybit")
public class BybitApiProperties {

    public final static String API_URL = "https://api.bybit.com/v5/asset/coin/query-info";

    public final static String RECV_WINDOW = "5000";

    private String key;

    private String secret;

}
