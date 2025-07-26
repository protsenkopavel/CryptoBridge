package net.protsenko.cryptobridge.telegramnotifier.service;

import lombok.RequiredArgsConstructor;
import net.protsenko.cryptobridge.telegramnotifier.dto.ArbitrageOpportunityFoundEvent;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ArbitrageNotificationService {

    private final TelegramBotService telegramBotService;

    @RabbitListener(queues = "arbitrage.events")
    public void onEvent(ArbitrageOpportunityFoundEvent event) {
        telegramBotService.notifyUsers(event.spread());
    }

}
