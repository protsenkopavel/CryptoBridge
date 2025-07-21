package net.protsenko.cryptobridge.cryptobridge.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExchangeCacheScheduler {

    private final ExchangeService exchangeService;

    @Scheduled(fixedRateString = "${exchange.cache.refresh-ms}")
    public void refreshCache() {
        log.info("Scheduled cache refresh started");
        try {
            exchangeService.refreshCache();
            log.info("Scheduled cache refresh completed successfully");
        } catch (Exception e) {
            log.error("Error during scheduled cache refresh", e);
        }
    }

}
