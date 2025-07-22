package net.protsenko.cryptobridge.cryptobridge.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ArbitrageScannerScheduler {

    private final ArbitrageScannerService scannerService;

    @Scheduled(fixedRateString = "${arbitrage.scanner.refresh-ms}")
    public void scheduledScan() {
        try {
            scannerService.scanBestSpreads();
        } catch (Exception e) {
            log.error("Error during scheduled arbitrage scan", e);
        }
    }
}
