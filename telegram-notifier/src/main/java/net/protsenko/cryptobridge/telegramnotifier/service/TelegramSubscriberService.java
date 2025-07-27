package net.protsenko.cryptobridge.telegramnotifier.service;

import lombok.RequiredArgsConstructor;
import net.protsenko.cryptobridge.telegramnotifier.entity.TelegramSubscriber;
import net.protsenko.cryptobridge.telegramnotifier.repo.TelegramSubscriberRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TelegramSubscriberService {

    private final TelegramSubscriberRepository repository;

    public void subscribe(Long chatId) {
        if (!repository.existsById(chatId)) {
            repository.save(new TelegramSubscriber(chatId));
        }
    }

    public void unsubscribe(Long chatId) {
        repository.deleteById(chatId);
    }

    public List<Long> getAllChatIds() {
        return repository.findAll().stream()
                .map(TelegramSubscriber::getChatId)
                .toList();
    }

}
