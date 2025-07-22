package net.protsenko.cryptobridge.telegramnotifier.props;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "telegram.bot")
public class TelegramBotProperties {

    private String username;

    private String token;

}
