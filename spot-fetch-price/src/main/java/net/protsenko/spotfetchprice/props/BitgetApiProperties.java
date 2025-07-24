package net.protsenko.spotfetchprice.props;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "exchanger.properties.bitget")
public class BitgetApiProperties {

    private String key;

    private String secret;

    private String passphrase;

}
