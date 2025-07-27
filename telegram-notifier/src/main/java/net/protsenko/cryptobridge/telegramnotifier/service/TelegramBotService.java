package net.protsenko.cryptobridge.telegramnotifier.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.protsenko.cryptobridge.telegramnotifier.dto.PriceSpreadResultDTO;
import net.protsenko.cryptobridge.telegramnotifier.dto.TradingInfoDTO;
import net.protsenko.cryptobridge.telegramnotifier.dto.TradingNetworkInfoDTO;
import net.protsenko.cryptobridge.telegramnotifier.props.TelegramBotProperties;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramBotService extends TelegramLongPollingBot {

    private final TelegramBotProperties properties;
    private final TelegramSubscriberService subscriberService;

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
        if (update.hasMessage()) {
            Message message = update.getMessage();
            Long chatId = message.getChatId();
            String text = message.getText().trim().toLowerCase();

            switch (text) {
                case "/start" -> {
                    subscriberService.subscribe(chatId);
                    sendMessage(chatId, "✅ Вы подписаны на уведомления об арбитраже.\nЧтобы отписаться, используйте /stop");
                    log.info("Подписан новый пользователь: {}", chatId);
                }
                case "/stop" -> {
                    subscriberService.unsubscribe(chatId);
                    sendMessage(chatId, "❌ Вы отписались от уведомлений.");
                    log.info("Пользователь отписался: {}", chatId);
                }
                default -> sendMessage(chatId, """
                                                🤖 Доступные команды:
                        /start — подписаться на уведомления
                        /stop — отписаться от уведомлений
                        """);
            }
        }
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
        String message = formatSpreadMessage(spread);
        for (Long chatId : subscriberService.getAllChatIds()) {
            sendMessage(chatId, message);
        }
        log.info("Уведомления отправлены {} подписчикам", subscriberService.getAllChatIds().size());
    }

    public void sendMessage(Long chatId, String text) {
        SendMessage message = new SendMessage(chatId.toString(), text);
        message.setParseMode("HTML");
        try {
            execute(message);
        } catch (Exception e) {
            log.error("Ошибка при отправке сообщения в чат {}: {}", chatId, e.getMessage());
        }
    }

    public String formatSpreadMessage(PriceSpreadResultDTO spread) {
        String baseAsset = spread.baseCurrency();

        StringBuilder sb = new StringBuilder();
        sb.append("📈 <b>").append(spread.instrument()).append("</b>")
                .append(" | Спред: ").append(formatPriceWithDollar(spread.spread()))
                .append(" (").append(formatPercent(spread.spreadPercentage())).append(")\n\n");

        sb.append("<b>Покупка:</b>  ")
                .append(spread.buyExchange()).append("   ")
                .append(formatPriceWithDollar(spread.buyPrice())).append("\n")
                .append("Объем 24ч: ").append(formatVolumeShort(spread.buyVolume())).append("\n");
        appendBuyWithdrawInfo(sb, spread.buyTradingInfo(), baseAsset);

        sb.append("\n<b>Продажа:</b>  ")
                .append(spread.sellExchange()).append("   ")
                .append(formatPriceWithDollar(spread.sellPrice())).append("\n")
                .append("Объем 24ч: ").append(formatVolumeShort(spread.sellVolume())).append("\n");
        appendSellDepositInfo(sb, spread.sellTradingInfo());

        return sb.toString();
    }

    private void appendBuyWithdrawInfo(StringBuilder sb, TradingInfoDTO info, String asset) {
        if (info != null && info.networks() != null && !info.networks().isEmpty()) {
            for (TradingNetworkInfoDTO n : info.networks()) {
                sb.append("Сеть: ").append(nonEmpty(n.network()))
                        .append("  Комиссия на вывод: ").append(formatFeeSmart(n.withdrawFee(), asset))
                        .append("  Вывод: ").append(n.withdrawEnabled() ? "✅" : "❌").append("\n");
            }
        }
    }

    private void appendSellDepositInfo(StringBuilder sb, TradingInfoDTO info) {
        if (info != null && info.networks() != null && !info.networks().isEmpty()) {
            for (TradingNetworkInfoDTO n : info.networks()) {
                sb.append("Сеть: ").append(nonEmpty(n.network()))
                        .append("  Ввод: ").append(n.depositEnabled() ? "✅" : "❌").append("\n");
            }
        }
    }

    private String formatPriceWithDollar(double price) {
        return "$" + String.format("%.6f", price);
    }

    private String formatVolumeShort(Double volume) {
        if (volume == null) return "—";
        double v = volume;
        if (v >= 1_000_000_000) return String.format("%.1fB", v / 1_000_000_000.0);
        if (v >= 1_000_000) return String.format("%.1fM", v / 1_000_000.0);
        if (v >= 1_000) return String.format("%.1fK", v / 1_000.0);
        return String.format("%.0f", v);
    }

    private String formatFeeSmart(double fee, String asset) {
        if (fee < 0) return "N/A";
        return (fee % 1 == 0 ? String.valueOf((int) fee) : String.valueOf(fee)) + " " + asset;
    }

    private String formatPercent(double percent) {
        return String.format("%.2f%%", percent);
    }

    private String nonEmpty(String s) {
        return (s == null || s.isEmpty()) ? "—" : s;
    }

}
