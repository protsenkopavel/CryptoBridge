package net.protsenko.spotfetchprice.props;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "exchanger.properties.okx")
public class OkxApiProperties {

    private String key;

    private String secret;

    private String passphrase;

}
