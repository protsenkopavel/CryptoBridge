package net.protsenko.cryptobridge.telegramnotifier.repo;

import net.protsenko.cryptobridge.telegramnotifier.entity.TelegramSubscriber;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelegramSubscriberRepository extends JpaRepository<TelegramSubscriber, Long> {
}
