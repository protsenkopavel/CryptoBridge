package net.protsenko.cryptobridge.telegramnotifier.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.cryptobridge.telegramnotifier.dto.PriceSpreadResultDTO;
import net.protsenko.cryptobridge.telegramnotifier.props.TelegramBotProperties;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramBotService extends TelegramLongPollingBot {

    private final TelegramBotProperties properties;

    @PostConstruct
    public void init() {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(this);
            log.info("Telegram bot registered");
        } catch (Exception e) {
            log.error("Error registering Telegram bot", e);
        }
    }

    @Override
    public void onUpdateReceived(Update update) {
    }

    @Override
    public String getBotUsername() {
        return properties.getUsername();
    }

    @Override
    public String getBotToken() {
        return properties.getToken();
    }

    public void notifyUsers(PriceSpreadResultDTO spread) {
        // TODO Реализовать отправку в Telegram
        log.info("Обработано событие {}", spread.toString());
    }

}
