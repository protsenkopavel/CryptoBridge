package net.protsenko.cryptobridge.cryptobridge.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.knowm.xchange.currency.CurrencyPair;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArbitrageScannerService {

    private static final double MIN_VOLUME = 0.0;
    private final ExchangeService exchangeService;
    private final PriceSpreadService priceSpreadService;
    private List<CurrencyPair> pairsToScan = null;
    private List<ExchangeType> exchangesToScan = null;

    @Scheduled(fixedRateString = "${arbitrage.scanner.refresh-ms}")
    public void scanBestSpreads() {
        log.info("Starting arbitrage scan");

        List<CurrencyPair> pairs = pairsToScan != null ? pairsToScan : exchangeService.getAvailableCurrencyPairs(exchangesToScan);
        List<ExchangeType> exchanges = exchangesToScan != null ? exchangesToScan : exchangeService.getAvailableExchanges();

        for (CurrencyPair pair : pairs) {
            priceSpreadService.findMaxArbitrageSpreadForPair(pair, exchanges, MIN_VOLUME)
                    .ifPresent(spread -> {
                        log.info("Best arbitrage for pair {}: Buy on {} at {}, sell on {} at {}, spread = ({}%)",
                                spread.instrument(),
                                spread.buyExchange(),
                                spread.buyPrice(),
                                spread.sellExchange(),
                                spread.sellPrice(),
                                String.format("%.2f", spread.spreadPercent()));
                    });
        }

        log.info("Arbitrage scan completed");
    }

}