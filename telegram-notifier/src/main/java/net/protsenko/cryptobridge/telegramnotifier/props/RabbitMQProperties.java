package net.protsenko.cryptobridge.telegramnotifier.props;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "arbitrage.rabbit")
public class RabbitMQProperties {

    private String queueName;

    private String exchangeName;

    private String routingKey;

}
