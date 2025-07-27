package net.protsenko.cryptobridge.telegramnotifier.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TelegramSubscriber {
    @Id
    private Long chatId;
}
